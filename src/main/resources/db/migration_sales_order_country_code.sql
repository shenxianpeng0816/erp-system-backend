-- Country code on sales orders (may differ from ship-to customer country)
USE erp_db;

ALTER TABLE `sales_order`
    ADD COLUMN `country_code` VARCHAR(2) NOT NULL DEFAULT 'KE' COMMENT 'ISO 3166-1 alpha-2 e.g. KE UG TZ' AFTER `bill_to_customer_id`;
