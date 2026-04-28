-- ============================================================
-- ERP System - Full Database Schema
-- Database: MySQL 8.0+
-- Charset: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS erp_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE erp_db;

-- ============================================================
-- 1. Users (multi-role support)
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(50) NOT NULL COMMENT 'login username',
    `password`      VARCHAR(255) NOT NULL COMMENT 'bcrypt hashed',
    `real_name`     VARCHAR(100) NOT NULL COMMENT 'display name',
    `phone`         VARCHAR(30)  DEFAULT NULL,
    `email`         VARCHAR(100) DEFAULT NULL,
    `role`          ENUM('ADMIN','SALES','FINANCE','WAREHOUSE','INBOUND') NOT NULL DEFAULT 'SALES',
    `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System Users';

-- Default admin (password: Admin@123)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`) VALUES
('admin', '$2a$10$YQbF/4pynk0A7EjRXgZZUOQX5U4UOs7qmhyTpn0IHIFgdXtX9H8yK', 'System Admin', 'ADMIN');

-- ============================================================
-- 2. Customers
-- ============================================================
CREATE TABLE IF NOT EXISTS `customer` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `customer_no`     VARCHAR(20) NOT NULL COMMENT 'unique customer code e.g. C0001',
    `name`            VARCHAR(200) NOT NULL,
    `type`            ENUM('REGULAR','PICKUP_POINT','DISTRIBUTOR') NOT NULL DEFAULT 'REGULAR',
    `phone`           VARCHAR(30)  DEFAULT NULL,
    `email`           VARCHAR(100) DEFAULT NULL,
    `address`         VARCHAR(500) DEFAULT NULL,
    `contact_person`  VARCHAR(100) DEFAULT NULL,
    `is_pickup_point` TINYINT     NOT NULL DEFAULT 0 COMMENT '1=this customer is also a pickup point',
    `credit_limit`    DECIMAL(15,2) DEFAULT 0.00,
    `remark`          TEXT         DEFAULT NULL,
    `status`          TINYINT     NOT NULL DEFAULT 1,
    `created_by`      BIGINT       DEFAULT NULL,
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_no` (`customer_no`),
    KEY `idx_name` (`name`),
    CONSTRAINT `fk_customer_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Customers';

-- ============================================================
-- 3. Pickup Points (some customers ARE pickup points)
-- ============================================================
CREATE TABLE IF NOT EXISTS `pickup_point` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `customer_id` BIGINT      NOT NULL COMMENT 'links to customer who operates this point',
    `location`    VARCHAR(300) DEFAULT NULL,
    `manager`     VARCHAR(100) DEFAULT NULL,
    `phone`       VARCHAR(30)  DEFAULT NULL,
    `status`      TINYINT     NOT NULL DEFAULT 1,
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_pickup_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Pickup Points';

-- ============================================================
-- 4. Products
-- ============================================================
CREATE TABLE IF NOT EXISTS `product` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `product_no`   VARCHAR(30)  NOT NULL COMMENT 'e.g. P0001',
    `name`         VARCHAR(200) NOT NULL,
    `spec`         VARCHAR(200) DEFAULT NULL COMMENT 'specification / model',
    `category`     VARCHAR(100) DEFAULT NULL,
    `unit`         VARCHAR(20)  NOT NULL DEFAULT 'pcs',
    `unit_price`   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    `image_url`    VARCHAR(500) DEFAULT NULL,
    `remark`       TEXT         DEFAULT NULL,
    `status`       TINYINT     NOT NULL DEFAULT 1,
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Products';

-- ============================================================
-- 5. Inventory
-- ============================================================
CREATE TABLE IF NOT EXISTS `inventory` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `product_id`   BIGINT       NOT NULL,
    `qty`          INT          NOT NULL DEFAULT 0,
    `min_qty`      INT          NOT NULL DEFAULT 0 COMMENT 'alert threshold',
    `last_updated` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product` (`product_id`),
    CONSTRAINT `fk_inventory_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory';

