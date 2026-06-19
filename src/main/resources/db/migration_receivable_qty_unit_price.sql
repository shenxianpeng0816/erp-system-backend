-- Receivable: redundant qty + unit_price from sales order line
ALTER TABLE `receivable`
    ADD COLUMN `qty` INT DEFAULT NULL COMMENT 'sales_order_item.qty' AFTER `product_name`,
    ADD COLUMN `unit_price` DECIMAL(15,2) DEFAULT NULL COMMENT 'sales_order_item.unit_price' AFTER `qty`;

UPDATE `receivable` r
    INNER JOIN `sales_order_item` soi ON soi.id = r.order_item_id
SET r.qty = soi.qty,
    r.unit_price = soi.unit_price
WHERE r.order_item_id IS NOT NULL;
