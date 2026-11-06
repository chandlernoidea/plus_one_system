#!/usr/bin/env bash
#
# 单人版一键发布：本地构建 → 上传 → 需要时同步表结构 → 重启 → 自检
#
#   ./scripts/deploy-solo.sh root@1.2.3.4              # 发布
#   ./scripts/deploy-solo.sh root@1.2.3.4 --schema     # 表结构也一起更新
#   ./scripts/deploy-solo.sh --skip-build root@1.2.3.4 # 复用上次的构建产物
#
# 也可以把服务器写进环境变量：export SOLO_HOST=root@1.2.3.4
#
# 第一次跑会问 SSH 密码；配好密钥（ssh-copy-id）之后就不问了。
set -euo pipefail

cd "$(dirname "$0")/.."

HOST="${SOLO_HOST:-}"
SCHEMA=0
SKIP_BUILD=0

for arg in "$@"; do
  case "$arg" in
    --schema) SCHEMA=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    -*) echo "未知参数：$arg"; exit 2 ;;
    *) HOST="$arg" ;;
  esac
done

if [ -z "$HOST" ]; then
  echo "用法：$0 <root@服务器地址> [--schema] [--skip-build]"
  exit 2
fi

APP_DIR=/opt/one-finger
WEB_DIR=/var/www/one-finger
SERVICE=one-finger
# 注意：pom 里第一个 <version> 是 Spring Boot 父版本，要取 </parent> 之后的那个
VERSION=$(sed -n '/<\/parent>/,$p' backend/pom.xml | grep -m1 "<version>" | sed -E 's/.*<version>(.*)<\/version>.*/\1/')

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

say "发布 V${VERSION} → ${HOST}"

if [ "$SKIP_BUILD" -eq 0 ]; then
  say "1/5 构建后端"
  (cd backend && mvn -B -q package -DskipTests)
  say "2/5 构建前端"
  (cd frontend && npm run build)
else
  say "1-2/5 跳过构建（--skip-build）"
fi

JAR="backend/target/nail-demo-backend-${VERSION}.jar"
if [ ! -f "$JAR" ]; then
  echo "找不到 $JAR（版本号与 pom 对不上，或没构建）。去掉 --skip-build 再跑一次。"
  exit 1
fi

say "3/5 上传（jar $(du -h "$JAR" | cut -f1)，前端 $(du -sh frontend/dist | cut -f1)）"
# 先传成 .new，传完再替换，避免传到一半被服务读到半个文件
cat "$JAR" | ssh "$HOST" "cat > ${APP_DIR}/backend/app.jar.new"
tar czf - -C frontend/dist . | ssh "$HOST" "rm -rf ${WEB_DIR}/* && tar xzf - -C ${WEB_DIR} && chown -R onefinger:onefinger ${WEB_DIR}"

if [ "$SCHEMA" -eq 1 ]; then
  say "3.5/5 同步表结构（幂等脚本）"
  ssh "$HOST" "mariadb nail_demo" < backend/src/main/resources/db/schema_solo.sql
  echo "表结构已更新"
fi

say "4/5 替换 jar 并重启服务"
ssh "$HOST" "mv ${APP_DIR}/backend/app.jar.new ${APP_DIR}/backend/app.jar \
  && chown onefinger:onefinger ${APP_DIR}/backend/app.jar \
  && systemctl restart ${SERVICE}"

say "5/5 等它起来并自检"
for i in $(seq 1 40); do
  code=$(ssh "$HOST" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me" || echo 000)
  if [ "$code" = "401" ]; then
    echo "后端就绪（${i} 秒）"
    break
  fi
  sleep 2
done

if [ "$code" != "401" ]; then
  echo "后端没有正常起来，看日志：ssh $HOST journalctl -u $SERVICE -n 50 --no-pager"
  exit 1
fi

PAGE=$(curl -s -o /dev/null -w '%{http_code}' "http://${HOST#*@}/" || echo 000)
API=$(curl -s -o /dev/null -w '%{http_code}' "http://${HOST#*@}/api/auth/me" || echo 000)
echo "站点首页：${PAGE}   接口：${API}（401 表示接口在、只是没登录）"

if [ "$PAGE" != "200" ] || [ "$API" != "401" ]; then
  echo "⚠ 外部访问异常，检查安全组是否放行 80 端口"
  exit 1
fi

say "完成：http://${HOST#*@}/"