-- ============================================================
-- 6. Sales Orders (main order table)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sales_order` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `order_no`            VARCHAR(30)  NOT NULL COMMENT 'e.g. SO20240001',
    `sales_user_id`       BIGINT       NOT NULL COMMENT 'salesperson who created order',
    -- KEY: ship_to and bill_to can differ (pickup-point scenario)
    `ship_to_customer_id` BIGINT       NOT NULL COMMENT 'goods delivered to this customer',
    `bill_to_customer_id` BIGINT       NOT NULL COMMENT 'invoice / payment from this customer',
    `status`              ENUM('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','SHIPPED','CONFIRMED','CANCELLED')
                          NOT NULL DEFAULT 'DRAFT',
    `total_amount`        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    `payment_method`      VARCHAR(50)  DEFAULT NULL COMMENT 'Bank Transfer / Mpesa etc.',
    `price_term`          VARCHAR(50)  DEFAULT NULL COMMENT 'e.g. DDP Kenya',
    `validity_days`       INT          DEFAULT 30,
    `remark`              TEXT         DEFAULT NULL,
    `reject_reason`       TEXT         DEFAULT NULL,
    `sign_image_url`      VARCHAR(500) DEFAULT NULL COMMENT 'customer signed delivery confirmation',
    `confirmed_by`        BIGINT       DEFAULT NULL COMMENT 'sales user who confirmed delivery',
    `confirmed_at`        DATETIME     DEFAULT NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    CONSTRAINT `fk_order_sales_user`      FOREIGN KEY (`sales_user_id`)       REFERENCES `user` (`id`),
    CONSTRAINT `fk_order_ship_to`         FOREIGN KEY (`ship_to_customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_order_bill_to`         FOREIGN KEY (`bill_to_customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_order_confirmed_by`    FOREIGN KEY (`confirmed_by`)        REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sales Orders';

-- ============================================================
-- 7. Sales Order Items
-- ============================================================
CREATE TABLE IF NOT EXISTS `sales_order_item` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`    BIGINT       NOT NULL,
    `product_id`  BIGINT       NOT NULL,
    `qty`         INT          NOT NULL,
    `unit_price`  DECIMAL(15,2) NOT NULL,
    `total`       DECIMAL(15,2) NOT NULL,
    `remark`      VARCHAR(300) DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_item_order`   FOREIGN KEY (`order_id`)   REFERENCES `sales_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sales Order Items';

