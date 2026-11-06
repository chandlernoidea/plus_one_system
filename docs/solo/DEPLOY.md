# 部署说明 · V0.8.0

> 目标：把单人版跑在**你自己的服务器**上，一个人用、手机能开。
> 形态是前端静态文件（nginx）+ 后端 jar（systemd）+ MySQL，同一台机器即可。

## 0. 前置

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 后端编译与运行 |
| Maven | 3.8+ | 只在构建机器上需要 |
| Node | 18+ | 只在构建机器上需要 |
| MySQL | 8.0+（MariaDB 10.6+ 亦可） | 数据存储 |
| nginx | 任意近版本 | 静态文件 + 反向代理 |

服务器目录约定（下面都用这套，换成自己的也行）：

```
/opt/one-finger/
├── backend/          # jar 与配置文件
│   ├── nail-demo-backend-0.8.0.jar
│   └── app.env       # 环境变量（chmod 600）
├── uploads/          # 封面等上传文件
└── backups/          # 备份
/var/www/one-finger/  # 前端 dist
```

## 1. 准备数据库

```sql
CREATE DATABASE nail_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'nail'@'127.0.0.1' IDENTIFIED BY '换成你的强密码';
GRANT ALL PRIVILEGES ON nail_demo.* TO 'nail'@'127.0.0.1';
FLUSH PRIVILEGES;
```

表结构与初始数据由后端启动时的 `spring.sql.init` 自动执行（脚本幂等，可重复跑），**不需要手工导入**。

## 2. 构建

在本地或构建机上：

```bash
# 后端
cd backend
mvn -B package            # 产物：target/nail-demo-backend-0.8.0.jar
mvn test                  # 12 例单测

# 前端
cd ../frontend
npm ci
npm run build             # 产物：dist/
```

把 `nail-demo-backend-0.8.0.jar` 传到 `/opt/one-finger/backend/`，
把 `frontend/dist/` 整个传到 `/var/www/one-finger/`。

## 3. 后端环境变量

`/opt/one-finger/backend/app.env`（`chmod 600`，不要进 git）：

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/nail_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=nail
SPRING_DATASOURCE_PASSWORD=换成你的强密码
JWT_SECRET=换成至少32位的随机串
APP_UPLOAD_DIR=/opt/one-finger/uploads
SERVER_PORT=8080
```

> **必须改的两项**：数据库密码、`JWT_SECRET`（默认值写在仓库里，等于公开）。
> `SPRING_DATASOURCE_URL` 里如果保留 `createDatabaseIfNotExist=true`，应用会用这个账号尝试建库，
> 生产环境建议去掉，库提前建好。

## 4. systemd 服务

`/etc/systemd/system/one-finger.service`：

```ini
[Unit]
Description=One Finger Studio backend
After=network.target mysql.service

[Service]
Type=simple
User=onefinger
WorkingDirectory=/opt/one-finger/backend
EnvironmentFile=/opt/one-finger/backend/app.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/one-finger/backend/nail-demo-backend-0.8.0.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo useradd -r -s /usr/sbin/nologin onefinger
sudo chown -R onefinger:onefinger /opt/one-finger
sudo systemctl daemon-reload
sudo systemctl enable --now one-finger
sudo systemctl status one-finger
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/auth/me   # 期望 401（未登录）
```

## 5. nginx

`/etc/nginx/conf.d/one-finger.conf`（换成你的域名；先 http，证书后面再挂）：

```nginx
server {
    listen 80;
    server_name nail.example.com;

    root /var/www/one-finger;
    index index.html;
    client_max_body_size 10m;

    # 后端接口
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 上传的封面图
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }

    # 前端是 history 路由，找不到的路径都回 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 带指纹的静态资源可以长缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

HTTPS（可选但强烈建议，手机端尤其）：

```bash
sudo certbot --nginx -d nail.example.com
```

## 6. 首次初始化（重要）

1. 浏览器打开 `http://nail.example.com`，用 `admin / 123456` 登录。
2. **立刻改掉这个初始密码**：

   ```sql
   UPDATE sys_user SET password = '你的新密码' WHERE username = 'admin';
   ```

   重启服务后 `PasswordMigrationRunner` 会自动把它转成 BCrypt 存储。

3. **把初始化数据脚本关掉**，否则每次重启 `data.sql` 会把 admin 密码重置回 `123456`：

   改 `/opt/one-finger/backend/application.yml`（jar 外置同名配置即可覆盖），
   或加环境变量 `SPRING_SQL_INIT_MODE=never`，然后 `systemctl restart one-finger`。

4. 想要一份能演示的数据：登录后 **设置 → 一键生成演示数据**
   （等价于 `curl -X POST -H "Authorization: Bearer <token>" http://127.0.0.1:8080/api/solo/demo`）。
   不想留演示数据就跳过这步。

> 以后升级时表结构有增补（比如 P5 加的 `color_notes`、P7 加的 `solo_setting`），
> `schema.sql` 与 `schema_solo.sql` 都是幂等的，可以手工执行一次再启动新 jar：
> `mysql -u nail -p nail_demo < backend/src/main/resources/db/schema_solo.sql`

