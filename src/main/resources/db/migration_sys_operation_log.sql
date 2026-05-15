-- System operation audit log (mutating API calls only)
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT        NOT NULL,
    `user_name`       VARCHAR(100)  NOT NULL COMMENT 'realName snapshot',
    `user_role`       VARCHAR(30)   NOT NULL,
    `operation_name`  VARCHAR(200)  NOT NULL COMMENT 'e.g. 新增 /api/inbound',
    `operation_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `client_ip`       VARCHAR(64)   DEFAULT NULL,
    `request_uri`     VARCHAR(500)  NOT NULL,
    `request_params`  TEXT          COMMENT 'query + JSON body, truncated',
    PRIMARY KEY (`id`),
    KEY `idx_op_log_time` (`operation_at`),
    KEY `idx_op_log_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System operation log';
