-- Multi-warehouse: warehouse master, per-warehouse inventory, stock transfers
USE erp_db;

-- ============================================================
-- 1. Warehouse master
-- ============================================================
CREATE TABLE IF NOT EXISTS `warehouse` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `warehouse_code` VARCHAR(30)  NOT NULL COMMENT 'e.g. WH-KE-NBO',
    `name`           VARCHAR(200) NOT NULL,
    `country_code`   VARCHAR(2)   NOT NULL COMMENT 'ISO 3166-1 alpha-2: KE UG TZ',
    `city`           VARCHAR(100) DEFAULT NULL,
    `type`           ENUM('MAIN','BRANCH') NOT NULL DEFAULT 'MAIN',
    `address`        VARCHAR(500) DEFAULT NULL,
    `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=default ship-from warehouse for country',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_code` (`warehouse_code`),
    KEY `idx_country_code` (`country_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Warehouses';

INSERT INTO `warehouse` (`warehouse_code`, `name`, `country_code`, `city`, `type`, `is_default`, `status`)
VALUES
    ('WH-KE-NBO', 'Nairobi Main Warehouse', 'KE', 'Nairobi', 'MAIN', 1, 1),
    ('WH-KE-KSM', 'Kisumu Warehouse', 'KE', 'Kisumu', 'BRANCH', 0, 1),
    ('WH-UG-MAIN', 'Kampala Warehouse', 'UG', 'Kampala', 'MAIN', 1, 1),
    ('WH-TZ-MAIN', 'Dar es Salaam Warehouse', 'TZ', 'Dar es Salaam', 'MAIN', 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ============================================================
-- 2. Inventory → per warehouse
-- ============================================================
ALTER TABLE `inventory`
    ADD COLUMN `warehouse_id` BIGINT DEFAULT NULL COMMENT 'warehouse.id' AFTER `id`;

-- Assign legacy global stock to Nairobi main warehouse (Kenya default)
UPDATE `inventory` i
SET i.warehouse_id = (SELECT w.id FROM `warehouse` w WHERE w.warehouse_code = 'WH-KE-NBO' LIMIT 1)
WHERE i.warehouse_id IS NULL;

ALTER TABLE `inventory`
    DROP INDEX `uk_product`;

ALTER TABLE `inventory`
    MODIFY COLUMN `warehouse_id` BIGINT NOT NULL,
    ADD UNIQUE KEY `uk_warehouse_product` (`warehouse_id`, `product_id`),
    ADD KEY `idx_warehouse_id` (`warehouse_id`),
    ADD CONSTRAINT `fk_inventory_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`);

-- ============================================================
-- 3. Inventory log
-- ============================================================
ALTER TABLE `inventory_log`
    ADD COLUMN `warehouse_id` BIGINT DEFAULT NULL AFTER `product_id`;

UPDATE `inventory_log` l
SET l.warehouse_id = (SELECT w.id FROM `warehouse` w WHERE w.warehouse_code = 'WH-KE-NBO' LIMIT 1)
WHERE l.warehouse_id IS NULL;

ALTER TABLE `inventory_log`
    MODIFY COLUMN `warehouse_id` BIGINT NOT NULL,
    ADD KEY `idx_log_warehouse` (`warehouse_id`),
    ADD CONSTRAINT `fk_log_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`);

ALTER TABLE `inventory_log`
    MODIFY COLUMN `type` ENUM('INBOUND','OUTBOUND','ADJUST','TRANSFER_OUT','TRANSFER_IN') NOT NULL;

-- ============================================================
-- 4. Sales order / inbound / outbound
-- ============================================================
ALTER TABLE `sales_order`
    ADD COLUMN `ship_from_warehouse_id` BIGINT DEFAULT NULL COMMENT 'fulfillment warehouse' AFTER `country_code`,
    ADD KEY `idx_ship_from_warehouse` (`ship_from_warehouse_id`),
    ADD CONSTRAINT `fk_order_ship_from_wh` FOREIGN KEY (`ship_from_warehouse_id`) REFERENCES `warehouse` (`id`);

UPDATE `sales_order` o
SET o.ship_from_warehouse_id = (
    SELECT w.id FROM `warehouse` w
    WHERE w.country_code = o.country_code AND w.is_default = 1
    ORDER BY w.id LIMIT 1
)
WHERE o.ship_from_warehouse_id IS NULL;

ALTER TABLE `inbound_order`
    ADD COLUMN `warehouse_id` BIGINT DEFAULT NULL AFTER `inbound_no`,
    ADD KEY `idx_inbound_warehouse` (`warehouse_id`),
    ADD CONSTRAINT `fk_inbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`);

UPDATE `inbound_order` io
SET io.warehouse_id = (SELECT w.id FROM `warehouse` w WHERE w.warehouse_code = 'WH-KE-NBO' LIMIT 1)
WHERE io.warehouse_id IS NULL;

ALTER TABLE `outbound_order`
    ADD COLUMN `warehouse_id` BIGINT DEFAULT NULL AFTER `order_id`,
    ADD KEY `idx_outbound_warehouse` (`warehouse_id`),
    ADD CONSTRAINT `fk_outbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`id`);

UPDATE `outbound_order` oo
SET oo.warehouse_id = COALESCE(
    (SELECT o.ship_from_warehouse_id FROM `sales_order` o WHERE o.id = oo.order_id),
    (SELECT w.id FROM `warehouse` w WHERE w.warehouse_code = 'WH-KE-NBO' LIMIT 1)
)
WHERE oo.warehouse_id IS NULL;

-- ============================================================
-- 5. Stock transfer (inter-warehouse)
-- ============================================================
CREATE TABLE IF NOT EXISTS `stock_transfer` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `transfer_no`        VARCHAR(30)  NOT NULL COMMENT 'e.g. ST202504250001',
    `from_warehouse_id`  BIGINT       NOT NULL,
    `to_warehouse_id`    BIGINT       NOT NULL,
    `operator_id`        BIGINT       NOT NULL,
    `status`             ENUM('DRAFT','CONFIRMED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    `remark`             TEXT         DEFAULT NULL,
    `confirmed_at`       DATETIME     DEFAULT NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_no` (`transfer_no`),
    KEY `idx_from_wh` (`from_warehouse_id`),
    KEY `idx_to_wh` (`to_warehouse_id`),
    CONSTRAINT `fk_transfer_from_wh` FOREIGN KEY (`from_warehouse_id`) REFERENCES `warehouse` (`id`),
    CONSTRAINT `fk_transfer_to_wh`   FOREIGN KEY (`to_warehouse_id`)   REFERENCES `warehouse` (`id`),
    CONSTRAINT `fk_transfer_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inter-warehouse stock transfers';

CREATE TABLE IF NOT EXISTS `stock_transfer_item` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `transfer_id`  BIGINT NOT NULL,
    `product_id`   BIGINT NOT NULL,
    `qty`          INT    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_st_item_transfer` FOREIGN KEY (`transfer_id`) REFERENCES `stock_transfer` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_st_item_product`  FOREIGN KEY (`product_id`)  REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stock transfer line items';

INSERT IGNORE INTO `doc_sequence` (`seq_name`, `next_val`, `date_part`) VALUES ('ST', 1, '');
