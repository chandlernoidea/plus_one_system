-- 预约制美甲管理系统 - 阶段 0 数据库结构
-- 说明：
-- 1. 本脚本可重复执行，所有建表语句均使用 IF NOT EXISTS。
-- 2. MVP 阶段不建立外键约束，关联一致性由应用层保证。
-- 3. 所有表使用 InnoDB + utf8mb4，时间字段使用 DATE/TIME/DATETIME。

CREATE DATABASE IF NOT EXISTS nail_demo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

ALTER DATABASE nail_demo
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nail_demo;

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT '密码（阶段 0 为明文，生产环境应使用 BCrypt）',
    real_name   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    avatar_url  VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code  VARCHAR(50) NOT NULL COMMENT '角色编码',
    role_name  VARCHAR(50) NOT NULL COMMENT '角色名称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统角色';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id  BIGINT NOT NULL COMMENT '用户 ID',
    role_id  BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role_user_role (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联';

CREATE TABLE IF NOT EXISTS staff_profile (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '对应用户 ID',
    staff_no    VARCHAR(50)  DEFAULT NULL COMMENT '员工编号',
    bio         VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    avatar_url  VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    hire_date   DATE         DEFAULT NULL COMMENT '入职日期',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_profile_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '美甲师档案';

CREATE TABLE IF NOT EXISTS service_type (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name              VARCHAR(100) NOT NULL COMMENT '服务名称',
    est_duration_min  INT          NOT NULL COMMENT '预计最短时长（分钟）',
    est_duration_max  INT          NOT NULL COMMENT '预计最长时长（分钟）',
    buffer_minutes    INT          NOT NULL DEFAULT 0 COMMENT '服务后缓冲时间（分钟）',
    description       VARCHAR(500) DEFAULT NULL COMMENT '服务说明',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_type_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务类型';

CREATE TABLE IF NOT EXISTS staff_schedule (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_id       BIGINT       NOT NULL COMMENT '美甲师 ID',
    work_date      DATE         NOT NULL COMMENT '排班日期',
    start_time     TIME         NOT NULL COMMENT '开始时间',
    end_time       TIME         NOT NULL COMMENT '结束时间',
    schedule_type  VARCHAR(20)  DEFAULT NULL COMMENT '排班类型',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_staff_schedule_staff_work_date (staff_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '美甲师排班';

CREATE TABLE IF NOT EXISTS appointment (
    id                    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id           BIGINT         NOT NULL COMMENT '顾客用户 ID',
    staff_id              BIGINT         NOT NULL COMMENT '美甲师 ID',
    service_type_id       BIGINT         NOT NULL COMMENT '服务类型 ID',
    requested_start       DATETIME       NOT NULL COMMENT '顾客期望开始时间',
    confirmed_start       DATETIME       DEFAULT NULL COMMENT '商家确认开始时间',
    est_duration_minutes  INT            DEFAULT NULL COMMENT '预计服务时长（分钟）',
    actual_start          DATETIME       DEFAULT NULL COMMENT '实际开始时间',
    actual_end            DATETIME       DEFAULT NULL COMMENT '实际结束时间',
    status                VARCHAR(30)    NOT NULL DEFAULT 'PENDING' COMMENT '预约状态',
    reject_reason         VARCHAR(255)   DEFAULT NULL COMMENT '拒绝原因',
    expire_at             DATETIME       DEFAULT NULL COMMENT '确认截止时间',
    approved_by           BIGINT         DEFAULT NULL COMMENT '审批人用户 ID',
    approved_at           DATETIME       DEFAULT NULL COMMENT '审批时间',
    finish_image_urls     TEXT           NULL COMMENT '完成效果图地址，多个用逗号分隔',
    total_amount          DECIMAL(10, 2) DEFAULT NULL COMMENT '总金额',
    no_show_flag          TINYINT        NOT NULL DEFAULT 0 COMMENT '是否爽约：0 否，1 是',
    no_show_exempt        TINYINT        NOT NULL DEFAULT 0 COMMENT '爽约是否豁免：0 否，1 是',
    created_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_appointment_staff_confirmed_start (staff_id, confirmed_start),
    KEY idx_appointment_customer_id (customer_id),
    KEY idx_appointment_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约单';

CREATE TABLE IF NOT EXISTS time_slot (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_id    BIGINT      NOT NULL COMMENT '美甲师 ID',
    slot_start  DATETIME    NOT NULL COMMENT '时间段开始',
    slot_end    DATETIME    NOT NULL COMMENT '时间段结束',
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '时间段状态',
    PRIMARY KEY (id),
    UNIQUE KEY uk_time_slot_staff_start (staff_id, slot_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '可预约时间段';

CREATE TABLE IF NOT EXISTS appointment_prep (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    appointment_id     BIGINT        NOT NULL COMMENT '预约 ID',
    style_description  VARCHAR(1000) DEFAULT NULL COMMENT '款式说明',
    effect_image_url   VARCHAR(500)  DEFAULT NULL COMMENT '效果图地址',
    material_json      TEXT          NULL COMMENT '物料清单 JSON 字符串',
    staff_note         VARCHAR(500)  DEFAULT NULL COMMENT '美甲师备注',
    prep_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '工单状态',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_prep_appointment (appointment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '款式物料工单';

CREATE TABLE IF NOT EXISTS appointment_reschedule (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    appointment_id BIGINT       NOT NULL COMMENT '预约 ID',
    proposer_id    BIGINT       NOT NULL COMMENT '提案人用户 ID',
    proposed_start DATETIME     NOT NULL COMMENT '新开始时间',
    status         VARCHAR(20)  NOT NULL COMMENT '提案状态',
    expire_at      DATETIME     NOT NULL COMMENT '提案过期时间',
    responded_at   DATETIME     DEFAULT NULL COMMENT '响应时间',
    reason         VARCHAR(500) DEFAULT NULL COMMENT '改约原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_reschedule_appointment_id (appointment_id),
    KEY idx_reschedule_status_expire (status, expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '改约提案';

CREATE TABLE IF NOT EXISTS review (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    appointment_id BIGINT        NOT NULL COMMENT '预约 ID',
    customer_id    BIGINT        NOT NULL COMMENT '顾客用户 ID',
    staff_id       BIGINT        NOT NULL COMMENT '美甲师 ID',
    rating         INT           NOT NULL COMMENT '评分 1-5',
    content        VARCHAR(1000) NOT NULL COMMENT '评价内容',
    image_urls     TEXT          NULL COMMENT '评价图片地址，多个用逗号分隔',
    sentiment      VARCHAR(20)   NOT NULL DEFAULT 'NEUTRAL' COMMENT '评价倾向：GOOD/BAD/NEUTRAL',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_appointment (appointment_id),
    KEY idx_review_customer (customer_id),
    KEY idx_review_staff (staff_id),
    KEY idx_review_sentiment (sentiment)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务评价';

CREATE TABLE IF NOT EXISTS appointment_status_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    appointment_id BIGINT       NOT NULL COMMENT '预约 ID',
    action         VARCHAR(50)  NOT NULL COMMENT '操作类型',
    operator_id    BIGINT       NOT NULL COMMENT '操作人用户 ID',
    old_exempt     TINYINT      DEFAULT NULL COMMENT '变更前爽约豁免值',
    new_exempt     TINYINT      DEFAULT NULL COMMENT '变更后爽约豁免值',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_status_log_appointment (appointment_id),
    KEY idx_status_log_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约状态操作日志';

CREATE TABLE IF NOT EXISTS staff_compensation_rule (
    id                     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_id               BIGINT        NOT NULL COMMENT '美甲师 ID',
    base_salary            DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '底薪',
    commission_rate        DECIMAL(5,4)  NOT NULL DEFAULT 0 COMMENT '固定提成比例 0-1',
    good_review_bonus      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '每条好评奖励',
    bad_review_deduction   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '每条差评扣减',
    status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '规则状态',
    effective_from         DATE          DEFAULT NULL COMMENT '生效日期',
    created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_compensation_rule_staff (staff_id),
    KEY idx_compensation_rule_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '美甲师薪酬规则';

CREATE TABLE IF NOT EXISTS monthly_settlement (
    id                              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_id                        BIGINT        NOT NULL COMMENT '美甲师 ID',
    settle_month                    CHAR(7)       NOT NULL COMMENT '结算月份 YYYY-MM',
    base_salary                     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '底薪快照',
    completed_count                 INT           NOT NULL DEFAULT 0 COMMENT '完成单数',
    total_revenue                   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '总营收',
    commission_amount               DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '提成金额',
    good_review_count               INT           NOT NULL DEFAULT 0 COMMENT '好评数',
    good_review_bonus_amount        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '好评奖励合计',
    bad_review_count                INT           NOT NULL DEFAULT 0 COMMENT '差评数',
    bad_review_deduction_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '差评扣减合计',
    review_adjustment               DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '评价调整净额',
    no_show_count                   INT           NOT NULL DEFAULT 0 COMMENT '爽约次数',
    no_show_deduction               DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '爽约扣款',
    final_payable                   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '最终工资',
    final_payable_before_floor      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '保底前应得工资',
    status                          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '结算状态',
    settled_at                      DATETIME      DEFAULT NULL COMMENT '确认结算时间',
    created_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_staff_month (staff_id, settle_month),
    KEY idx_settlement_month (settle_month),
    KEY idx_settlement_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '月度结算快照';

-- ============================================================
-- 阶段 6 存量数据库兼容迁移
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，这里通过
-- information_schema 判断后用 PREPARE 动态执行，保证脚本可重复执行。
-- ============================================================

SET @appointment_no_show_exempt_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'appointment'
      AND COLUMN_NAME = 'no_show_exempt'
);
SET @appointment_no_show_exempt_ddl = IF(
    @appointment_no_show_exempt_exists = 0,
    'ALTER TABLE appointment ADD COLUMN no_show_exempt TINYINT NOT NULL DEFAULT 0 COMMENT ''爽约是否豁免：0 否，1 是''',
    'SELECT 1'
);
PREPARE appointment_no_show_exempt_stmt FROM @appointment_no_show_exempt_ddl;
EXECUTE appointment_no_show_exempt_stmt;
DEALLOCATE PREPARE appointment_no_show_exempt_stmt;

-- Hibernate 可能把存量列调整为可空；这里统一回填并恢复非空默认值。
UPDATE appointment
SET no_show_exempt = 0
WHERE no_show_exempt IS NULL;

ALTER TABLE appointment
    MODIFY COLUMN no_show_exempt TINYINT NOT NULL DEFAULT 0 COMMENT '爽约是否豁免：0 否，1 是';

SET @review_sentiment_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'review'
      AND COLUMN_NAME = 'sentiment'
);
SET @review_sentiment_ddl = IF(
    @review_sentiment_exists = 0,
    'ALTER TABLE review ADD COLUMN sentiment VARCHAR(20) NOT NULL DEFAULT ''NEUTRAL'' COMMENT ''评价倾向：GOOD/BAD/NEUTRAL''',
    'SELECT 1'
);
PREPARE review_sentiment_stmt FROM @review_sentiment_ddl;
EXECUTE review_sentiment_stmt;
DEALLOCATE PREPARE review_sentiment_stmt;

-- 阶段 5 已有评价没有 sentiment，按统一口径回填：
-- 4-5 星 GOOD，1-2 星 BAD，3 星 NEUTRAL。
UPDATE review
SET sentiment = CASE
    WHEN rating >= 4 THEN 'GOOD'
    WHEN rating <= 2 THEN 'BAD'
    ELSE 'NEUTRAL'
END;
