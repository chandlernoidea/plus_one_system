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

-- ============================================================
-- 阶段 7：答辩演示种子数据
-- 说明：
-- 1. 全部使用固定演示 ID（9001 起）和 INSERT IGNORE，重复启动不会覆盖演示过程。
-- 2. 日期以首次启动时的 CURDATE()/NOW() 为基准。
-- 3. 需要重新演示时，请在测试库中删除 nail_demo 后重启，或按 README 的“重置演示数据”操作。
-- ============================================================

SET @demo_today = CURDATE();
SET @demo_tomorrow = DATE_ADD(CURDATE(), INTERVAL 1 DAY);
SET @demo_customer_id = (SELECT id FROM sys_user WHERE username = 'cust1' LIMIT 1);
SET @demo_staff_user_id = (SELECT id FROM sys_user WHERE username = 'staff1' LIMIT 1);
SET @demo_admin_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);
SET @demo_staff_id = (SELECT id FROM staff_profile WHERE user_id = @demo_staff_user_id LIMIT 1);
SET @demo_service_id = (SELECT id FROM service_type WHERE name = '本甲纯色' LIMIT 1);

-- 演示排班：今天、明天
INSERT IGNORE INTO staff_schedule
    (id, staff_id, work_date, start_time, end_time, schedule_type, status, remark)
VALUES
    (9001, @demo_staff_id, @demo_today, '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '演示排班-今天'),
    (9002, @demo_staff_id, @demo_tomorrow, '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '演示排班-明天');

-- 今天 09:00-18:00 时间槽；16:00-18:00 为已确认预约占用
INSERT IGNORE INTO time_slot (staff_id, slot_start, slot_end, status)
SELECT @demo_staff_id,
       TIMESTAMP(@demo_today, '09:00:00') + INTERVAL n * 15 MINUTE,
       TIMESTAMP(@demo_today, '09:00:00') + INTERVAL (n + 1) * 15 MINUTE,
       CASE
           WHEN TIMESTAMP(@demo_today, '09:00:00') + INTERVAL n * 15 MINUTE
                >= TIMESTAMP(@demo_today, '16:00:00')
            THEN 'BOOKED'
           ELSE 'AVAILABLE'
       END
FROM (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
    UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
    UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31
    UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
) AS demo_nums;

-- 明天 09:00-18:00 时间槽；13:00-15:00 为改约中的旧预约占用
INSERT IGNORE INTO time_slot (staff_id, slot_start, slot_end, status)
SELECT @demo_staff_id,
       TIMESTAMP(@demo_tomorrow, '09:00:00') + INTERVAL n * 15 MINUTE,
       TIMESTAMP(@demo_tomorrow, '09:00:00') + INTERVAL (n + 1) * 15 MINUTE,
       CASE
           WHEN TIMESTAMP(@demo_tomorrow, '09:00:00') + INTERVAL n * 15 MINUTE
                >= TIMESTAMP(@demo_tomorrow, '13:00:00')
            AND TIMESTAMP(@demo_tomorrow, '09:00:00') + INTERVAL n * 15 MINUTE
                < TIMESTAMP(@demo_tomorrow, '15:00:00')
            THEN 'BOOKED'
           ELSE 'AVAILABLE'
       END
FROM (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
    UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
    UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30 UNION ALL SELECT 31
    UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
) AS demo_nums;

-- 已完成预约 3 条：用于结算、评价、完工图展示
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, actual_start, actual_end, status, finish_image_urls,
     total_amount, no_show_flag, no_show_exempt, approved_by, approved_at,
     created_at, updated_at)
VALUES
    (9001, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '10:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '10:00:00'), 120,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '10:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '11:00:00'), 'COMPLETED',
     '/demo/finish-1.svg,/demo/finish-2.svg', 680.00, 0, 0,
     @demo_admin_id, DATE_SUB(NOW(), INTERVAL 3 DAY),
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (9002, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '13:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '13:00:00'), 120,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '13:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '14:00:00'), 'COMPLETED',
     '/demo/finish-2.svg', 1280.00, 0, 0,
     @demo_admin_id, DATE_SUB(NOW(), INTERVAL 3 DAY),
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (9003, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '16:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '16:00:00'), 120,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '16:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '17:00:00'), 'COMPLETED',
     '/demo/finish-1.svg', 480.00, 0, 0,
     @demo_admin_id, DATE_SUB(NOW(), INTERVAL 3 DAY),
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 今天已确认预约：用于“开始服务 -> 完成服务 -> 顾客评价”
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, status, total_amount, no_show_flag, no_show_exempt,
     approved_by, approved_at, created_at, updated_at)
