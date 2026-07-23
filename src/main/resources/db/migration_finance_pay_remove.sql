-- ============================================================
-- RBAC: delete payment record permission
-- ============================================================
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(1056, 'Delete Payment', 105, 6, '', 'F', '0', '0', 'erp:finance:pay:remove', '#')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `perms` = VALUES(`perms`);

-- ADMIN
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 1056);
-- FINANCE (same as pay / pay:edit)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (3, 1056);
