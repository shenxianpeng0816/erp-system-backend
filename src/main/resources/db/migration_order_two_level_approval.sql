-- ============================================================
-- Order two-level approval: finance first → admin final
-- Additive; safe to re-run (ON DUPLICATE / UPDATE idempotent where noted)
-- ============================================================

-- 1) Expand sales_order.status ENUM and migrate legacy pending
ALTER TABLE `sales_order`
  MODIFY COLUMN `status` ENUM(
    'DRAFT',
    'PENDING_APPROVAL',
    'PENDING_FINANCE_APPROVAL',
    'PENDING_ADMIN_APPROVAL',
    'APPROVED',
    'REJECTED',
    'SHIPPED',
    'CONFIRMED',
    'CANCELLED'
  ) NOT NULL DEFAULT 'DRAFT';

-- Existing single-step pending → admin final (avoid re-running finance on in-flight orders)
UPDATE `sales_order`
SET `status` = 'PENDING_ADMIN_APPROVAL'
WHERE `status` = 'PENDING_APPROVAL';

-- 2) Split approve permission: 1023 → admin final; 1036 → finance first
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(1036, 'First Approve Order', 102, 12, '', 'F', '0', '0', 'erp:order:approve:finance', '#')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `perms` = VALUES(`perms`);

UPDATE `sys_menu`
SET `menu_name` = 'Final Approve Order',
    `perms` = 'erp:order:approve:admin'
WHERE `menu_id` = 1023;

-- ADMIN (1): final approve; first-approve optional (assign via Roles UI as needed)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 1023);
-- Do not auto-grant first-approve (1036) to ADMIN — assign flexibly via RBAC

-- Default seed: FINANCE gets first-approve; can be reassigned to any role in Roles UI
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 102),
(3, 1027),
(3, 1028),
(3, 1035),
(3, 1036);