VALUES
    (9004, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(@demo_today, '16:00:00'), TIMESTAMP(@demo_today, '16:00:00'), 120,
     'CONFIRMED', NULL, 0, 0, @demo_admin_id, NOW(), NOW(), NOW());

-- 明天待确认预约：用于美甲师审批
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, status, total_amount, no_show_flag, no_show_exempt,
     expire_at, created_at, updated_at)
VALUES
    (9005, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(@demo_tomorrow, '10:00:00'), NULL, 120,
     'PENDING', NULL, 0, 0, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW());

-- 明天改约中预约：旧时段 13:00-15:00，顾客可接受改到 15:00
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, status, total_amount, no_show_flag, no_show_exempt,
     approved_by, approved_at, created_at, updated_at)
VALUES
    (9006, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(@demo_tomorrow, '13:00:00'), TIMESTAMP(@demo_tomorrow, '13:00:00'), 120,
     'RESCHEDULING', NULL, 0, 0, @demo_admin_id, NOW(), NOW(), NOW());

-- 爽约 3 条：用于管理员撤销爽约和结算爽约扣款
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, status, total_amount, no_show_flag, no_show_exempt,
     created_at, updated_at)
VALUES
    (9007, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '18:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 2 DAY), '18:00:00'), 120,
     'NO_SHOW', NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (9008, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 1 DAY), '18:00:00'),
     TIMESTAMP(DATE_SUB(@demo_today, INTERVAL 1 DAY), '18:00:00'), 120,
     'NO_SHOW', NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (9009, @demo_customer_id, @demo_staff_id, @demo_service_id,
     TIMESTAMP(@demo_today, '15:00:00'),
     TIMESTAMP(@demo_today, '15:00:00'), 120,
     'NO_SHOW', NULL, 1, 0, NOW(), NOW());

-- 工单：已完成、今天已确认、明天改约中
INSERT IGNORE INTO appointment_prep
    (id, appointment_id, style_description, effect_image_url, material_json,
     staff_note, prep_status, created_at, updated_at)
