-- Enforce unique product name per country (country_code + name).
-- Safe to run if migration_product_inventory_country_code.sql already applied with uk_product_no_country.

-- Drop previous product_no+country unique key if present
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product'
      AND index_name = 'uk_product_no_country'
);
SET @sql := IF(@idx_exists > 0,
    'ALTER TABLE `product` DROP INDEX `uk_product_no_country`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add country+name unique key if missing
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product'
      AND index_name = 'uk_product_country_name'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `product` ADD UNIQUE KEY `uk_product_country_name` (`country_code`, `name`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Keep product_no searchable (non-unique)
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product'
      AND index_name = 'idx_product_no'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `product` ADD KEY `idx_product_no` (`product_no`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
