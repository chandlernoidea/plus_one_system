-- 单人美甲工作室版 - P1 数据模型（增量脚本）
-- 说明：
-- 1. 本脚本只新增 solo_* 表，不改动现有 main 分支业务表（sys_* / appointment 等）。
-- 2. 可重复执行：所有建表语句均使用 IF NOT EXISTS，不包含破坏性 DDL。
-- 3. 与 schema.sql 保持一致：InnoDB + utf8mb4，不建外键（关联一致性由应用层保证）。
-- 4. 实体映射见 com.nail.demo.solo.entity，脚本字段需与实体逐列对齐。

CREATE DATABASE IF NOT EXISTS nail_demo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

ALTER DATABASE nail_demo
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nail_demo;

-- ------------------------------------------------------------
-- 客户档案：客户不是账号，标识 = 短号 + 昵称 + 手机后 4 位（选填）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_client (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code             VARCHAR(10)  NOT NULL COMMENT '客户短号，如 #C001，自动生成且永不变更',
    nickname         VARCHAR(50)  NOT NULL COMMENT '昵称',
    phone_tail       VARCHAR(4)   DEFAULT NULL COMMENT '手机号后 4 位（选填）',
    source           VARCHAR(30)  DEFAULT NULL COMMENT '来源：小红书 / 朋友介绍 / 路过 / 老客',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    color_notes      VARCHAR(200) DEFAULT NULL COMMENT '常用色号 / 偏好色，例：#E03 裸粉',
    first_visit_date DATE         DEFAULT NULL COMMENT '首次到访日期',
    last_visit_date  DATE         DEFAULT NULL COMMENT '最近到访日期',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已删除：0 否，1 是（软删除，历史预约保留）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_solo_client_code (code),
    KEY idx_solo_client_nickname (nickname),
    KEY idx_solo_client_phone_tail (phone_tail),
    KEY idx_solo_client_last_visit (last_visit_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户档案';

-- ------------------------------------------------------------
-- 款式库：兼作品集，可带标签筛选；材料清单存 JSON 快照
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_style (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(80)   NOT NULL COMMENT '款式名称',
    cover_url     VARCHAR(500)  DEFAULT NULL COMMENT '封面图地址',
    duration_min  INT           NOT NULL DEFAULT 60 COMMENT '标准时长（分钟）',
    price         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '标准价',
    material_json TEXT          NULL COMMENT '材料清单 JSON 字符串',
    tags          VARCHAR(200)  DEFAULT NULL COMMENT '标签，多个用英文逗号分隔',
    enabled       TINYINT       NOT NULL DEFAULT 1 COMMENT '是否启用：0 否，1 是',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_solo_style_enabled (enabled),
    KEY idx_solo_style_tags (tags)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '款式库';

-- ------------------------------------------------------------
-- 预约单：占用 = 各项目时长之和 + 缓冲；end_at 为冗余落库值，便于冲突 SQL 判断
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_booking (
    id                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    client_id             BIGINT      NOT NULL COMMENT '客户 ID',
    start_at              DATETIME    NOT NULL COMMENT '开始时间',
    planned_minutes       INT         NOT NULL COMMENT '预计时长（分钟，= 各项目时长之和）',
    buffer_minutes        INT         NOT NULL DEFAULT 20 COMMENT '缓冲（分钟），默认 20，可按单覆盖',
    end_at                DATETIME    NOT NULL COMMENT '结束时间（= start_at + planned_minutes + buffer_minutes）',
    status                VARCHAR(20) NOT NULL DEFAULT 'LEAD' COMMENT '状态：LEAD/WAIT_DEPOSIT/BOOKED/ARRIVED/IN_SERVICE/COMPLETED/RESCHEDULED/CANCELLED/NO_SHOW',
    source                VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '来源：NORMAL/WECHAT/XHS/WALKIN/JUMP',
    group_id              VARCHAR(32) DEFAULT NULL COMMENT '连单分组 ID，同一客户多项目或先后到店共用',
    free_reschedule_used  TINYINT     NOT NULL DEFAULT 0 COMMENT '免费改期是否已用：0 否，1 是',
    remark                VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_solo_booking_start (start_at),
    KEY idx_solo_booking_status_start (status, start_at),
    KEY idx_solo_booking_client_start (client_id, start_at),
    KEY idx_solo_booking_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约单';

-- ------------------------------------------------------------
-- 预约项目：一个预约下的多个项目（连做），名称与价格做快照，避免款式改价影响历史单
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_booking_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_id   BIGINT        NOT NULL COMMENT '预约 ID',
    style_id     BIGINT        DEFAULT NULL COMMENT '款式 ID（手写项目可为空）',
    style_name   VARCHAR(80)   NOT NULL COMMENT '款式名称快照',
    duration_min INT           NOT NULL COMMENT '该项目时长（分钟）',
    price        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该项目价格',
    addon        TINYINT       NOT NULL DEFAULT 0 COMMENT '是否加项：0 否，1 是',
    seq          INT           NOT NULL DEFAULT 0 COMMENT '项目顺序，从 0 开始',
    PRIMARY KEY (id),
    KEY idx_solo_booking_item_booking (booking_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约项目';

-- ------------------------------------------------------------
-- 营业安排：营业 / 休息 / 外出 / 机动位，按日期分段
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_slot_block (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    work_date  DATE         NOT NULL COMMENT '日期',
    start_time TIME         NOT NULL COMMENT '开始时间',
    end_time   TIME         NOT NULL COMMENT '结束时间',
    type       VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '类型：OPEN 营业 / REST 休息 / OUT 外出 / FLEX 机动位',
    remark     VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_slot_block_date (work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '营业安排';

-- ------------------------------------------------------------
-- 定金流水：收取 / 抵扣 / 退还 / 没收四条严格分开，不靠状态推断金额
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_deposit_flow (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_id  BIGINT        NOT NULL COMMENT '关联预约 ID',
    type        VARCHAR(20)   NOT NULL COMMENT '类型：COLLECT 收取 / OFFSET 抵扣 / REFUND 退还 / FORFEIT 没收',
    amount      DECIMAL(10,2) NOT NULL COMMENT '金额',
    happened_at DATETIME      NOT NULL COMMENT '发生时间',
    reason      VARCHAR(200)  DEFAULT NULL COMMENT '原因',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_deposit_flow_booking (booking_id, happened_at),
    KEY idx_solo_deposit_flow_type_time (type, happened_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '定金流水';

-- ------------------------------------------------------------
-- 收支流水：收入 / 支出，可回跳预约
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_money_flow (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    direction   VARCHAR(10)   NOT NULL COMMENT '方向：IN 收入 / OUT 支出',
    category    VARCHAR(20)   NOT NULL COMMENT '类别：SERVICE 服务收入 / MATERIAL 物料支出 / OTHER 其他',
    amount      DECIMAL(10,2) NOT NULL COMMENT '金额',
    happened_at DATETIME      NOT NULL COMMENT '发生时间',
    booking_id  BIGINT        DEFAULT NULL COMMENT '关联预约 ID（可空）',
    remark      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_money_flow_time (happened_at),
    KEY idx_solo_money_flow_direction_time (direction, happened_at),
    KEY idx_solo_money_flow_booking (booking_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收支流水';

-- ------------------------------------------------------------
-- 物料库存：成本、数量、赞助信息、采购状态
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_material_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(80)   NOT NULL COMMENT '物料名称',
    unit            VARCHAR(16)   NOT NULL DEFAULT '个' COMMENT '单位',
    unit_cost       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单位成本（赞助物料记 0）',
    quantity        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前库存数量',
    sponsored       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否赞助：0 否，1 是',
    sponsor_name    VARCHAR(80)   DEFAULT NULL COMMENT '赞助商',
    sponsor_value   DECIMAL(10,2) DEFAULT NULL COMMENT '赞助价值',
    purchase_status VARCHAR(20)   NOT NULL DEFAULT 'TODO' COMMENT '采购状态：TODO 待采购 / ORDERED 已下单 / SHIPPING 在途 / ARRIVED 已到货 / READY 已备好',
    remark          VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_solo_material_item_name (name),
    KEY idx_solo_material_item_status (purchase_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料库存';

-- ------------------------------------------------------------
-- 按单领用：写成本快照，库存扣减与账面成本解耦
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_material_usage (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_id    BIGINT        NOT NULL COMMENT '预约 ID',
    material_id   BIGINT        NOT NULL COMMENT '物料 ID',
    quantity      DECIMAL(10,2) NOT NULL COMMENT '领用数量',
    cost_snapshot DECIMAL(10,2) NOT NULL COMMENT '成本快照（赞助物料为 0）',
    remark        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_material_usage_booking (booking_id),
    KEY idx_solo_material_usage_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料领用';

-- ------------------------------------------------------------
-- 变更记录：改期 / 顺延 / 插队，含定金处理与是否已通知
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_booking_change (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_id     BIGINT       NOT NULL COMMENT '预约 ID',
    type           VARCHAR(20)  NOT NULL COMMENT '类型：RESCHEDULE 改期 / SHIFT 顺延 / JUMP 插队',
    old_start      DATETIME     DEFAULT NULL COMMENT '原开始时间（新建插单时为空）',
    new_start      DATETIME     NOT NULL COMMENT '新开始时间',
    initiator      VARCHAR(20)  NOT NULL COMMENT '发起方：CLIENT 客户 / OWNER 老板',
    hours_before   INT          NOT NULL DEFAULT 0 COMMENT '发起时距原开始的小时数',
    deposit_action VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT '定金处理：NONE 未处理 / FREE 免费 / REFUND 退还 / FORFEIT 没收',
    notified       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已通知客户：0 否，1 是',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_booking_change_booking (booking_id),
    KEY idx_solo_booking_change_notified (notified, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '改期/顺延/插队记录';

-- ------------------------------------------------------------
-- 状态事件：每次状态变化留痕（P2 规则 7 的前置依赖）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_booking_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_id  BIGINT       NOT NULL COMMENT '预约 ID',
    action      VARCHAR(30)  NOT NULL COMMENT '动作：CREATE/CONFIRM/ARRIVE/START/FINISH/RESCHEDULE/CANCEL/NO_SHOW',
    from_status VARCHAR(20)  DEFAULT NULL COMMENT '变更前状态',
    to_status   VARCHAR(20)  DEFAULT NULL COMMENT '变更后状态',
    operator    VARCHAR(50)  DEFAULT NULL COMMENT '操作者（单人版固定为老板）',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_solo_booking_event_booking (booking_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约状态事件';

-- ============================================================
-- P5 增量：客户档案补「常用色号」
-- 说明：MySQL 8 / MariaDB 都不支持 ADD COLUMN IF NOT EXISTS，
-- 这里用 information_schema 判断后动态执行，脚本可重复运行。
-- ============================================================

SET @solo_client_color_notes_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'solo_client'
      AND COLUMN_NAME = 'color_notes'
);
SET @solo_client_color_notes_ddl = IF(
    @solo_client_color_notes_exists = 0,
    'ALTER TABLE solo_client ADD COLUMN color_notes VARCHAR(200) DEFAULT NULL COMMENT ''常用色号 / 偏好色，例：#E03 裸粉'' AFTER remark',
    'SELECT 1'
);
PREPARE solo_client_color_notes_stmt FROM @solo_client_color_notes_ddl;
EXECUTE solo_client_color_notes_stmt;
DEALLOCATE PREPARE solo_client_color_notes_stmt;

-- ------------------------------------------------------------
-- P7 设置：营业模板 / 缓冲 / 定金 / 免费改期阈值，键值对存
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solo_setting (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    setting_key   VARCHAR(50)  NOT NULL COMMENT '键：open_time / close_time / buffer_minutes / deposit_amount / free_reschedule_hours',
    setting_value VARCHAR(200) NOT NULL COMMENT '值',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_solo_setting_key (setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '单人版设置';

-- ------------------------------------------------------------
-- P9 账号设置：改密码后让旧 token 立刻失效（token_version 递增即吊销）
-- 同样用 information_schema 判断，脚本可重复运行。
-- ------------------------------------------------------------
SET @sys_user_token_version_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'token_version'
);
SET @sys_user_token_version_ddl = IF(
    @sys_user_token_version_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT ''令牌版本：改密码时 +1，旧 token 立即失效''',
    'SELECT 1'
);
PREPARE sys_user_token_version_stmt FROM @sys_user_token_version_ddl;
EXECUTE sys_user_token_version_stmt;
DEALLOCATE PREPARE sys_user_token_version_stmt;
