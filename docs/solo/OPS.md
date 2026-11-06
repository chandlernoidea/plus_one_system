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
cp /opt/one-finger/backend/nail-demo-backend-0.8.5.jar /opt/one-finger/backend/app.jar
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

## 9. 换服务器（迁移到新机器）

服务器到期、换云厂商、升级配置都走这套。**核心原则：新机器跑通并验证过之前，旧机器不要停、不要退订。**

### 10.1 时间线

| 时间 | 做什么 |
|---|---|
| 提前 3 天 | 新服务器按 [DEPLOY.md](DEPLOY.md) 装好 JDK17 / MariaDB / nginx；云控制台安全组放行 22、80；把 migrations 脚本从仓库取下来 |
| 提前 1 天 | 在旧机器上跑一次导出脚本，把包导进新机器的临时库 `nail_demo_test`，确认能还原（演练一次） |
| 迁移当天 | ① 旧机器导出 → ② 拷包 → ③ 新机器导入 → ④ 验证 → ⑤ 切换入口（改域名解析或告知新 IP）→ ⑥ 停旧机器 |
| 之后 3–7 天 | 旧机器保持能开机、能回滚；确认新机器一切正常后再退订 |

### 10.2 具体步骤

**第 1 步 · 旧机器打包**（不停机，热备）：

```bash
ssh root@旧服务器
bash -s < scripts/migrate-export.sh      # 或在仓库里 scp 上去执行
# 产物：/root/one-finger-migrate-YYYY-MM-DD.tar.gz
```

包里含：数据库全量 dump、上传文件、**当前运行的 jar**、`app.env`、systemd 单元、
nginx 站点、备份与安全更新的 cron、以及安全加固的配置文件（sshd / sysctl / fail2ban / mariadb）。

**第 2 步 · 拷到新机器**：

```bash
scp /root/one-finger-migrate-*.tar.gz root@新服务器:/root/
```

**第 3 步 · 新机器导入**：

```bash
ssh root@新服务器
bash migrate-import.sh /root/one-finger-migrate-YYYY-MM-DD.tar.gz
```

脚本会：建库建用户 → 导数据 → 铺 jar / 上传文件 / 配置 → 装 systemd 与 nginx 站点 →
恢复 cron 与安全配置 → 启动并等到 `/api/auth/me` 返回 401。

**第 4 步 · 手工验证（必做，别只看脚本输出）**：

```bash
curl -I http://127.0.0.1/                       # 200
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/auth/me   # 401
mariadb -N -e "SELECT COUNT(*) FROM nail_demo.solo_client;"          # 和旧机器一致
```

然后用浏览器登录一次：客户列表、今天的作业单、财务三本账都在；
上传一张款式封面，点开能显示（验证 `/opt/one-finger/uploads` 属主与 `/uploads` 反代）。

**第 5 步 · 切换入口**：

- 有域名：改 DNS 解析到新 IP（提前把 TTL 调小到 300 秒，避免切换后长时间指向旧机器）；
- 只用 IP：把新地址告诉使用的人，旧地址保留一段时间做跳转说明；
- 有 HTTPS：证书要在新机器重新签（`certbot --nginx -d 域名`），DNS 生效后再签。

### 10.3 容易踩的坑

| 坑 | 说明 |
|---|---|
| **`SPRING_SQL_INIT_MODE` 一定要是 `never`** | 迁移包里带的是你现在的 `app.env`（已经是 never）。如果在新机器手工新建配置却用了 `always`，下次重启 `data.sql` 会把账号密码重置回初始值 |
| **时区** | 新机器必须 `Asia/Shanghai`：`timedatectl set-timezone Asia/Shanghai`。systemd 单元里已有 `-Duser.timezone=Asia/Shanghai`，JDBC URL 里也有 `serverTimezone`，但主机时区不对仍会影响 MariaDB 的 `NOW()` 与备份文件名 |
| **上传文件没带走** | 只导数据库会登录正常、客户在、**封面全是 404**。迁移包里 `uploads.tar.gz` 就是干这个的 |
| **前端产物** | 迁移包默认不带 `dist`（可 rebuild）。如果没带，导入脚本会提示，此时在本地跑 `./scripts/deploy-solo.sh root@新IP` 补传 |
| **权限** | `/opt/one-finger` 属主必须是 `onefinger`，否则后端起不来或上传失败。导入脚本已处理，手工搬的要自己 `chown -R` |
| **安全组** | 新机器默认可能只开 22。80 不开 → 站点打不开；3306/8080 **不要**开 |
| **数据库密码** | 迁移包里带着旧密码。换机器是个换密码的好时机：改 MySQL 用户密码 + `app.env`，然后重启服务 |
| **JWT 密钥** | 换掉 `JWT_SECRET` 会让所有已登录会话失效（单人版重新登录即可）。如果不换，旧 token 在新机器上依然有效 |
| **迁移包本身** | 里面有客户昵称、手机后四位、数据库密码，600 权限，**用完立刻删** |
| **别忘了退订前确认** | 旧机器至少保留一周；确认新机器备份 cron 真的跑过一次（`ls /opt/one-finger/backups`）再退 |

### 10.4 迁移后收尾

- 在新机器跑一次 `sudo -u onefinger /opt/one-finger/backup.sh`，确认备份产物生成；
- 按第 8 节「自查命令」过一遍安全基线（防火墙、fail2ban、补丁、CSP）；
- 更新线下记录里的服务器地址与 root 密码；
- 旧机器 `systemctl stop` 或直接关机，一周后确认无误再退订。

## 10. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-06 | 首次部署：Debian 12 + JDK17 + MariaDB 10.11 + nginx，V0.8.0 |
| 2026-11-07 | 发布 V0.8.4：新增一键安装包与打包脚本 |
| 2026-11-06 | 精简 `/root/.zshrc`（去掉逐键重画的 zsh 插件），修 SSH 输入异常 |
| 2026-11-07 | 安全加固：补丁、防火墙、fail2ban、SSH、内核参数、DB、nginx、权限、口令轮换 |
| 2026-11-07 | 重启服务器，内核从 6.1.0-18 切到 6.1.0-54；重启后全部服务自启正常，启动耗时 9.1s，无启动错误 |

> 加固后实测：登录/建单/导出九宫格案例图全部正常，CSP 无拦截（canvas → data URL → 下载成功）。
