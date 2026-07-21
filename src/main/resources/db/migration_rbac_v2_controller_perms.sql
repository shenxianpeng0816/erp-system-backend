-- ============================================================
-- RBAC v2: Full permission seed from Controller @PreAuthorize
-- Safe to re-run (deletes F buttons under known parents, rebuilds role_menu)
-- Prerequisite: migration_rbac.sql already applied (tables + 5 roles + C menus)
-- ============================================================
USE erp_db;

-- Ensure roles exist
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `role_sort`, `status`, `remark`) VALUES
(1, 'Administrator', 'admin',     1, '0', 'Full access'),
(2, 'Sales',         'sales',     2, '0', 'Sales orders'),
(3, 'Finance',       'finance',   3, '0', 'Receivables & invoices'),
(4, 'Warehouse',     'warehouse', 4, '0', 'Outbound & inventory'),
(5, 'Inbound',       'inbound',   5, '0', 'Inbound operations')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- Ensure directory + page menus (C)
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(100, 'ERP',        0,   1, '',            'M', '0', '0', NULL,                    'erp'),
(101, 'Dashboard',  100, 1, '/dashboard',  'C', '0', '0', 'erp:dashboard:view',    'dashboard'),
(102, 'Orders',     100, 2, '/orders',     'C', '0', '0', 'erp:order:list',        'orders'),
(103, 'Outbound',   100, 3, '/outbound',   'C', '0', '0', 'erp:outbound:list',     'outbound'),
(104, 'Inbound',    100, 4, '/inbound',    'C', '0', '0', 'erp:inbound:list',      'inbound'),
(105, 'Finance',    100, 5, '/finance',    'C', '0', '0', 'erp:finance:list',      'finance'),
(106, 'Inventory',  100, 6, '/inventory',  'C', '0', '0', 'erp:inventory:list',    'inventory'),
(107, 'Customers',  100, 7, '/customers',  'C', '0', '0', 'erp:customer:list',     'customers'),
(108, 'Users',      100, 8, '/users',      'C', '0', '0', 'erp:user:list',         'users'),
(109, 'Roles',      100, 9, '/roles',      'C', '0', '0', 'erp:role:list',         'roles')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `perms` = VALUES(`perms`), `path` = VALUES(`path`);

-- Remove old button rows under these parents (keep C/M), then re-insert full F set
DELETE FROM `sys_role_menu` WHERE `menu_id` >= 1020;
DELETE FROM `sys_menu` WHERE `menu_type` = 'F' AND `menu_id` >= 1020;

