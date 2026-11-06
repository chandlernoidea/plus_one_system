#!/usr/bin/env bash
#
# 在【旧服务器】上执行：把整套数据与配置打成一个迁移包，拷去新服务器。
#
#   ssh root@旧服务器 'bash -s' < scripts/migrate-export.sh
#   或把本脚本 scp 上去后： ./migrate-export.sh
#
# 产物：/root/one-finger-migrate-YYYY-MM-DD.tar.gz（600，内含数据库密码等敏感信息）
set -euo pipefail

APP_DIR=/opt/one-finger
STAMP=$(date +%F)
PKG="/root/one-finger-migrate-${STAMP}.tar.gz"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "== 1/6 导出数据库 =="
mysqldump --defaults-extra-file="${APP_DIR}/.my.cnf" \
  --single-transaction --routines --databases nail_demo > "$TMP/nail_demo.sql"
echo "   $(du -h "$TMP/nail_demo.sql" | cut -f1)"

echo "== 2/6 打包上传文件 =="
tar czf "$TMP/uploads.tar.gz" -C "$APP_DIR" uploads
echo "   $(du -h "$TMP/uploads.tar.gz" | cut -f1)（空目录也算正常）"

echo "== 3/6 带上当前运行的 jar =="
cp "$APP_DIR/backend/app.jar" "$TMP/app.jar"

echo "== 4/6 收集配置 =="
cp "$APP_DIR/backend/app.env" "$TMP/app.env"
cp "$APP_DIR/.my.cnf" "$TMP/my.cnf"
cp /etc/systemd/system/one-finger.service "$TMP/one-finger.service"
cp /etc/nginx/sites-available/one-finger "$TMP/nginx-site.conf"
cp /etc/cron.d/one-finger "$TMP/cron-one-finger" 2>/dev/null || true
cp /etc/cron.weekly/security-update "$TMP/cron-security-update" 2>/dev/null || true

# 安全加固配置（新机器上照着恢复，省得重新配一遍）
# 注意改成互不重名的文件名：不同目录下有同名文件（都叫 99-hardening.conf）
copy_as() {
  [ -f "$1" ] && cp "$1" "$TMP/$2"
}
copy_as /etc/ssh/sshd_config.d/99-hardening.conf      sshd-hardening.conf
copy_as /etc/sysctl.d/99-hardening.conf               sysctl-hardening.conf
copy_as /etc/fail2ban/jail.d/99-hardening.local       fail2ban-hardening.local
copy_as /etc/mysql/mariadb.conf.d/99-hardening.cnf    mariadb-hardening.cnf
copy_as /etc/nginx/nginx.conf                         nginx.conf

echo "== 5/6 打包 =="
tar czf "$PKG" -C "$TMP" .
chmod 600 "$PKG"
echo "   $PKG  $(du -h "$PKG" | cut -f1)"

echo "== 6/6 摘要 =="
mariadb -N -e "SELECT CONCAT('  账号 ', COUNT(*), ' 个') FROM nail_demo.sys_user;"
mariadb -N -e "SELECT CONCAT('  客户 ', COUNT(*), ' 位，款式 ', (SELECT COUNT(*) FROM nail_demo.solo_style), ' 个，预约 ', (SELECT COUNT(*) FROM nail_demo.solo_booking), ' 单') FROM nail_demo.solo_client;"
echo
echo "下一步："
echo "  scp $PKG root@新服务器:/root/"
echo "  ssh root@新服务器 'bash -s' < scripts/migrate-import.sh -- $PKG"
echo
echo "提醒：包里含数据库密码与 JWT 密钥，用完请删除 $PKG"
