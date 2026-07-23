-- ============================================================
-- Approval history: permission-based first review + role snapshot
-- Prerequisite: migration_order_two_level_approval.sql
-- ============================================================

-- Pending steps may have no assignee; actor is written on approve/reject
ALTER TABLE `approval_flow` DROP FOREIGN KEY `fk_approval_approver`;
ALTER TABLE `approval_flow`
  MODIFY COLUMN `approver_id` BIGINT NULL COMMENT 'set when acted; null while PENDING unassigned';
ALTER TABLE `approval_flow`
  ADD CONSTRAINT `fk_approval_approver`
    FOREIGN KEY (`approver_id`) REFERENCES `user` (`id`);

-- Snapshot of system role(s) at action time (e.g. FINANCE or admin,sales)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'approval_flow'
    AND COLUMN_NAME = 'approver_role'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `approval_flow` ADD COLUMN `approver_role` VARCHAR(100) NULL COMMENT ''system role snapshot at action time'' AFTER `approver_id`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Clarify menu names: stage by permission, not fixed FINANCE role
UPDATE `sys_menu`
SET `menu_name` = 'First Approve Order (permission)'
WHERE `menu_id` = 1036;

UPDATE `sys_menu`
SET `menu_name` = 'Final Approve Order (permission)'
WHERE `menu_id` = 1023;
