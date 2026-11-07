-- 单人美甲工作室版 —— 账号与角色表
-- 只保留登录鉴权需要的三张表；业务表全部在 schema_solo.sql。
-- 不再写死库名（CREATE DATABASE / USE 已移除）：由连接的 JDBC URL 决定目标库。
-- 脚本可重复执行：全部使用 IF NOT EXISTS。

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
