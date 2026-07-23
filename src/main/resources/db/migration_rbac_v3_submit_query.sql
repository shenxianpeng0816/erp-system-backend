-- RBAC v3: order submit / query button permissions (additive, does not wipe role_menu)
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(1030, 'Submit Order', 102, 10, '', 'F', '0', '0', 'erp:order:submit', '#'),
(1035, 'View Order Detail', 102, 11, '', 'F', '0', '0', 'erp:order:query', '#')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `perms` = VALUES(`perms`);

-- Grant to ADMIN (role_id=1)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 1030), (1, 1035);

-- SALES: submit + query + cancel (owner cancel was previously allowed in UI)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2, 1030), (2, 1035), (2, 1025);

-- FINANCE / WAREHOUSE / INBOUND: query only (view orders where they already have list)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 1035),
(4, 1035),
(5, 1035);
