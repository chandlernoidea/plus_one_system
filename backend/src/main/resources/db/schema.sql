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
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_appointment (appointment_id),
    KEY idx_review_customer (customer_id),
    KEY idx_review_staff (staff_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务评价';
