-- Receivable: redundant order + per-line product fields
ALTER TABLE `receivable`
    ADD COLUMN `order_id` BIGINT DEFAULT NULL COMMENT 'sales_order.id' AFTER `invoice_id`,
    ADD COLUMN `order_no` VARCHAR(30) DEFAULT NULL COMMENT 'sales_order.order_no' AFTER `order_id`,
    ADD COLUMN `order_item_id` BIGINT DEFAULT NULL COMMENT 'sales_order_item.id' AFTER `order_no`,
    ADD COLUMN `product_id` BIGINT DEFAULT NULL AFTER `order_item_id`,
    ADD COLUMN `product_name` VARCHAR(200) DEFAULT NULL AFTER `product_id`,
    ADD INDEX `idx_order_no` (`order_no`),
    ADD INDEX `idx_order_id` (`order_id`),
    ADD INDEX `idx_product_name` (`product_name`);

-- Backfill order_no / order_id from invoice → sales_order
UPDATE `receivable` r
    INNER JOIN `invoice` i ON i.id = r.invoice_id
    INNER JOIN `sales_order` o ON o.id = i.order_id
SET r.order_id = o.id,
    r.order_no = o.order_no
WHERE r.order_id IS NULL OR r.order_no IS NULL;

-- Single-line orders: attach product from the only order item
UPDATE `receivable` r
    INNER JOIN `invoice` i ON i.id = r.invoice_id
    INNER JOIN `sales_order_item` soi ON soi.order_id = i.order_id
    INNER JOIN `product` p ON p.id = soi.product_id
SET r.order_item_id = soi.id,
    r.product_id = soi.product_id,
    r.product_name = p.name
WHERE r.product_id IS NULL
  AND (SELECT COUNT(*) FROM `sales_order_item` x WHERE x.order_id = i.order_id) = 1;

-- Multi-line orders with zero payments: split one legacy receivable into per-line receivables.
-- Skip rows that already have product_id or have received payments.
INSERT INTO `receivable` (
    `invoice_id`, `order_id`, `order_no`, `order_item_id`, `product_id`, `product_name`,
    `customer_id`, `amount`, `received_amount`, `balance`, `due_date`, `status`,
    `created_at`, `updated_at`
)
SELECT
    r.invoice_id,
    r.order_id,
    r.order_no,
    soi.id,
    soi.product_id,
    p.name,
    r.customer_id,
    soi.total,
    0,
    soi.total,
    r.due_date,
    'OUTSTANDING',
    r.created_at,
    NOW()
FROM `receivable` r
    INNER JOIN `invoice` i ON i.id = r.invoice_id
    INNER JOIN `sales_order_item` soi ON soi.order_id = i.order_id
    INNER JOIN `product` p ON p.id = soi.product_id
WHERE r.product_id IS NULL
  AND r.received_amount = 0
  AND r.status NOT IN ('CANCELLED', 'SETTLED')
  AND (SELECT COUNT(*) FROM `sales_order_item` x WHERE x.order_id = i.order_id) > 1
  AND NOT EXISTS (
      SELECT 1 FROM `receivable` r2
      WHERE r2.invoice_id = r.invoice_id AND r2.order_item_id = soi.id
  );

-- Remove legacy aggregate rows after per-line rows were inserted (MySQL cannot reference
-- the target table in a subquery on DELETE, so use a derived table join instead).
DELETE r FROM `receivable` r
    INNER JOIN `invoice` i ON i.id = r.invoice_id
    INNER JOIN (
        SELECT DISTINCT invoice_id
        FROM `receivable`
        WHERE order_item_id IS NOT NULL
    ) split ON split.invoice_id = r.invoice_id
WHERE r.product_id IS NULL
  AND r.received_amount = 0
  AND r.status NOT IN ('CANCELLED', 'SETTLED')
  AND (SELECT COUNT(*) FROM `sales_order_item` x WHERE x.order_id = i.order_id) > 1;
