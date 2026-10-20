-- 预约制美甲管理系统 - 初始化数据
-- 本脚本可重复执行：
-- 1. 用户/角色/服务使用唯一键 + ON DUPLICATE KEY UPDATE；
-- 2. 关联表和档案使用 INSERT IGNORE 或唯一键 + ON DUPLICATE KEY UPDATE。
--
-- 安全提示：阶段 1 起密码统一使用 BCrypt 存储。
-- 以下 hash 均对应明文 123456，仅用于本地演示；生产环境必须使用强密码并妥善保管 hash。

INSERT INTO sys_role (role_code, role_name)
VALUES ('ADMIN', '管理员'),
       ('STAFF', '美甲师'),
       ('CUSTOMER', '顾客')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 阶段 1 演示账号：admin / staff1 / cust1，密码均为 123456。
-- 下列 BCrypt hash 由 BCryptPasswordEncoder.encode("123456") 生成，cost=10。
-- PasswordMigrationRunner 会兼容迁移历史遗留的明文密码。
INSERT INTO sys_user (username, password, real_name, status)
VALUES ('admin', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '管理员', 'ACTIVE'),
       ('staff1', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '美甲师小王', 'ACTIVE'),
       ('cust1', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '顾客小李', 'ACTIVE')
ON DUPLICATE KEY UPDATE password  = VALUES(password),
                        real_name = VALUES(real_name),
                        status    = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
VALUES ((SELECT id FROM sys_user WHERE username = 'admin'),
        (SELECT id FROM sys_role WHERE role_code = 'ADMIN')),
       ((SELECT id FROM sys_user WHERE username = 'staff1'),
        (SELECT id FROM sys_role WHERE role_code = 'STAFF')),
       ((SELECT id FROM sys_user WHERE username = 'cust1'),
        (SELECT id FROM sys_role WHERE role_code = 'CUSTOMER'));

INSERT INTO staff_profile (user_id, staff_no, bio, hire_date, status)
SELECT u.id, 'S001', '擅长本甲款式与延长甲', '2024-01-01', 'ACTIVE'
FROM sys_user u
WHERE u.username = 'staff1'
ON DUPLICATE KEY UPDATE staff_no = VALUES(staff_no),
                        bio      = VALUES(bio),
                        status   = VALUES(status);

-- service_type.name 在 schema.sql 中设有唯一索引，保证本脚本重复执行不会产生重复服务。
INSERT INTO service_type (name, est_duration_min, est_duration_max, buffer_minutes, description, status)
VALUES ('本甲纯色', 60, 120, 30, '基础本甲纯色涂装', 'ACTIVE'),
       ('本甲款式', 90, 180, 30, '本甲跳色、渐变、贴饰等款式', 'ACTIVE'),
       ('延长甲', 180, 240, 60, '延长甲制作与造型', 'ACTIVE'),
       ('复杂手绘', 240, 300, 60, '复杂手绘图案定制', 'ACTIVE')
ON DUPLICATE KEY UPDATE est_duration_min = VALUES(est_duration_min),
                        est_duration_max = VALUES(est_duration_max),
                        buffer_minutes   = VALUES(buffer_minutes),
                        description      = VALUES(description),
                        status           = VALUES(status);
