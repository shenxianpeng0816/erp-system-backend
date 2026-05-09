-- Kenya ETR (Electronic Tax Register) invoice details on sales orders
USE erp_db;

ALTER TABLE `sales_order`
    ADD COLUMN `etr_required`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = customer ETR details required' AFTER `remark`,
    ADD COLUMN `etr_company_name`   VARCHAR(200) DEFAULT NULL COMMENT 'company name for ETR' AFTER `etr_required`,
    ADD COLUMN `etr_company_kra_pin` VARCHAR(50) DEFAULT NULL COMMENT 'company KRA PIN for ETR' AFTER `etr_company_name`;
