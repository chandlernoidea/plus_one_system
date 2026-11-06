#!/usr/bin/env bash
#
# 一键打包：产出一个可以直接扔到新服务器上安装的发布包。
#
#   ./scripts/package-release.sh
#
# 产物：release/one-finger-v<版本>.tar.gz（含 jar、前端 dist、部署配置、安装脚本、文档）
#       release/one-finger-v<版本>.tar.gz.sha256
#
# 包内结构：
#   VERSION              版本号
#   app/app.jar          后端
#   dist/                前端静态文件
#   deploy/              systemd 单元、nginx 站点、env 模板、备份脚本、加固配置
#   scripts/install.sh   一键安装（新机器上 sudo bash install.sh）
#   docs/                部署与运维文档
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
OUT="$ROOT/release"
# 注意：pom 里第一个 <version> 是 Spring Boot 父版本，要取 </parent> 之后的那个
VERSION=$(sed -n '/<\/parent>/,$p' backend/pom.xml | grep -m1 "<version>" | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
NAME="one-finger-v${VERSION}"
STAGE=$(mktemp -d)
PKG_DIR="$STAGE/$NAME"
trap 'rm -rf "$STAGE"' EXIT

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

say "打包 V${VERSION}"
say "1/5 构建后端"
# 先清掉旧版本的 jar：Spring Boot 打出来的 jar 时间戳被固定成 1980，
# 用 ls -t 分不清新旧，只能按版本号取
rm -f backend/target/nail-demo-backend-*.jar
(cd backend && mvn -B -q package -DskipTests)
JAR="backend/target/nail-demo-backend-${VERSION}.jar"
[ -f "$JAR" ] || { echo "没找到 $JAR，构建可能失败"; exit 1; }

say "2/5 构建前端"
(cd frontend && npm run build)

say "3/5 收集文件"
mkdir -p "$PKG_DIR/app" "$PKG_DIR/dist" "$PKG_DIR/deploy" "$PKG_DIR/scripts" "$PKG_DIR/docs"
echo "$VERSION" > "$PKG_DIR/VERSION"
cp "$JAR" "$PKG_DIR/app/app.jar"
cp -r frontend/dist/. "$PKG_DIR/dist/"
cp deploy/one-finger.service deploy/nginx-solo.conf deploy/app.env.example \
   deploy/backup.sh "$PKG_DIR/deploy/" 2>/dev/null || true
# 安全加固配置（如果本机按 OPS 配过，就一起带进包里，新机器安装时自动应用）
for pair in "/etc/fail2ban/jail.d/99-hardening.local:deploy/99-hardening.local" \
            "/etc/sysctl.d/99-hardening.conf:deploy/99-hardening.conf"; do
  src="${pair%%:*}"; dst="${pair##*:}"
  [ -f "$src" ] && cp "$src" "$ROOT/$dst"
done
[ -f deploy/99-hardening.local ] && cp deploy/99-hardening.local "$PKG_DIR/deploy/" 2>/dev/null || true
[ -f deploy/99-hardening.conf ] && cp deploy/99-hardening.conf "$PKG_DIR/deploy/" 2>/dev/null || true
cp scripts/install.sh scripts/deploy-solo.sh scripts/migrate-export.sh scripts/migrate-import.sh \
   "$PKG_DIR/scripts/" 2>/dev/null || true
cp docs/solo/DEPLOY.md docs/solo/OPS.md docs/solo/README.md "$PKG_DIR/docs/" 2>/dev/null || true
cp CHANGELOG.md "$PKG_DIR/docs/" 2>/dev/null || true

cat > "$PKG_DIR/README.txt" <<EOF
指间工作室 · 单人美甲工作室版 V${VERSION}
==========================================

这是一个可直接安装的发布包。

新服务器（Debian 12 / Ubuntu 22.04+）上：

    tar xzf one-finger-v${VERSION}.tar.gz
    cd one-finger-v${VERSION}
    sudo bash scripts/install.sh

装完会打印访问地址和初始账号密码（也写在 /root/one-finger-credentials.txt）。

已有服务器升级（在本地仓库里）：

    ./scripts/deploy-solo.sh root@服务器地址

换服务器迁移（数据一起搬走）：

    ssh root@旧服务器 'bash -s' < scripts/migrate-export.sh
    见 docs/solo/OPS.md 第 9 节

文档：
    docs/DEPLOY.md   第一次部署、日常发布、找回密码
    docs/OPS.md      运维手册：命令、备份、巡检、故障处理、安全基线、迁移
EOF

say "4/5 打 tar.gz"
mkdir -p "$OUT"
tar czf "$OUT/${NAME}.tar.gz" -C "$STAGE" "$NAME"
(cd "$OUT" && sha256sum "${NAME}.tar.gz" > "${NAME}.tar.gz.sha256")
SIZE=$(du -h "$OUT/${NAME}.tar.gz" | cut -f1)

say "5/5 校验包内容"
echo "   文件数：$(tar tzf "$OUT/${NAME}.tar.gz" | wc -l)"
tar tzf "$OUT/${NAME}.tar.gz" | grep -E "(app.jar|dist/index.html|scripts/install.sh|VERSION)$" | sed 's/^/   /'
echo "   jar 大小：$(tar xzf "$OUT/${NAME}.tar.gz" -O "$NAME/app/app.jar" | wc -c | awk '{printf "%.0f MB\n", $1/1024/1024}')"

say "完成"
echo "   $OUT/${NAME}.tar.gz  (${SIZE})"
echo "   $OUT/${NAME}.tar.gz.sha256"
echo
echo "传到新服务器："
echo "   scp $OUT/${NAME}.tar.gz root@新服务器:/root/"
echo "   ssh root@新服务器 'tar xzf /root/${NAME}.tar.gz && sudo bash ${NAME}/scripts/install.sh'"
echo
echo "（release/ 已在 .gitignore 里，不会把 50M 的二进制提交进仓库）"