-- ---------- Button permissions (F) — one perms per Controller capability ----------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
-- Orders (SalesOrderController)
(1021, 'Create Order',        102, 1,  '', 'F', '0', '0', 'erp:order:add',           '#'),
(1022, 'Edit Order',          102, 2,  '', 'F', '0', '0', 'erp:order:edit',          '#'),
(1023, 'Approve Order',       102, 3,  '', 'F', '0', '0', 'erp:order:approve',       '#'),
(1024, 'Delete Order',        102, 4,  '', 'F', '0', '0', 'erp:order:remove',        '#'),
(1025, 'Cancel Order',        102, 5,  '', 'F', '0', '0', 'erp:order:cancel',        '#'),
(1026, 'My Orders List',      102, 6,  '', 'F', '0', '0', 'erp:order:list:mine',     '#'),
(1027, 'All Orders List',     102, 7,  '', 'F', '0', '0', 'erp:order:list:all',      '#'),
(1028, 'Pending Approvals',   102, 8,  '', 'F', '0', '0', 'erp:order:pending',       '#'),
(1029, 'Confirm Delivery',    102, 9,  '', 'F', '0', '0', 'erp:order:confirm',       '#'),
-- Outbound
(1031, 'Outbound List',       103, 1,  '', 'F', '0', '0', 'erp:outbound:list',       '#'),
(1032, 'Outbound Export',     103, 2,  '', 'F', '0', '0', 'erp:outbound:export',     '#'),
(1033, 'Outbound Print',      103, 3,  '', 'F', '0', '0', 'erp:outbound:print',      '#'),
(1034, 'Ship Outbound',       103, 4,  '', 'F', '0', '0', 'erp:outbound:ship',       '#'),
-- Inbound
(1041, 'Inbound List',        104, 1,  '', 'F', '0', '0', 'erp:inbound:list',        '#'),
(1042, 'Inbound Export',      104, 2,  '', 'F', '0', '0', 'erp:inbound:export',      '#'),
(1043, 'Inbound Query',       104, 3,  '', 'F', '0', '0', 'erp:inbound:query',       '#'),
(1044, 'Create Inbound',      104, 4,  '', 'F', '0', '0', 'erp:inbound:add',         '#'),
(1045, 'Edit Inbound',        104, 5,  '', 'F', '0', '0', 'erp:inbound:edit',        '#'),
(1046, 'Confirm Inbound',     104, 6,  '', 'F', '0', '0', 'erp:inbound:confirm',     '#'),
(1047, 'Reject Inbound',      104, 7,  '', 'F', '0', '0', 'erp:inbound:reject',      '#'),
(1048, 'Delete Inbound',      104, 8,  '', 'F', '0', '0', 'erp:inbound:remove',      '#'),
-- Finance
(1051, 'Invoice List',        105, 1,  '', 'F', '0', '0', 'erp:finance:invoice:list','#'),
(1052, 'Receivable List',     105, 2,  '', 'F', '0', '0', 'erp:finance:receivable:list','#'),
(1053, 'Receivable Export',   105, 3,  '', 'F', '0', '0', 'erp:finance:receivable:export','#'),
(1054, 'Record Payment',      105, 4,  '', 'F', '0', '0', 'erp:finance:pay',         '#'),
(1055, 'Edit Payment',        105, 5,  '', 'F', '0', '0', 'erp:finance:pay:edit',    '#'),
-- Inventory
(1061, 'Inventory Alert',     106, 1,  '', 'F', '0', '0', 'erp:inventory:alert',     '#'),
(1062, 'Inventory Log',       106, 2,  '', 'F', '0', '0', 'erp:inventory:log',       '#'),
(1063, 'Transaction Logs',    106, 3,  '', 'F', '0', '0', 'erp:inventory:transaction','#'),
(1064, 'Add Product',         106, 4,  '', 'F', '0', '0', 'erp:inventory:product:add','#'),
(1065, 'Edit Product',        106, 5,  '', 'F', '0', '0', 'erp:inventory:product:edit','#'),
(1066, 'Delete Product',      106, 6,  '', 'F', '0', '0', 'erp:inventory:product:remove','#'),
-- Stock transfer (under inventory)
(1067, 'Transfer List',       106, 7,  '', 'F', '0', '0', 'erp:transfer:list',       '#'),
(1068, 'Create Transfer',     106, 8,  '', 'F', '0', '0', 'erp:transfer:add',        '#'),
(1069, 'Confirm Transfer',    106, 9,  '', 'F', '0', '0', 'erp:transfer:confirm',    '#'),
(1070, 'Cancel Transfer',     106, 10, '', 'F', '0', '0', 'erp:transfer:cancel',     '#'),
-- Customers (parent 107 — use 1071+)
(1071, 'Create Customer',     107, 1,  '', 'F', '0', '0', 'erp:customer:add',        '#'),
(1072, 'Edit Customer',       107, 2,  '', 'F', '0', '0', 'erp:customer:edit',       '#'),
(1073, 'Delete Customer',     107, 3,  '', 'F', '0', '0', 'erp:customer:remove',     '#'),
-- Users
(1081, 'User List',           108, 1,  '', 'F', '0', '0', 'erp:user:list',           '#'),
(1082, 'Create/Edit User',    108, 2,  '', 'F', '0', '0', 'erp:user:edit',           '#'),
(1083, 'Disable User',        108, 3,  '', 'F', '0', '0', 'erp:user:remove',         '#'),
(1084, 'Users By Role',       108, 4,  '', 'F', '0', '0', 'erp:user:byRole',         '#'),
(1085, 'Operation Logs',      108, 5,  '', 'F', '0', '0', 'erp:user:operlog',        '#'),
-- Roles / system
(1091, 'Role List',           109, 1,  '', 'F', '0', '0', 'erp:role:list',           '#'),
(1092, 'Assign Role Menus',   109, 2,  '', 'F', '0', '0', 'erp:role:edit',           '#'),
(1093, 'Menu Tree',           109, 3,  '', 'F', '0', '0', 'erp:menu:list',           '#'),
(1094, 'Assign User Roles',   109, 4,  '', 'F', '0', '0', 'erp:user:role:edit',      '#'),
-- Warehouse create (under inventory)
(1101, 'Create Warehouse',    106, 11, '', 'F', '0', '0', 'erp:warehouse:add',       '#'),
-- File upload
(1121, 'File Upload',         100, 20, '', 'F', '0', '0', 'erp:file:upload',         '#');

