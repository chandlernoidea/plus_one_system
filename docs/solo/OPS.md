# 运维手册 · 单人美甲工作室

> 这份文档是「日常怎么跑」的操作手册：[DEPLOY.md](DEPLOY.md) 讲第一次怎么装，这里讲装上以后怎么用、怎么查、怎么修。
> 服务器与账号信息不写进仓库（本仓库是公开的），实际地址见线下记录。

## 1. 系统长什么样

```
浏览器 ──▶ nginx:80 ──┬─ /           静态文件（Vue 打包产物）
                      └─ /api,/uploads ──▶ Spring Boot:8080（只监听 127.0.0.1）
                                              └──▶ MariaDB:3306（只监听 127.0.0.1）
```

| 组件 | 位置 / 名字 | 说明 |
|---|---|---|
| 后端服务 | systemd `one-finger` | `java -jar /opt/one-finger/backend/app.jar` |
| 后端配置 | `/opt/one-finger/backend/app.env`（600） | 数据库、JWT 密钥、端口 |
| 前端 | `/var/www/one-finger` | nginx 静态目录 |
| 数据库 | MariaDB，库 `nail_demo`，用户 `nail`@127.0.0.1 | 26 张表（`solo_*` 11 张 + 多角色版表） |
| 上传文件 | `/opt/one-finger/uploads` | 款式封面 |
| 备份 | `/opt/one-finger/backups`（700） | 每天 03:00，保留 14 天 |
| 日志 | `journalctl -u one-finger`、`/var/log/nginx/` | journald 上限 200M |

## 2. 日常命令

```bash
# 服务
systemctl status one-finger        # 看状态
systemctl restart one-finger       # 重启（改了 app.env 或换了 jar）
journalctl -u one-finger -f        # 实时日志
journalctl -u one-finger -n 200 --no-pager   # 最近 200 行

# 前端 / 反代
nginx -t && systemctl reload nginx
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log

# 数据库
mariadb nail_demo                                  # 进库（root 走 socket 认证）
mariadb -e "SELECT COUNT(*) FROM nail_demo.solo_booking;"
systemctl status mariadb

# 备份
sudo -u onefinger /opt/one-finger/backup.sh        # 手动备份一次
ls -lh /opt/one-finger/backups/
```

## 3. 账号

### 应用账号（单人版）

只有一个账号（初始登录名 `admin`，可以在设置里改；线上当前是 `one_plus`）。
**登录后去 设置 → 账号** 可以改登录名和密码。

- 改完 `token_version` +1，**所有已登录会话立即失效**，需要重新登录；
- 忘记密码只能在服务器上改（写明文，重启时自动转 BCrypt）：

  ```sql
  -- 登录名可能已经改过，先确认：SELECT username FROM sys_user;
  UPDATE sys_user SET password='新密码', token_version=token_version+1 WHERE username='现在的登录名';
  ```
  ```bash
  systemctl restart one-finger
  ```

### 服务器账号

| 账号 | 用途 | 说明 |
|---|---|---|
| `root` | 系统管理、发布 | 密码见线下记录；**不要再用旧密码** |
| `onefinger` | 跑后端服务 | 无登录 shell（`nologin`），不能 SSH |
| `www-data` | nginx worker | 系统自带 |
| `nail` | 应用连数据库 | 只能从 127.0.0.1 连，权限仅 `nail_demo` |

> 更安全的下一步：自建一个 `ops` 用户加 sudo，配好公钥后把 SSH 的
> `PermitRootLogin` 改成 `prohibit-password`、`PasswordAuthentication` 改成 `no`。
> 改之前务必先用公钥登录验证一次，别把自己锁在外面。

## 4. 发布与回滚

本地改完代码：

```bash
./scripts/deploy-solo.sh root@服务器地址            # 普通发布
./scripts/deploy-solo.sh root@服务器地址 --schema   # 这次动了表结构
```

脚本会构建 → 上传 → 重启 → 自检，失败会直接报错退出。

