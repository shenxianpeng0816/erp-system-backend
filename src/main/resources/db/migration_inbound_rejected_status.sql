-- Add REJECTED to inbound_order.status for audit / refuse draft without stock-in.
-- Run on existing erp_db when upgrading.

ALTER TABLE `inbound_order`
MODIFY COLUMN `status` ENUM('DRAFT','CONFIRMED','CANCELLED','REJECTED') NOT NULL DEFAULT 'DRAFT';