-- Rebuild ALL role_menu from scratch
DELETE FROM `sys_role_menu`;

-- ADMIN = every menu
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu`;

-- SALES (role_id=2): from PreAuthorize hasAnyRole including SALES
-- orders: add/edit/mine/confirm; customers add/edit; dashboard; users byRole
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 100), (2, 101),
(2, 102), (2, 1021), (2, 1022), (2, 1026), (2, 1029),
(2, 107), (2, 1071), (2, 1072),
(2, 1084);

-- FINANCE (3): finance*, customers add/edit, inventory log/transaction, file upload, byRole, dashboard
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 100), (3, 101),
(3, 105), (3, 1051), (3, 1052), (3, 1053), (3, 1054), (3, 1055),
(3, 106), (3, 1062), (3, 1063),
(3, 107), (3, 1071), (3, 1072),
(3, 1084),
(3, 1121);

-- WAREHOUSE (4): orders list:all; outbound*; inbound* (not remove); inventory*; transfer*; file; byRole
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(4, 100), (4, 101),
(4, 102), (4, 1027),
(4, 103), (4, 1031), (4, 1032), (4, 1033), (4, 1034),
(4, 104), (4, 1041), (4, 1042), (4, 1043), (4, 1044), (4, 1045), (4, 1046), (4, 1047),
(4, 106), (4, 1061), (4, 1062), (4, 1063), (4, 1064), (4, 1065), (4, 1066),
(4, 1067), (4, 1068), (4, 1069), (4, 1070),
(4, 1084),
(4, 1121);

-- INBOUND (5): same warehouse set for warehouse ops (matches hasAnyRole INBOUND/WAREHOUSE)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(5, 100), (5, 101),
(5, 102), (5, 1027),
(5, 103), (5, 1031), (5, 1032), (5, 1033), (5, 1034),
(5, 104), (5, 1041), (5, 1042), (5, 1043), (5, 1044), (5, 1045), (5, 1046), (5, 1047),
(5, 106), (5, 1061), (5, 1062), (5, 1063), (5, 1064), (5, 1065), (5, 1066),
(5, 1067), (5, 1068), (5, 1069), (5, 1070),
(5, 1084),
(5, 1121);

-- Re-sync user_role from user.role ENUM (existing accounts)
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id,
       CASE UPPER(u.role)
           WHEN 'ADMIN' THEN 1
           WHEN 'SALES' THEN 2
           WHEN 'FINANCE' THEN 3
           WHEN 'WAREHOUSE' THEN 4
           WHEN 'INBOUND' THEN 5
           ELSE 2
       END
FROM `user` u
WHERE u.status = 1;