**回滚**：服务固定跑 `/opt/one-finger/backend/app.jar`，所以

```bash
ssh root@服务器
cp /opt/one-finger/backend/app.jar /opt/one-finger/backend/app.jar.bad
# 换回上一个版本（如果你留了旧 jar）
cp /opt/one-finger/backend/nail-demo-backend-0.8.0.jar /opt/one-finger/backend/app.jar
systemctl restart one-finger
```

建议每次发布前把当前 jar 存一份带日期的副本：`cp app.jar app-$(date +%F).jar`。

前端回滚就是重铺 `dist`（仓库历史里 checkout 旧版本重新 build 即可）。

## 5. 备份与恢复

**自动**：`/etc/cron.d/one-finger`，每天 03:00 执行 `backup.sh`
→ `db-YYYY-MM-DD.sql.gz` + `uploads-YYYY-MM-DD.tar.gz`，保留 14 天。

**手动**：

```bash
sudo -u onefinger /opt/one-finger/backup.sh
```

**恢复数据库**：

```bash
gunzip -c /opt/one-finger/backups/db-2026-11-07.sql.gz | mariadb nail_demo
systemctl restart one-finger
```

**恢复上传文件**：

```bash
tar xzf /opt/one-finger/backups/uploads-2026-11-07.tar.gz -C /opt/one-finger
chown -R onefinger:onefinger /opt/one-finger/uploads
```

**每月做一次恢复演练**（重要）：把最新备份灌进一个临时库 `nail_demo_restore`，确认表和数据都在，再删掉。
没演练过的备份等于没有备份。

> 备份里含客户昵称、手机后四位、备注，属于个人信息。`backups` 目录已设 700。
> 如果要传到别处，建议先 `gpg -c` 加密。

## 6. 监控与巡检

没有告警系统，靠这三个视角：

| 频率 | 看什么 | 命令 / 判断 |
|---|---|---|
| 每天 | 服务在不在 | `systemctl is-active one-finger nginx mariadb` 三个都要 `active` |
| 每天 | 站点和接口通不通 | 打开首页能登录；`curl -o /dev/null -w '%{http_code}' http://127.0.0.1/api/auth/me` 应为 401 |
| 每周 | 磁盘、内存 | `df -h /`（低于 20% 要清备份）、`free -h` |
| 每周 | 安全更新 | `cat /var/log/security-update.log` |
| 每周 | 异常登录 | `sudo grep "Failed password" /var/log/auth.log | tail`、`fail2ban-client status sshd` |
| 每月 | 备份演练 | 见上一节 |

**健康检查的语义**：`/api/auth/me` 返回 **401 才是健康的**（接口在、只是没登录）；返回 502 说明后端挂了，返回 000 说明连不上。

## 7. 故障处理手册

| 症状 | 排查顺序 |
|---|---|
| 首页 502 | 1) `systemctl is-active one-finger`；2) `journalctl -u one-finger -n 100` 找 `Caused by`；3) 如果 8080 没监听，多半是 `app.env` 里数据库密码错了或 MariaDB 没起 |
| 首页打不开、接口 000 | 1) `ufw status` 确认 80 放行；2) 火山控制台安全组是否放行 80；3) `systemctl is-active nginx` |
| 登录一直失败 | 1) 提示「用户名或密码错误」→ 密码错，或改过密码后旧 token 失效（正常）；2) 提示「尝试太频繁」→ 触发登录限速，等 1 分钟；3) 401 但密码对 → `token_version` 变了，重新登录即可 |
| 页面能开但数据空 | 1) 浏览器 F12 看 XHR 是不是 401/500；2) `journalctl` 有没有 SQL 异常；3) 数据库里有没有数据（`SELECT COUNT(*) FROM solo_booking;`） |
| 上传封面 404 | 1) `ls /opt/one-finger/uploads`；2) 目录属主是否 `onefinger`；3) `/uploads/` 反代是否在 |
| 时间不对 | `timedatectl` 确认时区 `Asia/Shanghai`；`app.env` 的 JDBC URL 要带 `serverTimezone=Asia/Shanghai` |
| 磁盘满 | `du -sh /opt/one-finger/backups`；清理旧备份；`journalctl --vacuum-size=200M` |
| 内存不够（2G 机器） | `free -h`；必要时把 systemd 里的 `-Xmx512m` 调小；MariaDB 可加 `innodb_buffer_pool_size=128M` |
| 被爆破打到卡 | `fail2ban-client status sshd` 看 banned 数；必要时临时 `ufw deny from <IP>` |
| 忘了 admin 密码 | 见第 3 节 SQL |

