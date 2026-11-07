-- 单人美甲工作室版 —— 初始化数据
--
-- 单人版只有一个账号（老板），没有员工、没有顾客账号。
-- 业务数据（客户 / 款式 / 预约 / 物料 / 流水）由应用自己产生，这里不种演示数据。
--
-- 本脚本可重复执行：唯一键 + ON DUPLICATE KEY UPDATE。
-- 初始密码 123456（BCrypt, cost=10）。部署后必须改掉：见 docs/solo/DEPLOY.md 第 6 节。

INSERT INTO sys_role (role_code, role_name)
VALUES ('ADMIN', '管理员')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 只有一个管理员账号；PasswordMigrationRunner 会兼容迁移历史遗留的明文密码。
INSERT INTO sys_user (username, password, real_name, status)
VALUES ('admin', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '管理员', 'ACTIVE')
ON DUPLICATE KEY UPDATE password  = VALUES(password),
                        real_name = VALUES(real_name),
                        status    = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
VALUES ((SELECT id FROM sys_user WHERE username = 'admin'),
        (SELECT id FROM sys_role WHERE role_code = 'ADMIN'));
