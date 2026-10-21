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
