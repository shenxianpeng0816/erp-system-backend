-- Product & inventory country codes (ISO 3166-1 alpha-2)
-- Product country drives unit-price currency; inventory country is denormalized from warehouse for filtering.

ALTER TABLE `product`
    ADD COLUMN `country_code` VARCHAR(2) NOT NULL DEFAULT 'KE'
        COMMENT 'ISO 3166-1 alpha-2; pricing currency from CountryEnum' AFTER `unit_price`;

ALTER TABLE `product`
    DROP INDEX `uk_product_no`,
    ADD UNIQUE KEY `uk_product_country_name` (`country_code`, `name`),
    ADD KEY `idx_product_country` (`country_code`),
    ADD KEY `idx_product_no` (`product_no`);

ALTER TABLE `inventory`
    ADD COLUMN `country_code` VARCHAR(2) DEFAULT NULL
        COMMENT 'denormalized from warehouse.country_code' AFTER `warehouse_id`;

UPDATE `inventory` i
    INNER JOIN `warehouse` w ON w.id = i.warehouse_id
SET i.country_code = w.country_code
WHERE i.country_code IS NULL OR i.country_code = '';

ALTER TABLE `inventory`
    MODIFY COLUMN `country_code` VARCHAR(2) NOT NULL,
    ADD KEY `idx_inventory_country` (`country_code`);