-- ============================================================
-- 8. Approval Flow (supports redirect approval)
-- ============================================================
CREATE TABLE IF NOT EXISTS `approval_flow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`    BIGINT       NOT NULL,
    `step`        INT          NOT NULL DEFAULT 1,
    `approver_id` BIGINT       NOT NULL,
    `status`      ENUM('PENDING','APPROVED','REJECTED','REDIRECTED') NOT NULL DEFAULT 'PENDING',
    `redirect_to` BIGINT       DEFAULT NULL COMMENT 'redirect approval to another user',
    `comment`     TEXT         DEFAULT NULL,
    `acted_at`    DATETIME     DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_approval_order`       FOREIGN KEY (`order_id`)    REFERENCES `sales_order` (`id`),
    CONSTRAINT `fk_approval_approver`    FOREIGN KEY (`approver_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_approval_redirect_to` FOREIGN KEY (`redirect_to`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Order Approval Flow';

-- ============================================================
-- 9. Invoices
-- ============================================================
CREATE TABLE IF NOT EXISTS `invoice` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `invoice_no`         VARCHAR(30)  NOT NULL COMMENT 'e.g. INV20240001',
    `order_id`           BIGINT       NOT NULL,
    `bill_to_customer_id` BIGINT      NOT NULL,
    `amount`             DECIMAL(15,2) NOT NULL,
    `status`             ENUM('PENDING','PARTIAL','PAID','CANCELLED') NOT NULL DEFAULT 'PENDING',
    `issue_date`         DATE         NOT NULL,
    `due_date`           DATE         DEFAULT NULL,
    `payment_method`     VARCHAR(50)  DEFAULT NULL,
    `remark`             TEXT         DEFAULT NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invoice_no` (`invoice_no`),
    CONSTRAINT `fk_invoice_order`    FOREIGN KEY (`order_id`)            REFERENCES `sales_order` (`id`),
    CONSTRAINT `fk_invoice_customer` FOREIGN KEY (`bill_to_customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Invoices';

-- ============================================================
-- 10. Accounts Receivable
-- ============================================================
CREATE TABLE IF NOT EXISTS `receivable` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `invoice_id`      BIGINT       NOT NULL,
    `customer_id`     BIGINT       NOT NULL,
    `amount`          DECIMAL(15,2) NOT NULL,
    `received_amount` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    `balance`         DECIMAL(15,2) NOT NULL,
    `due_date`        DATE         DEFAULT NULL,
    `status`          ENUM('OUTSTANDING','PARTIAL','SETTLED','OVERDUE') NOT NULL DEFAULT 'OUTSTANDING',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_recv_invoice`  FOREIGN KEY (`invoice_id`)  REFERENCES `invoice` (`id`),
    CONSTRAINT `fk_recv_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Accounts Receivable';

-- ============================================================
-- 11. Payment Records (收款记录)
-- ============================================================
CREATE TABLE IF NOT EXISTS `payment_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `receivable_id`  BIGINT       NOT NULL,
    `amount`         DECIMAL(15,2) NOT NULL,
    `payment_method` VARCHAR(50)  DEFAULT NULL COMMENT 'Bank Transfer / Mpesa',
    `mpesa_ref`      VARCHAR(100) DEFAULT NULL,
    `paid_at`        DATE         NOT NULL,
    `remark`         TEXT         DEFAULT NULL,
    `created_by`     BIGINT       NOT NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_payment_recv`       FOREIGN KEY (`receivable_id`) REFERENCES `receivable` (`id`),
    CONSTRAINT `fk_payment_created_by` FOREIGN KEY (`created_by`)    REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payment Records';

-- ============================================================
-- 12. Outbound Orders (Delivery Notes)
-- ============================================================
CREATE TABLE IF NOT EXISTS `outbound_order` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `outbound_no`         VARCHAR(30)  NOT NULL COMMENT 'e.g. DN20240001',
    `order_id`            BIGINT       NOT NULL,
    `ship_to_customer_id` BIGINT       NOT NULL,
    `operator_id`         BIGINT       DEFAULT NULL COMMENT 'warehouse staff who executes',
    `status`              ENUM('PENDING','PRINTED','SHIPPED','CONFIRMED','CANCELLED')
                          NOT NULL DEFAULT 'PENDING',
    `shipped_at`          DATETIME     DEFAULT NULL,
    `remark`              TEXT         DEFAULT NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbound_no` (`outbound_no`),
    CONSTRAINT `fk_outbound_order`    FOREIGN KEY (`order_id`)            REFERENCES `sales_order` (`id`),
    CONSTRAINT `fk_outbound_ship_to`  FOREIGN KEY (`ship_to_customer_id`) REFERENCES `customer` (`id`),
    CONSTRAINT `fk_outbound_operator` FOREIGN KEY (`operator_id`)         REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbound Orders (Delivery Notes)';

-- ============================================================
-- 13. Outbound Items
-- ============================================================
CREATE TABLE IF NOT EXISTS `outbound_item` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT,
    `outbound_id`  BIGINT NOT NULL,
    `product_id`   BIGINT NOT NULL,
    `qty`          INT    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_out_item_outbound` FOREIGN KEY (`outbound_id`) REFERENCES `outbound_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_out_item_product`  FOREIGN KEY (`product_id`)  REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbound Order Items';

-- ============================================================
-- 14. Inbound Orders (Stock In)
-- ============================================================
CREATE TABLE IF NOT EXISTS `inbound_order` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `inbound_no`  VARCHAR(30)  NOT NULL COMMENT 'e.g. IN20240001',
    `supplier`    VARCHAR(200) DEFAULT NULL,
    `operator_id` BIGINT       NOT NULL COMMENT 'inbound staff',
    `status`      ENUM('DRAFT','CONFIRMED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    `remark`      TEXT         DEFAULT NULL,
    `inbound_at`  DATETIME     DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbound_no` (`inbound_no`),
    CONSTRAINT `fk_inbound_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inbound Orders';

-- ============================================================
-- 15. Inbound Items
-- ============================================================
CREATE TABLE IF NOT EXISTS `inbound_item` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `inbound_id`  BIGINT        NOT NULL,
    `product_id`  BIGINT        NOT NULL,
    `qty`         INT           NOT NULL,
    `unit_cost`   DECIMAL(15,2) DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_in_item_inbound`  FOREIGN KEY (`inbound_id`) REFERENCES `inbound_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_in_item_product`  FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inbound Order Items';

-- ============================================================
-- 17. Document Sequence (restart-safe number generation)
-- ============================================================
CREATE TABLE IF NOT EXISTS `doc_sequence` (
    `seq_name`    VARCHAR(30)  NOT NULL COMMENT 'prefix key: SO, INV, DN, IN',
    `next_val`    BIGINT       NOT NULL DEFAULT 1 COMMENT 'next value to allocate',
    `date_part`   VARCHAR(8)   NOT NULL DEFAULT '' COMMENT 'current yyyyMMdd prefix; resets counter on change',
    PRIMARY KEY (`seq_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-prefix document number sequences';

INSERT IGNORE INTO `doc_sequence` (`seq_name`, `next_val`, `date_part`) VALUES
('SO',  1, ''),
('INV', 1, ''),
('DN',  1, ''),
('IN',  1, '');

-- ============================================================
-- 18. Inventory Transaction Log (audit trail)
-- ============================================================
CREATE TABLE IF NOT EXISTS `inventory_log` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `product_id`   BIGINT        NOT NULL,
    `type`         ENUM('INBOUND','OUTBOUND','ADJUST') NOT NULL,
    `qty_change`   INT           NOT NULL COMMENT 'positive=in, negative=out',
    `qty_after`    INT           NOT NULL,
    `ref_id`       BIGINT        DEFAULT NULL COMMENT 'inbound_order.id or outbound_order.id',
    `ref_type`     VARCHAR(20)   DEFAULT NULL COMMENT 'INBOUND or OUTBOUND',
    `operator_id`  BIGINT        NOT NULL,
    `remark`       VARCHAR(300)  DEFAULT NULL,
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_log_product`  FOREIGN KEY (`product_id`)  REFERENCES `product` (`id`),
    CONSTRAINT `fk_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory Transaction Log';
