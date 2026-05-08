-- Run on existing erp_db when upgrading inbound orders without document URL.
USE erp_db;

ALTER TABLE `inbound_order`
    ADD COLUMN `document_url` VARCHAR(500) DEFAULT NULL COMMENT 'delivery note / BOL image path' AFTER `remark`;