VALUES
    (9001, 9001, '奶咖渐变 + 细闪跳色', '/demo/effect-1.svg',
     '{"色号":"奶咖 N03 / 香槟金","饰品":"细闪","工具":"圆头笔"}',
     '顾客喜欢低饱和，甲面修短一点。', 'READY',
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (9002, 9002, '裸粉法式 + 珍珠点缀', '/demo/effect-2.svg',
     '{"色号":"裸粉 F01","饰品":"小珍珠","工具":"拉线笔"}',
     '保留原生甲长度，珍珠只做两指。', 'READY',
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (9003, 9003, '深红猫眼', '/demo/effect-1.svg',
     '{"色号":"酒红 C02","饰品":"猫眼磁铁","工具":"磁铁笔"}',
     '上次顾客觉得颜色偏亮，这次加深。', 'READY',
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (9004, 9004, '今日演示款：裸粉细闪', '/demo/effect-1.svg',
     '{"色号":"裸粉 F01","饰品":"细闪","工具":"圆头笔"}',
     '今天答辩演示用，完成后记得上传完工图。', 'READY',
     NOW(), NOW()),
    (9006, 9006, '明天改约款：奶咖渐变', '/demo/effect-2.svg',
     '{"色号":"奶咖 N03","饰品":"金箔","工具":"海绵拍"}',
     '改约后时段为 15:00-17:00。', 'READY',
     NOW(), NOW());

-- 评价：2 好评 + 1 差评，sentiment 与评分口径一致
INSERT IGNORE INTO review
    (id, appointment_id, customer_id, staff_id, rating, content, image_urls,
     sentiment, created_at)
VALUES
    (9001, 9001, @demo_customer_id, @demo_staff_id, 5,
     '颜色很温柔，渐变过渡自然，下次还来做。', '/demo/review-1.svg',
     'GOOD', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (9002, 9002, @demo_customer_id, @demo_staff_id, 4,
     '法式很干净，珍珠点缀也好看。', NULL,
     'GOOD', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (9003, 9003, @demo_customer_id, @demo_staff_id, 2,
     '这次颜色比预期深，沟通还可以再细一点。', '/demo/review-2.svg',
     'BAD', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 明天改约提案：美甲师发起，顾客可接受/拒绝
INSERT IGNORE INTO appointment_reschedule
    (id, appointment_id, proposer_id, proposed_start, status, expire_at,
     reason, created_at)
VALUES
    (9001, 9006, @demo_staff_user_id, TIMESTAMP(@demo_tomorrow, '15:00:00'),
     'PENDING', DATE_ADD(NOW(), INTERVAL 12 HOUR),
     '美甲师临时调整，希望改到下午 15:00', DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 薪酬规则：底薪 3500，提成 30%，好评 +50，差评 -100
INSERT IGNORE INTO staff_compensation_rule
    (id, staff_id, base_salary, commission_rate, good_review_bonus,
     bad_review_deduction, status, effective_from, created_at, updated_at)
VALUES
    (9001, @demo_staff_id, 3500.00, 0.3000, 50.00, 100.00, 'ACTIVE',
     DATE_FORMAT(CURDATE(), '%Y-%m-01'), NOW(), NOW());

-- ==========================================================================
-- 档期演示数据：上个月 + 本月整月排班、预约与时间槽
-- 以 CURDATE() 为基准相对生成，重复执行由主键与唯一键去重
-- ==========================================================================

SET @seed_cur_month = DATE_FORMAT(CURDATE(), '%Y-%m-01');
SET @seed_prev_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01');
SET @seed_admin_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);

-- 追加两位美甲师与三位顾客（演示账号密码同为 123456）
INSERT IGNORE INTO sys_user (id, username, password, real_name, status) VALUES
    (9010, 'staff2', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '美甲师小陈', 'ACTIVE'),
    (9011, 'staff3', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '美甲师小周', 'ACTIVE'),
    (9020, 'cust2', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '顾客小赵', 'ACTIVE'),
    (9021, 'cust3', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '顾客小许', 'ACTIVE'),
    (9022, 'cust4', '$2a$10$5CVEj8hdJ6J95e0wTfASXebydl4S44SjcnGe4SF9XLijRxED6qquC', '顾客小唐', 'ACTIVE');

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r
    ON r.role_code = CASE WHEN u.username IN ('staff2', 'staff3') THEN 'STAFF' ELSE 'CUSTOMER' END
WHERE u.username IN ('staff2', 'staff3', 'cust2', 'cust3', 'cust4');

INSERT IGNORE INTO staff_profile (user_id, staff_no, bio, hire_date, status)
SELECT id, 'S002', '擅长日式晕染与短甲护理', DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 'ACTIVE'
FROM sys_user WHERE username = 'staff2';
INSERT IGNORE INTO staff_profile (user_id, staff_no, bio, hire_date, status)
SELECT id, 'S003', '擅长延长甲与手绘款式', DATE_SUB(CURDATE(), INTERVAL 1 YEAR), 'ACTIVE'
FROM sys_user WHERE username = 'staff3';

INSERT IGNORE INTO staff_compensation_rule
    (staff_id, base_salary, commission_rate, good_review_bonus,
     bad_review_deduction, status, effective_from)
SELECT sp.id, 3200.00, 0.2800, 50.00, 100.00, 'ACTIVE', DATE_FORMAT(CURDATE(), '%Y-%m-01')
FROM staff_profile sp JOIN sys_user u ON u.id = sp.user_id WHERE u.username = 'staff2';
INSERT IGNORE INTO staff_compensation_rule
    (staff_id, base_salary, commission_rate, good_review_bonus,
     bad_review_deduction, status, effective_from)
SELECT sp.id, 3000.00, 0.2500, 50.00, 100.00, 'ACTIVE', DATE_FORMAT(CURDATE(), '%Y-%m-01')
FROM staff_profile sp JOIN sys_user u ON u.id = sp.user_id WHERE u.username = 'staff3';

-- 变量必须在新增用户与档案之后取，否则引用不到新美甲师
SET @st1 = (SELECT id FROM staff_profile WHERE user_id = (SELECT id FROM sys_user WHERE username = 'staff1') LIMIT 1);
SET @st2 = (SELECT id FROM staff_profile WHERE user_id = (SELECT id FROM sys_user WHERE username = 'staff2') LIMIT 1);
SET @st3 = (SELECT id FROM staff_profile WHERE user_id = (SELECT id FROM sys_user WHERE username = 'staff3') LIMIT 1);
SET @cu1 = (SELECT id FROM sys_user WHERE username = 'cust1' LIMIT 1);
SET @cu2 = (SELECT id FROM sys_user WHERE username = 'cust2' LIMIT 1);
SET @cu3 = (SELECT id FROM sys_user WHERE username = 'cust3' LIMIT 1);
SET @cu4 = (SELECT id FROM sys_user WHERE username = 'cust4' LIMIT 1);
SET @svc1 = (SELECT id FROM service_type ORDER BY id LIMIT 1);
SET @svc2 = (SELECT id FROM service_type ORDER BY id LIMIT 1 OFFSET 1);
SET @svc3 = (SELECT id FROM service_type ORDER BY id LIMIT 1 OFFSET 2);
SET @svc4 = (SELECT id FROM service_type ORDER BY id LIMIT 1 OFFSET 3);

-- 排班：每人固定作息，各安排一天调休
INSERT IGNORE INTO staff_schedule
    (id, staff_id, work_date, start_time, end_time, schedule_type, status, remark)
VALUES
    (520101, @st1, DATE_ADD(@seed_prev_month, INTERVAL 0 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520201, @st2, DATE_ADD(@seed_prev_month, INTERVAL 0 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520102, @st1, DATE_ADD(@seed_prev_month, INTERVAL 1 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520202, @st2, DATE_ADD(@seed_prev_month, INTERVAL 1 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520302, @st3, DATE_ADD(@seed_prev_month, INTERVAL 1 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520103, @st1, DATE_ADD(@seed_prev_month, INTERVAL 2 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520203, @st2, DATE_ADD(@seed_prev_month, INTERVAL 2 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520303, @st3, DATE_ADD(@seed_prev_month, INTERVAL 2 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520104, @st1, DATE_ADD(@seed_prev_month, INTERVAL 3 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520204, @st2, DATE_ADD(@seed_prev_month, INTERVAL 3 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520304, @st3, DATE_ADD(@seed_prev_month, INTERVAL 3 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520105, @st1, DATE_ADD(@seed_prev_month, INTERVAL 4 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520205, @st2, DATE_ADD(@seed_prev_month, INTERVAL 4 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520305, @st3, DATE_ADD(@seed_prev_month, INTERVAL 4 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520206, @st2, DATE_ADD(@seed_prev_month, INTERVAL 5 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520306, @st3, DATE_ADD(@seed_prev_month, INTERVAL 5 DAY), '11:00:00', '20:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (520107, @st1, DATE_ADD(@seed_prev_month, INTERVAL 6 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520108, @st1, DATE_ADD(@seed_prev_month, INTERVAL 7 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520208, @st2, DATE_ADD(@seed_prev_month, INTERVAL 7 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520109, @st1, DATE_ADD(@seed_prev_month, INTERVAL 8 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520209, @st2, DATE_ADD(@seed_prev_month, INTERVAL 8 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520309, @st3, DATE_ADD(@seed_prev_month, INTERVAL 8 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520110, @st1, DATE_ADD(@seed_prev_month, INTERVAL 9 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520210, @st2, DATE_ADD(@seed_prev_month, INTERVAL 9 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520310, @st3, DATE_ADD(@seed_prev_month, INTERVAL 9 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520111, @st1, DATE_ADD(@seed_prev_month, INTERVAL 10 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520211, @st2, DATE_ADD(@seed_prev_month, INTERVAL 10 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520311, @st3, DATE_ADD(@seed_prev_month, INTERVAL 10 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520112, @st1, DATE_ADD(@seed_prev_month, INTERVAL 11 DAY), '09:00:00', '18:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (520212, @st2, DATE_ADD(@seed_prev_month, INTERVAL 11 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520312, @st3, DATE_ADD(@seed_prev_month, INTERVAL 11 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520213, @st2, DATE_ADD(@seed_prev_month, INTERVAL 12 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520313, @st3, DATE_ADD(@seed_prev_month, INTERVAL 12 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520114, @st1, DATE_ADD(@seed_prev_month, INTERVAL 13 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520115, @st1, DATE_ADD(@seed_prev_month, INTERVAL 14 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520215, @st2, DATE_ADD(@seed_prev_month, INTERVAL 14 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520116, @st1, DATE_ADD(@seed_prev_month, INTERVAL 15 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520216, @st2, DATE_ADD(@seed_prev_month, INTERVAL 15 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520316, @st3, DATE_ADD(@seed_prev_month, INTERVAL 15 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520117, @st1, DATE_ADD(@seed_prev_month, INTERVAL 16 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520217, @st2, DATE_ADD(@seed_prev_month, INTERVAL 16 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520317, @st3, DATE_ADD(@seed_prev_month, INTERVAL 16 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520118, @st1, DATE_ADD(@seed_prev_month, INTERVAL 17 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520218, @st2, DATE_ADD(@seed_prev_month, INTERVAL 17 DAY), '10:00:00', '19:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (520318, @st3, DATE_ADD(@seed_prev_month, INTERVAL 17 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520119, @st1, DATE_ADD(@seed_prev_month, INTERVAL 18 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520219, @st2, DATE_ADD(@seed_prev_month, INTERVAL 18 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520319, @st3, DATE_ADD(@seed_prev_month, INTERVAL 18 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520220, @st2, DATE_ADD(@seed_prev_month, INTERVAL 19 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520320, @st3, DATE_ADD(@seed_prev_month, INTERVAL 19 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520121, @st1, DATE_ADD(@seed_prev_month, INTERVAL 20 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520122, @st1, DATE_ADD(@seed_prev_month, INTERVAL 21 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520222, @st2, DATE_ADD(@seed_prev_month, INTERVAL 21 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520123, @st1, DATE_ADD(@seed_prev_month, INTERVAL 22 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520223, @st2, DATE_ADD(@seed_prev_month, INTERVAL 22 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520323, @st3, DATE_ADD(@seed_prev_month, INTERVAL 22 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520124, @st1, DATE_ADD(@seed_prev_month, INTERVAL 23 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520224, @st2, DATE_ADD(@seed_prev_month, INTERVAL 23 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520324, @st3, DATE_ADD(@seed_prev_month, INTERVAL 23 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520125, @st1, DATE_ADD(@seed_prev_month, INTERVAL 24 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520225, @st2, DATE_ADD(@seed_prev_month, INTERVAL 24 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520325, @st3, DATE_ADD(@seed_prev_month, INTERVAL 24 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520126, @st1, DATE_ADD(@seed_prev_month, INTERVAL 25 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520226, @st2, DATE_ADD(@seed_prev_month, INTERVAL 25 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520326, @st3, DATE_ADD(@seed_prev_month, INTERVAL 25 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520227, @st2, DATE_ADD(@seed_prev_month, INTERVAL 26 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520327, @st3, DATE_ADD(@seed_prev_month, INTERVAL 26 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (520128, @st1, DATE_ADD(@seed_prev_month, INTERVAL 27 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520129, @st1, DATE_ADD(@seed_prev_month, INTERVAL 28 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520229, @st2, DATE_ADD(@seed_prev_month, INTERVAL 28 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520130, @st1, DATE_ADD(@seed_prev_month, INTERVAL 29 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (520230, @st2, DATE_ADD(@seed_prev_month, INTERVAL 29 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (520330, @st3, DATE_ADD(@seed_prev_month, INTERVAL 29 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510101, @st1, DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510201, @st2, DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510301, @st3, DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510102, @st1, DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510202, @st2, DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510302, @st3, DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510103, @st1, DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510203, @st2, DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510303, @st3, DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510204, @st2, DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510304, @st3, DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510105, @st1, DATE_ADD(@seed_cur_month, INTERVAL 4 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510106, @st1, DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510206, @st2, DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510306, @st3, DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '11:00:00', '20:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (510107, @st1, DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510207, @st2, DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510307, @st3, DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510108, @st1, DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510208, @st2, DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510308, @st3, DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510109, @st1, DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510209, @st2, DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510309, @st3, DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510110, @st1, DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510210, @st2, DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510310, @st3, DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510211, @st2, DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510311, @st3, DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510112, @st1, DATE_ADD(@seed_cur_month, INTERVAL 11 DAY), '09:00:00', '18:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (510113, @st1, DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510213, @st2, DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510114, @st1, DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510214, @st2, DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510314, @st3, DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510115, @st1, DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510215, @st2, DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510315, @st3, DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510116, @st1, DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510216, @st2, DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510316, @st3, DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510117, @st1, DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510217, @st2, DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510317, @st3, DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510218, @st2, DATE_ADD(@seed_cur_month, INTERVAL 17 DAY), '10:00:00', '19:00:00', 'LEAVE', 'ACTIVE', '调休'),
    (510318, @st3, DATE_ADD(@seed_cur_month, INTERVAL 17 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510119, @st1, DATE_ADD(@seed_cur_month, INTERVAL 18 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510120, @st1, DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510220, @st2, DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510121, @st1, DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510221, @st2, DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510321, @st3, DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510122, @st1, DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510222, @st2, DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510322, @st3, DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510223, @st2, DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510323, @st3, DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510224, @st2, DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510324, @st3, DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510225, @st2, DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510325, @st3, DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510126, @st1, DATE_ADD(@seed_cur_month, INTERVAL 25 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510127, @st1, DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510227, @st2, DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510128, @st1, DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510228, @st2, DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510328, @st3, DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510129, @st1, DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510229, @st2, DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510329, @st3, DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510130, @st1, DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510230, @st2, DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510330, @st3, DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示'),
    (510131, @st1, DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '09:00:00', '18:00:00', 'NORMAL', 'ACTIVE', '美甲师小王 档期演示'),
    (510231, @st2, DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '10:00:00', '19:00:00', 'NORMAL', 'ACTIVE', '美甲师小陈 档期演示'),
    (510331, @st3, DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '11:00:00', '20:00:00', 'NORMAL', 'ACTIVE', '美甲师小周 档期演示');

-- 预约：历史月份记为已完成，本月未到期记为已确认，未来混入待确认
INSERT IGNORE INTO appointment
    (id, customer_id, staff_id, service_type_id, requested_start, confirmed_start,
     est_duration_minutes, actual_start, actual_end, status, total_amount,
     no_show_flag, no_show_exempt, approved_by, approved_at, expire_at, created_at, updated_at)
VALUES
    (900110, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '10:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900111, @cu3, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '12:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900120, @cu4, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '12:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '12:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900121, @cu3, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '15:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '15:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900130, @cu4, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '19:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '19:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900131, @cu4, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 0 DAY), '16:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900210, @cu1, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '09:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '09:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900220, @cu1, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '14:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900221, @cu4, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900230, @cu1, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '16:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900231, @cu3, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '18:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 1 DAY), '18:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900310, @cu4, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900320, @cu2, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '11:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900330, @cu3, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '11:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900331, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '15:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 2 DAY), '15:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900420, @cu4, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '11:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '11:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900430, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '14:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900431, @cu4, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '17:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 3 DAY), '17:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900510, @cu3, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 4 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 4 DAY), '14:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900511, @cu2, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 4 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 4 DAY), '10:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900610, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '13:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '13:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900611, @cu4, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '11:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900620, @cu4, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 5 DAY), '17:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900710, @cu3, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '14:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900711, @cu4, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '11:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900720, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '12:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '12:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900730, @cu4, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '17:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900731, @cu4, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 6 DAY), '14:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900810, @cu1, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '13:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900811, @cu2, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900820, @cu1, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '16:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '16:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900821, @cu3, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900830, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '16:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 7 DAY), '16:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900910, @cu3, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '14:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900911, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '15:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '15:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900920, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '16:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '16:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900921, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '13:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900930, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '11:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (900931, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '18:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 8 DAY), '18:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901010, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '17:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901020, @cu3, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '14:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901021, @cu4, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '11:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901030, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '13:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 9 DAY), '13:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901120, @cu4, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '17:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901121, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '13:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901130, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '17:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '17:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901131, @cu2, @st3, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 10 DAY), '11:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901310, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '09:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '09:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901320, @cu3, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '14:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901321, @cu4, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 12 DAY), '10:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901410, @cu1, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '09:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '09:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901420, @cu1, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '10:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901430, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '11:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901431, @cu2, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 13 DAY), '14:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901510, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '17:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901520, @cu4, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '13:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901530, @cu2, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '13:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901531, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 14 DAY), '12:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901610, @cu3, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '16:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901611, @cu4, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901620, @cu3, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '14:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901630, @cu4, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '14:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901631, @cu2, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 15 DAY), '11:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901710, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '13:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901720, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '12:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901730, @cu2, @st3, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '13:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901731, @cu4, @st3, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '17:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 16 DAY), '17:30:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901830, @cu4, @st3, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 17 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 17 DAY), '17:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (901910, @cu2, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 18 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 18 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902010, @cu1, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '11:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '11:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902020, @cu4, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '11:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 19 DAY), '11:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902110, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '16:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '16:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902120, @cu3, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902130, @cu4, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 20 DAY), '13:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902210, @cu4, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '16:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902220, @cu4, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '12:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902221, @cu2, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '10:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902230, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '12:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902231, @cu2, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '15:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 21 DAY), '15:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902310, @cu2, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '15:00:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902311, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '11:30:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902320, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '11:30:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902321, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '15:30:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902330, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '18:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '18:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902331, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '12:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 22 DAY), '12:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902410, @cu4, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '13:30:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902411, @cu4, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '10:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902412, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '16:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '16:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902420, @cu2, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '14:30:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902421, @cu2, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '11:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902430, @cu3, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '16:30:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902431, @cu3, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '12:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '12:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902432, @cu3, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '15:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 23 DAY), '15:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902520, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '14:00:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902521, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '12:00:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902530, @cu3, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '18:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902531, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '11:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 24 DAY), '11:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902610, @cu1, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 25 DAY), '13:00:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902611, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 25 DAY), '15:30:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902612, @cu4, @st1, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 25 DAY), '09:30:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902710, @cu2, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '14:30:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902711, @cu2, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '10:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902712, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '11:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '11:00:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902720, @cu4, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '17:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902721, @cu2, @st2, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 26 DAY), '11:30:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902810, @cu2, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '16:00:00'), 120, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902811, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '12:30:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902820, @cu3, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '14:30:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902821, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '11:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '11:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902830, @cu3, @st3, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '17:30:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902831, @cu4, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '14:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '14:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902832, @cu4, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 27 DAY), '15:00:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902910, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '15:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902911, @cu3, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '14:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902912, @cu4, @st1, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '10:00:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902920, @cu2, @st2, @svc3, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '13:00:00'), NULL, 120, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (902921, @cu4, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '15:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '15:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902930, @cu2, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '15:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '15:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (902931, @cu4, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '12:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 28 DAY), '12:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903010, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '10:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '10:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903011, @cu4, @st1, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '15:00:00'), NULL, 90, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903020, @cu2, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '10:30:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903021, @cu3, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '16:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '16:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903022, @cu2, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '14:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '14:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903030, @cu2, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '17:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '17:00:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903031, @cu4, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 29 DAY), '11:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903110, @cu2, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '10:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '10:30:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903111, @cu4, @st1, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '11:30:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903120, @cu3, @st2, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '10:00:00'), NULL, 180, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903121, @cu2, @st2, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '17:00:00'), NULL, 60, NULL, NULL, 'PENDING', NULL, 0, 0, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
    (903130, @cu3, @st3, @svc2, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '18:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '18:30:00'), 90, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903131, @cu3, @st3, @svc4, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '15:30:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '15:30:00'), 180, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW()),
    (903132, @cu2, @st3, @svc1, TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '13:00:00'), TIMESTAMP(DATE_ADD(@seed_cur_month, INTERVAL 30 DAY), '13:00:00'), 60, NULL, NULL, 'CONFIRMED', NULL, 0, 0, @seed_admin_id, NOW(), NULL, NOW(), NOW());

-- 今天的款式工单：供「今日物料」演示
INSERT IGNORE INTO appointment_prep
    (id, appointment_id, style_description, effect_image_url, material_json,
     staff_note, prep_status, created_at, updated_at)
VALUES
    (810000, 902330, '裸粉细闪 + 金箔点缀', '/demo/effect-1.svg', '{"色号":"裸粉 F01","饰品":"金箔","工具":"拉线笔"}', '甲面修短，金箔只做两指。', 'READY', NOW(), NOW()),
    (810001, 902331, '奶咖渐变 + 香槟跳色', '/demo/effect-2.svg', '{"色号":"奶咖 N03 / 香槟金","饰品":"细闪","工具":"海绵拍"}', '低饱和，过渡要柔。', 'READY', NOW(), NOW());

-- 时间槽：由排班按 15 分钟展开，已确认/服务中/改约中的预约对应槽位标记为已占用
INSERT IGNORE INTO time_slot (staff_id, slot_start, slot_end, status)
SELECT ss.staff_id,
       DATE_ADD(TIMESTAMP(ss.work_date, ss.start_time), INTERVAL n.i * 15 MINUTE),
       DATE_ADD(TIMESTAMP(ss.work_date, ss.start_time), INTERVAL (n.i + 1) * 15 MINUTE),
       CASE WHEN EXISTS (
           SELECT 1 FROM appointment a
           WHERE a.staff_id = ss.staff_id AND a.status IN ('CONFIRMED', 'IN_SERVICE', 'RESCHEDULING')
             AND a.confirmed_start < DATE_ADD(TIMESTAMP(ss.work_date, ss.start_time), INTERVAL (n.i + 1) * 15 MINUTE)
             AND DATE_ADD(a.confirmed_start, INTERVAL COALESCE(a.est_duration_minutes, 60) MINUTE) > DATE_ADD(TIMESTAMP(ss.work_date, ss.start_time), INTERVAL n.i * 15 MINUTE)
       ) THEN 'BOOKED' ELSE 'AVAILABLE' END
FROM staff_schedule ss
JOIN (
    SELECT 0 AS i
    UNION ALL     SELECT 1 AS i
    UNION ALL     SELECT 2 AS i
    UNION ALL     SELECT 3 AS i
    UNION ALL     SELECT 4 AS i
    UNION ALL     SELECT 5 AS i
    UNION ALL     SELECT 6 AS i
    UNION ALL     SELECT 7 AS i
    UNION ALL     SELECT 8 AS i
    UNION ALL     SELECT 9 AS i
    UNION ALL     SELECT 10 AS i
    UNION ALL     SELECT 11 AS i
    UNION ALL     SELECT 12 AS i
    UNION ALL     SELECT 13 AS i
    UNION ALL     SELECT 14 AS i
    UNION ALL     SELECT 15 AS i
    UNION ALL     SELECT 16 AS i
    UNION ALL     SELECT 17 AS i
    UNION ALL     SELECT 18 AS i
    UNION ALL     SELECT 19 AS i
    UNION ALL     SELECT 20 AS i
    UNION ALL     SELECT 21 AS i
    UNION ALL     SELECT 22 AS i
    UNION ALL     SELECT 23 AS i
    UNION ALL     SELECT 24 AS i
    UNION ALL     SELECT 25 AS i
    UNION ALL     SELECT 26 AS i
    UNION ALL     SELECT 27 AS i
    UNION ALL     SELECT 28 AS i
    UNION ALL     SELECT 29 AS i
    UNION ALL     SELECT 30 AS i
    UNION ALL     SELECT 31 AS i
    UNION ALL     SELECT 32 AS i
    UNION ALL     SELECT 33 AS i
    UNION ALL     SELECT 34 AS i
    UNION ALL     SELECT 35 AS i
    UNION ALL     SELECT 36 AS i
    UNION ALL     SELECT 37 AS i
    UNION ALL     SELECT 38 AS i
    UNION ALL     SELECT 39 AS i
    UNION ALL     SELECT 40 AS i
    UNION ALL     SELECT 41 AS i
    UNION ALL     SELECT 42 AS i
    UNION ALL     SELECT 43 AS i
    UNION ALL     SELECT 44 AS i
    UNION ALL     SELECT 45 AS i
    UNION ALL     SELECT 46 AS i
    UNION ALL     SELECT 47 AS i
) n
WHERE ss.schedule_type = 'NORMAL'
  AND ss.work_date >= @seed_cur_month
  AND DATE_ADD(TIMESTAMP(ss.work_date, ss.start_time), INTERVAL (n.i + 1) * 15 MINUTE)
      <= TIMESTAMP(ss.work_date, ss.end_time);