## 8. 安全基线（2026-11-07 加固后的状态）

### 已做

| 项 | 现状 |
|---|---|
| 系统补丁 | 安全源更新全部应用（0 挂起）；`/etc/cron.weekly/security-update` 每周自动装安全包 |
| 内核 | 已装 6.1.0-54，**运行中仍是 6.1.0-18 → 需要重启才生效** |
| 防火墙 | ufw 启用：默认拒绝入站，只放行 22 / 80 / 443 |
| SSH | 关闭 X11 与 agent 转发、`MaxAuthTries 3`、`LoginGraceTime 30`、`ClientAliveInterval 300`、`LogLevel VERBOSE` |
| 暴力破解 | fail2ban 两个 jail：`sshd`（4 次失败封 4 小时）+ `recidive`（反复被封封一周） |
| 登录限速 | nginx 对 `/api/auth/login` 限 10 次/分钟（burst 5），超出回 JSON 429 |
| 内核参数 | 防 SYN flood、忽略 ICMP 重定向与源路由、`kptr_restrict=2`、`dmesg_restrict=1`、`ptrace_scope=1` |
| 数据库 | 只监听 127.0.0.1；无匿名用户、无 test 库；`local_infile=OFF` |
| 应用 | 后端只监听 127.0.0.1；JWT 密钥 48 位随机；改密后旧 token 立即失效；`/api/solo/**` 仅 ADMIN |
| Web | 隐藏 nginx 版本、CSP、X-Frame-Options、nosniff、Referrer-Policy、Permissions-Policy、拒绝访问隐藏文件 |
| 权限 | `app.env`/`.my.cnf` 600，`backups` 700，`/opt/one-finger` 750 |
| 口令 | root 密码已换为 20 位随机；admin 密码 14 位随机 |

### 待办（按优先级）

1. **重启一次**让新内核生效：`reboot`（选凌晨，重启后 `uname -r` 应为 `6.1.0-54-amd64`）。
2. **配 SSH 公钥并关闭密码登录**：`ssh-copy-id` → 用公钥登录验证一次 → 改 `PasswordAuthentication no` 和 `PermitRootLogin prohibit-password` → `sshd -t && systemctl reload ssh`。
3. **上 HTTPS**：有域名后 `certbot --nginx -d 域名`，再加 HSTS。
4. 备份加密/异地存放。
5. 定期（季度）跑一次 `apt list --upgradable`，把非安全源的大版本更新也安排上。

### 自查命令

```bash
ufw status verbose                      # 防火墙
fail2ban-client status                  # 两个 jail 都在
ss -ltn                                 # 只应有 22 / 80（3306、8080 仅在 127.0.0.1）
apt list --upgradable | grep -c security  # 期望 0
timedatectl | grep "Time zone"          # Asia/Shanghai
curl -sI http://127.0.0.1/ | grep -i content-security   # CSP 在
```

## 9. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-06 | 首次部署：Debian 12 + JDK17 + MariaDB 10.11 + nginx，V0.8.0 |
| 2026-11-06 | 精简 `/root/.zshrc`（去掉逐键重画的 zsh 插件），修 SSH 输入异常 |
| 2026-11-07 | 安全加固：补丁、防火墙、fail2ban、SSH、内核参数、DB、nginx、权限、口令轮换 |

> 加固后实测：登录/建单/导出九宫格案例图全部正常，CSP 无拦截（canvas → data URL → 下载成功）。
