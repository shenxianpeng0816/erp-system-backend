-- ============================================================
-- Order two-level approval: first → final (permission-based)
-- Additive; for fresh installs prefer schema.sql + this file
-- ============================================================

ALTER TABLE `sales_order`
  MODIFY COLUMN `status` ENUM(
    'DRAFT',
    'PENDING_APPROVAL',
    'PENDING_FINANCE_APPROVAL',
    'PENDING_ADMIN_APPROVAL',
    'PENDING_FIRST_APPROVAL',
    'PENDING_FINAL_APPROVAL',
    'APPROVED',
    'REJECTED',
    'SHIPPED',
    'CONFIRMED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'DRAFT';

-- Migrate any legacy / intermediate names
UPDATE `sales_order` SET `status` = 'PENDING_FIRST_APPROVAL'
WHERE `status` IN ('PENDING_FINANCE_APPROVAL');
UPDATE `sales_order` SET `status` = 'PENDING_FINAL_APPROVAL'
WHERE `status` IN ('PENDING_ADMIN_APPROVAL', 'PENDING_APPROVAL');

ALTER TABLE `sales_order`
  MODIFY COLUMN `status` ENUM(
    'DRAFT',
    'PENDING_FIRST_APPROVAL',
    'PENDING_FINAL_APPROVAL',
    'APPROVED',
    'REJECTED',
    'SHIPPED',
    'CONFIRMED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'DRAFT';

-- First / final approve permissions
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(1036, 'First Approve Order', 102, 12, '', 'F', '0', '0', 'erp:order:approve:first', '#')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `perms` = VALUES(`perms`);

UPDATE `sys_menu`
SET `menu_name` = 'Final Approve Order',
    `perms` = 'erp:order:approve:final'
WHERE `menu_id` = 1023;

-- ADMIN (1): final approve by default; first-approve assign via Roles UI
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 1023);

-- Default seed: FINANCE role gets first-approve (+ order list/pending/query)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 102),
(3, 1027),
(3, 1028),
(3, 1035),
(3, 1036);