## 7. 备份

数据库 + 上传目录：

```bash
mysqldump -u nail -p --single-transaction --routines nail_demo | gzip > /opt/one-finger/backups/db-$(date +%F).sql.gz
tar czf /opt/one-finger/backups/uploads-$(date +%F).tar.gz -C /opt/one-finger uploads
```

`crontab -e` 里每天凌晨跑一次：

```cron
0 3 * * * my script above / 或直接两行命令
```

恢复：`gunzip -c db-2026-11-06.sql.gz | mysql -u nail -p nail_demo`，再把 uploads 解回原位。

## 8. 日常发布（本地改完同步到服务器）

仓库里带了发布脚本，一条命令完成：构建 → 上传 → 需要时同步表结构 → 重启 → 自检。

```bash
# 第一次：配好免密登录，之后就不用输密码了
ssh-keygen -t ed25519            # 一路回车（已有密钥可跳过）
ssh-copy-id root@你的服务器IP

# 以后每次改完代码
./scripts/deploy-solo.sh root@你的服务器IP

# 这次改动了表结构（schema 里新增了列/表）
./scripts/deploy-solo.sh root@你的服务器IP --schema
```

脚本做的事：

1. `mvn package -DskipTests` + `npm run build`（`--skip-build` 可跳过，复用上次产物）；
2. jar 传成 `app.jar.new`，传完再原子替换成 `app.jar`（避免半截文件被读到）；
3. dist 清空重铺，属主改回 `onefinger`；
4. `--schema` 时把幂等的 `schema_solo.sql` 灌进库；
5. `systemctl restart one-finger`，轮询到后端返回 401 为止；
6. 从公网回测首页与接口，异常就报错退出。

> 服务固定跑 `/opt/one-finger/backend/app.jar`，所以升级不用改 systemd 单元。
> 服务器地址别写进仓库，用参数或 `export SOLO_HOST=root@1.2.3.4`。

手工发布（不想用脚本）：

```bash
cd backend && mvn -B package -DskipTests
cat target/nail-demo-backend-*.jar | ssh root@服务器 'cat > /opt/one-finger/backend/app.jar.new'
ssh root@服务器 'mv /opt/one-finger/backend/app.jar.new /opt/one-finger/backend/app.jar && chown onefinger:onefinger /opt/one-finger/backend/app.jar && systemctl restart one-finger'
cd ../frontend && npm run build && tar czf - -C dist . | ssh root@服务器 'rm -rf /var/www/one-finger/* && tar xzf - -C /var/www/one-finger'
```

## 8.1 改了账号/密码怎么找回

登录后在 **设置 → 账号** 里改登录名和密码（要先输当前密码，改完自动退出、旧 token 立即失效）。
忘了密码只能在服务器上改：

```sql
-- 写明文，重启后启动时的迁移会把明文转成 BCrypt
UPDATE sys_user SET password = '新密码', token_version = token_version + 1 WHERE username = 'admin';
```

改完 `systemctl restart one-finger`，用新密码登录。

## 9. 常见问题

| 现象 | 原因与处理 |
|---|---|
| 登录一直 401 | `JWT_SECRET` 改了但前端还存着旧 token；退出重登即可 |
| 页面能开但接口 404 | nginx 的 `location /api/` 少了反代，或后端没起来 |
| 刷新子路由 404 | 缺 `try_files $uri $uri/ /index.html` |
| 上传封面 404 | 没配 `/uploads/` 反代，或 `APP_UPLOAD_DIR` 指向的目录不可写 |
| 重启后密码又变回 123456 | `spring.sql.init.mode` 还是 `always`，见第 6 节第 3 步 |
| 时间对不上 | 数据库连接串与容器时区都要是 `Asia/Shanghai` |
| 手机端排版紧 | 断点在 900px；再窄的分辨率（<360px）会进入紧凑样式 |
| SSH 里打字像「多打了字」/卡顿 | 服务器 root 的 zsh 加载了 `zsh-syntax-highlighting` 与 `zsh-autosuggestions`：每按一个键都要退格重画整行并上色，远程连接下看起来就是字被重复、光标乱跳。已把 `/root/.zshrc` 精简为无插件版本（原文件备份在 `/root/.zshrc.plugins.bak`）。若仍异常，查本地终端的「本地回显 / Local echo」是否被打开（双重回显才会每个字符出现两次） |

## 10. 上线检查清单

- [ ] 数据库密码、`JWT_SECRET` 已换，`app.env` 权限 600
- [ ] admin 初始密码已改，`spring.sql.init.mode=never`
- [ ] 登录后在「设置 → 账号」再确认一次密码可改（改完会自动退出）
- [ ] nginx `try_files` 与 `/api`、`/uploads` 反代都通
- [ ] HTTPS 已挂，手机上打开正常
- [ ] 备份 cron 已跑过一次并确认能恢复
- [ ] 用 `admin` 登录后建一单、收定金、完成，确认业务链路正常
