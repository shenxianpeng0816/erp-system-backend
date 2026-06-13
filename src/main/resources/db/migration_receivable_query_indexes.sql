-- Receivables list/filter performance: sort, status, joins for name filters
ALTER TABLE `receivable`
    ADD INDEX `idx_created_at` (`created_at`),
    ADD INDEX `idx_status_created` (`status`, `created_at`),
    ADD INDEX `idx_customer_id` (`customer_id`),
    ADD INDEX `idx_invoice_id` (`invoice_id`);

ALTER TABLE `sales_order`
    ADD INDEX `idx_sales_user_id` (`sales_user_id`);

ALTER TABLE `invoice`
    ADD INDEX `idx_order_id` (`order_id`);

ALTER TABLE `user`
    ADD INDEX `idx_real_name` (`real_name`);
