-- Run on existing erp_db when upgrading from schema without extended customer fields.
USE erp_db;

ALTER TABLE `customer`
    ADD COLUMN `country_code` VARCHAR(2) DEFAULT NULL COMMENT 'ISO 3166-1 alpha-2 e.g. KE UG TZ' AFTER `remark`,
    ADD COLUMN `first_transaction_at` DATETIME DEFAULT NULL COMMENT 'first deal time' AFTER `country_code`,
    ADD COLUMN `shop_name` VARCHAR(200) DEFAULT NULL COMMENT 'customer shop name' AFTER `first_transaction_at`,
    ADD COLUMN `invoice_required` TINYINT NOT NULL DEFAULT 0 COMMENT '1=invoice needed' AFTER `shop_name`,
    ADD COLUMN `photo_url` VARCHAR(500) DEFAULT NULL COMMENT 'customer photo URL path' AFTER `invoice_required`;
