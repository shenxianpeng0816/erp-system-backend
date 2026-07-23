-- RBAC v5: single-permission endpoints — grant dedicated perms that replace hasAnyPermi workarounds
-- Run manually after deploy.

-- Warehouse list (dropdown / operational read)
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`)
VALUES
(1102, 'Warehouse List', 106, 12, '', 'F', '0', '0', 'erp:warehouse:list', '#');

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1102),
(2, 1102),
(3, 1102),
(4, 1102),
(5, 1102);

-- Product picker permission for order/inbound forms (F-button; does not force Inventory sidebar)
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`)
VALUES
(1060, 'Product List Query', 106, 0, '', 'F', '0', '0', 'erp:inventory:list', '#');

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1060),
(2, 1060),
(3, 1060),
(4, 1060),
(5, 1060);

-- Customer list query F-button (reinforce erp:customer:list for search/options/detail APIs)
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`)
VALUES
(1074, 'Customer List Query', 107, 0, '', 'F', '0', '0', 'erp:customer:list', '#');

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1074),
(2, 1074),
(3, 1074);

-- inbound:query for detail API (query-only)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1043),
(4, 1043),
(5, 1043);

-- order:query for detail/print API (query-only)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1035),
(2, 1035),
(3, 1035),
(4, 1035),
(5, 1035);
