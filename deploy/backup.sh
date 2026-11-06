#!/bin/bash
# 每日备份：数据库 + 上传目录，保留最近 14 天
set -euo pipefail
DIR=/opt/one-finger/backups
STAMP=$(date +%F)
mysqldump --defaults-extra-file=/opt/one-finger/.my.cnf --single-transaction --routines nail_demo | gzip > "$DIR/db-$STAMP.sql.gz"
tar czf "$DIR/uploads-$STAMP.tar.gz" -C /opt/one-finger uploads
find "$DIR" -name "*.gz" -mtime +14 -delete
