-- Receivable: track received product quantity
ALTER TABLE `receivable`
    ADD COLUMN `received_qty` INT NOT NULL DEFAULT 0 COMMENT 'cumulative paid product qty' AFTER `received_amount`;

UPDATE `receivable`
SET `received_qty` = CASE
    WHEN `unit_price` IS NOT NULL AND `unit_price` > 0 AND `received_amount` > 0
        THEN FLOOR(`received_amount` / `unit_price`)
    ELSE 0
END
WHERE `qty` IS NOT NULL;

-- Payment record: qty per payment + datetime paid_at
ALTER TABLE `payment_record`
    ADD COLUMN `qty` INT DEFAULT NULL COMMENT 'paid product qty' AFTER `amount`;

ALTER TABLE `payment_record`
    MODIFY COLUMN `paid_at` DATETIME NOT NULL;

UPDATE `payment_record` pr
    INNER JOIN `receivable` r ON r.id = pr.receivable_id
SET pr.qty = CASE
    WHEN r.unit_price IS NOT NULL AND r.unit_price > 0
        THEN FLOOR(pr.amount / r.unit_price)
    ELSE NULL
END
WHERE pr.qty IS NULL;
