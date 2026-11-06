#!/usr/bin/env bash
#
# 在【新服务器】上执行：把迁移包还原成一套能跑的服务。
# 前提：已按 docs/solo/DEPLOY.md 装好 JDK17 / MariaDB / nginx（装依赖那步不能省）。
#
#   bash migrate-import.sh /root/one-finger-migrate-2026-12-31.tar.gz
#
set -euo pipefail

PKG=${1:?用法: bash migrate-import.sh /root/one-finger-migrate-YYYY-MM-DD.tar.gz}
[ -f "$PKG" ] || { echo "找不到迁移包：$PKG"; exit 1; }

APP_DIR=/opt/one-finger
WEB_DIR=/var/www/one-finger
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
tar xzf "$PKG" -C "$TMP"

echo "== 1/7 建库与账号 =="
DB_PASS=$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$TMP/app.env")
if [ -z "$DB_PASS" ]; then echo "app.env 里没有数据库密码，包不对"; exit 1; fi
systemctl stop one-finger 2>/dev/null || true
mariadb <<SQL
CREATE DATABASE IF NOT EXISTS nail_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'nail'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
ALTER USER 'nail'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON nail_demo.* TO 'nail'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

echo "== 2/7 导入数据 =="
mariadb < "$TMP/nail_demo.sql"
mariadb -N -e "SELECT CONCAT('   账号 ', COUNT(*), ' 个') FROM nail_demo.sys_user;"
mariadb -N -e "SELECT CONCAT('   客户 ', COUNT(*), ' 位') FROM nail_demo.solo_client;"

echo "== 3/7 目录与文件 =="
id onefinger >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin onefinger
mkdir -p "$APP_DIR/backend" "$APP_DIR/uploads" "$APP_DIR/backups" "$WEB_DIR"
tar xzf "$TMP/uploads.tar.gz" -C "$APP_DIR"
cp "$TMP/app.jar" "$APP_DIR/backend/app.jar"
cp "$TMP/app.env" "$APP_DIR/backend/app.env"
cp "$TMP/my.cnf" "$APP_DIR/.my.cnf"
chmod 600 "$APP_DIR/backend/app.env" "$APP_DIR/.my.cnf"
chmod 750 "$APP_DIR"
chmod 700 "$APP_DIR/backups"

echo "== 4/7 前端 =="
if [ -d /tmp/dist ] || [ -f /root/dist.tar.gz ]; then
  echo "   发现前端产物，解包"
  [ -f /root/dist.tar.gz ] && tar xzf /root/dist.tar.gz -C "$WEB_DIR"
fi
if [ -z "$(ls -A "$WEB_DIR" 2>/dev/null)" ]; then
  echo "   ⚠ 前端目录是空的：本地跑 ./scripts/deploy-solo.sh root@本机地址 会把 dist 传上来"
fi

echo "== 5/7 systemd 与 nginx =="
cp "$TMP/one-finger.service" /etc/systemd/system/one-finger.service
cp "$TMP/nginx-site.conf" /etc/nginx/sites-available/one-finger
ln -sf /etc/nginx/sites-available/one-finger /etc/nginx/sites-enabled/one-finger
rm -f /etc/nginx/sites-enabled/default
chown -R onefinger:onefinger "$APP_DIR" "$WEB_DIR"
systemctl daemon-reload

echo "== 6/7 定时任务与加固配置 =="
install_conf() {
  [ -f "$TMP/$1" ] || return 0
  mkdir -p "$(dirname "$2")"
  cp "$TMP/$1" "$2"
}
install_conf cron-one-finger          /etc/cron.d/one-finger
install_conf cron-security-update     /etc/cron.weekly/security-update
install_conf sysctl-hardening.conf    /etc/sysctl.d/99-hardening.conf
install_conf sshd-hardening.conf      /etc/ssh/sshd_config.d/99-hardening.conf
install_conf fail2ban-hardening.local /etc/fail2ban/jail.d/99-hardening.local
install_conf mariadb-hardening.cnf    /etc/mysql/mariadb.conf.d/99-hardening.cnf
install_conf nginx.conf               /etc/nginx/nginx.conf
chmod 700 /etc/cron.weekly/security-update 2>/dev/null || true
sysctl --system >/dev/null 2>&1 || true

echo "== 7/7 启动 =="
systemctl enable --now mariadb nginx fail2ban >/dev/null 2>&1 || true
systemctl restart mariadb
systemctl enable --now one-finger >/dev/null 2>&1 || true
systemctl restart one-finger
nginx -t && systemctl reload nginx

for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me || echo 000)
  [ "$code" = "401" ] && { echo "   后端就绪（$((i*2)) 秒）"; break; }
  sleep 2
done
[ "${code:-000}" = "401" ] || { echo "   ⚠ 后端没起来，看 journalctl -u one-finger -n 50"; exit 1; }

echo
echo "完成。请检查："
echo "  1) 站点：curl -I http://127.0.0.1/"
echo "  2) 登录一次，确认客户/预约/流水都在"
echo "  3) 上传一张封面，确认 /uploads 能访问（目录属主 onefinger）"
echo "  4) 云控制台安全组：放行 22 / 80"
echo "  5) 删除迁移包：rm -f $PKG"
