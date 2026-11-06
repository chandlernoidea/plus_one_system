#!/usr/bin/env bash
#
# 一键安装：在全新的 Debian 12 / Ubuntu 22.04+ 上把单人版跑起来。
#
#   sudo bash install.sh                 # 默认 80 端口，用 IP 访问
#   sudo bash install.sh --port 8080     # 换端口
#   sudo bash install.sh --force         # 已有安装时强制覆盖（不会动数据库数据）
#
# 装之前请确认：这台机器是新的 / 可以占用 80 端口；云控制台安全组已放行 22 和 80。
set -euo pipefail

APP_DIR=/opt/one-finger
WEB_DIR=/var/www/one-finger
SERVICE=one-finger
PKG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=$(cat "$PKG_ROOT/VERSION" 2>/dev/null || echo "dev")
PORT=80
FORCE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --port) PORT="${2:?--port 需要一个数字}"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

log()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { printf '\n\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "请用 root 运行：sudo bash install.sh"
command -v apt-get >/dev/null || fail "只支持 Debian/Ubuntu 系（需要 apt）"

if [ -f "$APP_DIR/backend/app.env" ] && [ "$FORCE" -eq 0 ]; then
  fail "这台机器上已经装过了（$APP_DIR/backend/app.env 存在）。
   想重装请加 --force；只升级版本请用 scripts/deploy-solo.sh。"
fi

log "V${VERSION} · 安装依赖（JDK17 / MariaDB / nginx / fail2ban / ufw）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jre-headless mariadb-server nginx fail2ban ufw >/dev/null
systemctl enable --now mariadb nginx fail2ban >/dev/null 2>&1

log "生成密钥"
DB_PASS=$(openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 24)
JWT_SECRET=$(openssl rand -base64 60 | tr -dc 'A-Za-z0-9' | head -c 48)
ADMIN_PASS=$(openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 14)

log "建库建账号"
mariadb <<SQL
CREATE DATABASE IF NOT EXISTS nail_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'nail'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
ALTER USER 'nail'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON nail_demo.* TO 'nail'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
cat > "$APP_DIR/.my.cnf" <<EOF
[client]
user=nail
password=${DB_PASS}
host=127.0.0.1
EOF

log "铺文件"
id onefinger >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin onefinger
mkdir -p "$APP_DIR/backend" "$APP_DIR/uploads" "$APP_DIR/backups"
rm -rf "$WEB_DIR"; mkdir -p "$WEB_DIR"
cp "$PKG_ROOT/app/app.jar" "$APP_DIR/backend/app.jar"
cp -r "$PKG_ROOT/dist/." "$WEB_DIR/"
chmod 600 "$APP_DIR/.my.cnf"
chmod 750 "$APP_DIR"; chmod 700 "$APP_DIR/backups"

log "写配置（首次启动要建表，所以先用 always）"
cat > "$APP_DIR/backend/app.env" <<EOF
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/nail_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=nail
SPRING_DATASOURCE_PASSWORD=${DB_PASS}
JWT_SECRET=${JWT_SECRET}
APP_UPLOAD_DIR=${APP_DIR}/uploads
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
SPRING_SQL_INIT_MODE=always
EOF
chmod 600 "$APP_DIR/backend/app.env"

if [ -f "$PKG_ROOT/deploy/backup.sh" ]; then
  cp "$PKG_ROOT/deploy/backup.sh" "$APP_DIR/backup.sh"
  chmod 700 "$APP_DIR/backup.sh"
  echo "0 3 * * * onefinger $APP_DIR/backup.sh >/dev/null 2>&1" > /etc/cron.d/one-finger
fi

log "装 systemd 服务"
cp "$PKG_ROOT/deploy/one-finger.service" /etc/systemd/system/$SERVICE.service
chown -R onefinger:onefinger "$APP_DIR" "$WEB_DIR"
systemctl daemon-reload
systemctl enable $SERVICE >/dev/null 2>&1
systemctl restart $SERVICE

log "等后端建表（首次启动约 15 秒）"
code=000
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080/api/auth/me" || echo 000)
  [ "$code" = "401" ] && { echo "   就绪（$((i*2)) 秒）"; break; }
  sleep 2
done
[ "$code" = "401" ] || { journalctl -u $SERVICE -n 40 --no-pager; fail "后端没起来"; }

log "设置管理员密码并关掉初始化脚本"
mariadb nail_demo -e "UPDATE sys_user SET password='${ADMIN_PASS}', token_version=token_version+1 WHERE username='admin';"
sed -i 's/^SPRING_SQL_INIT_MODE=.*/SPRING_SQL_INIT_MODE=never/' "$APP_DIR/backend/app.env"
systemctl restart $SERVICE
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080/api/auth/me" || echo 000)
  [ "$code" = "401" ] && break
  sleep 2
done

log "配 nginx"
if [ "$PORT" != "80" ]; then
  sed "s/listen 80 /listen ${PORT} /; s/listen \[::\]:80 /listen [::]:${PORT} /" \
    "$PKG_ROOT/deploy/nginx-solo.conf" > /etc/nginx/sites-available/one-finger
else
  cp "$PKG_ROOT/deploy/nginx-solo.conf" /etc/nginx/sites-available/one-finger
fi
ln -sf /etc/nginx/sites-available/one-finger /etc/nginx/sites-enabled/one-finger
rm -f /etc/nginx/sites-enabled/default
nginx -t >/dev/null 2>&1 && systemctl reload nginx

log "加固（防火墙 / fail2ban / 内核参数）"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null
ufw allow "${PORT}/tcp" >/dev/null
[ "$PORT" = "80" ] && ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
if [ -f "$PKG_ROOT/deploy/99-hardening.local" ]; then
  cp "$PKG_ROOT/deploy/99-hardening.local" /etc/fail2ban/jail.d/99-hardening.local
  systemctl restart fail2ban
fi
if [ -f "$PKG_ROOT/deploy/99-hardening.conf" ]; then
  cp "$PKG_ROOT/deploy/99-hardening.conf" /etc/sysctl.d/99-hardening.conf
  sysctl --system >/dev/null 2>&1
fi

log "自检"
PAGE=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/ || echo 000)
LOGIN=$(curl -s -X POST http://127.0.0.1/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"${ADMIN_PASS}\"}" | grep -c '"token"' || true)
[ "$PAGE" = "200" ] || fail "站点返回 $PAGE，检查 nginx"
[ "$LOGIN" = "1" ] || fail "新密码登录失败，检查 /opt/one-finger/backend/app.env"

IP=$(curl -s --max-time 5 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
cat > /root/one-finger-credentials.txt <<EOF
单人版 V${VERSION} 凭据（本文件 600，仅 root 可读）

访问地址：http://${IP}${PORT:+$( [ "$PORT" = "80" ] && echo "" || echo ":$PORT" )}
应用登录：admin / ${ADMIN_PASS}
数据库：nail / ${DB_PASS}（库 nail_demo）
JWT 密钥：见 /opt/one-finger/backend/app.env
安装时间：$(date -Is)
EOF
chmod 600 /root/one-finger-credentials.txt

cat <<EOF

\033[32m✓ 安装完成（V${VERSION}）\033[0m

  地址      http://${IP}$( [ "$PORT" = "80" ] && echo "" || echo ":$PORT" )
  账号      admin
  密码      ${ADMIN_PASS}
  凭据文件  /root/one-finger-credentials.txt

  下一步：
    1. 云控制台安全组放行 ${PORT}（和 22）
    2. 登录后去「设置 → 账号」改掉这个密码
    3. 建议配 SSH 公钥后关闭密码登录（见 docs/OPS.md 第 8 节）
    4. 想先看有数据的样子：设置 → 一键生成演示数据

  常用命令：
    systemctl status ${SERVICE}
    journalctl -u ${SERVICE} -f
    nginx -t && systemctl reload nginx

EOF
