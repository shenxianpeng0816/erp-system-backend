-- Rename mpesa_ref -> transaction_ref; enforce uniqueness per payment_method + transaction_ref.
-- If you previously ran migration_payment_mpesa_ref_unique.sql, drop the old index first.

-- ALTER TABLE `payment_record` DROP INDEX `uk_mpesa_ref`;

UPDATE `payment_record`
SET `mpesa_ref` = UPPER(TRIM(`mpesa_ref`))
WHERE `mpesa_ref` IS NOT NULL AND TRIM(`mpesa_ref`) <> '';

ALTER TABLE `payment_record`
    CHANGE COLUMN `mpesa_ref` `transaction_ref` VARCHAR(100) DEFAULT NULL
        COMMENT 'Transaction reference (not required for Cash)';

ALTER TABLE `payment_record`
    ADD UNIQUE KEY `uk_payment_method_transaction_ref` (`payment_method`, `transaction_ref`);
