-- ============================================================
-- Option B: RuoYi-style RBAC (keep existing `user` table)
-- Database: erp_db
-- ============================================================
USE erp_db;

-- Roles
CREATE TABLE IF NOT EXISTS `sys_role` (
    `role_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `role_name`   VARCHAR(100) NOT NULL COMMENT 'display name e.g. Sales',
    `role_key`    VARCHAR(50)  NOT NULL COMMENT 'admin/sales/finance/warehouse/inbound',
    `role_sort`   INT          NOT NULL DEFAULT 0,
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=normal 1=disabled',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC Roles';

-- Menus (directory / menu / button)
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `menu_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `menu_name`   VARCHAR(100) NOT NULL COMMENT 'English display name',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0,
    `order_num`   INT          NOT NULL DEFAULT 0,
    `path`        VARCHAR(200) DEFAULT '' COMMENT 'frontend route e.g. /orders',
    `component`   VARCHAR(255) DEFAULT NULL,
    `menu_type`   CHAR(1)      NOT NULL COMMENT 'M=directory C=menu F=button',
    `visible`     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=show 1=hide',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=normal 1=disabled',
    `perms`       VARCHAR(100) DEFAULT NULL COMMENT 'permission string e.g. erp:order:add',
    `icon`        VARCHAR(100) DEFAULT '#',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`menu_id`),
    KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC Menus';

-- User <-> Role (user_id references existing `user`.id)
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-Role';

-- Role <-> Menu
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `menu_id`),
    KEY `idx_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Role-Menu';

-- Seed roles (role_key lowercase; user.role ENUM stays UPPERCASE for legacy)
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `role_sort`, `status`, `remark`) VALUES
(1, 'Administrator', 'admin',     1, '0', 'Full access'),
(2, 'Sales',         'sales',     2, '0', 'Sales orders'),
(3, 'Finance',       'finance',   3, '0', 'Receivables & invoices'),
(4, 'Warehouse',     'warehouse', 4, '0', 'Outbound & inventory'),
(5, 'Inbound',       'inbound',   5, '0', 'Inbound operations')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- Seed menus (IDs fixed for role_menu mapping)
-- Directory ERP (100)
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(100, 'ERP', 0, 1, '', 'M', '0', '0', NULL, 'erp')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- Top menus (C)
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(101, 'Dashboard',  100, 1, '/dashboard', 'C', '0', '0', 'erp:dashboard:view',  'dashboard'),
(102, 'Orders',     100, 2, '/orders',    'C', '0', '0', 'erp:order:list',      'orders'),
(103, 'Outbound',   100, 3, '/outbound',  'C', '0', '0', 'erp:outbound:list',   'outbound'),
(104, 'Inbound',    100, 4, '/inbound',   'C', '0', '0', 'erp:inbound:list',    'inbound'),
(105, 'Finance',    100, 5, '/finance',   'C', '0', '0', 'erp:finance:list',    'finance'),
(106, 'Inventory',  100, 6, '/inventory', 'C', '0', '0', 'erp:inventory:list',  'inventory'),
(107, 'Customers',  100, 7, '/customers', 'C', '0', '0', 'erp:customer:list',   'customers'),
(108, 'Users',      100, 8, '/users',     'C', '0', '0', 'erp:user:list',       'users'),
(109, 'Roles',      100, 9, '/roles',     'C', '0', '0', 'erp:role:list',       'roles')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- Button permissions (F)
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(1021, 'Create Order',   102, 1, '', 'F', '0', '0', 'erp:order:add',      '#'),
(1022, 'Edit Order',     102, 2, '', 'F', '0', '0', 'erp:order:edit',     '#'),
(1023, 'Approve Order',  102, 3, '', 'F', '0', '0', 'erp:order:approve',  '#'),
(1024, 'Delete Order',   102, 4, '', 'F', '0', '0', 'erp:order:remove',   '#'),
(1025, 'Cancel Order',   102, 5, '', 'F', '0', '0', 'erp:order:cancel',   '#'),
(1031, 'Ship Outbound',  103, 1, '', 'F', '0', '0', 'erp:outbound:ship',  '#'),
(1041, 'Create Inbound', 104, 1, '', 'F', '0', '0', 'erp:inbound:add',    '#'),
(1042, 'Confirm Inbound',104, 2, '', 'F', '0', '0', 'erp:inbound:confirm','#'),
(1051, 'Record Payment', 105, 1, '', 'F', '0', '0', 'erp:finance:pay',    '#'),
(1061, 'Manage Product', 106, 1, '', 'F', '0', '0', 'erp:inventory:product', '#'),
(1062, 'Manage Warehouse',106,2, '', 'F', '0', '0', 'erp:inventory:warehouse', '#'),
(1071, 'Create Customer',107, 1, '', 'F', '0', '0', 'erp:customer:add',   '#'),
(1072, 'Edit Customer',  107, 2, '', 'F', '0', '0', 'erp:customer:edit',  '#'),
(1073, 'Delete Customer',107, 3, '', 'F', '0', '0', 'erp:customer:remove','#'),
(1081, 'Manage User',    108, 1, '', 'F', '0', '0', 'erp:user:edit',      '#'),
(1091, 'Assign Role',    109, 1, '', 'F', '0', '0', 'erp:role:edit',      '#')
ON DUPLICATE KEY UPDATE `perms` = VALUES(`perms`);

-- Role-menu: ADMIN = all menus
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu`;

-- SALES: dashboard, orders(+add/edit), customers(+add/edit)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 100), (2, 101), (2, 102), (2, 1021), (2, 1022), (2, 107), (2, 1071), (2, 1072);

-- FINANCE: dashboard, finance(+pay), customers(+add/edit)
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 100), (3, 101), (3, 105), (3, 1051), (3, 107), (3, 1071), (3, 1072);

-- WAREHOUSE: dashboard, orders(list), outbound, inbound, inventory
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(4, 100), (4, 101), (4, 102), (4, 103), (4, 1031), (4, 104), (4, 1041), (4, 1042),
(4, 106), (4, 1061);

-- INBOUND: dashboard, orders(list), outbound, inbound, inventory
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(5, 100), (5, 101), (5, 102), (5, 103), (5, 1031), (5, 104), (5, 1041), (5, 1042),
(5, 106), (5, 1061);

-- Migrate existing users: map user.role ENUM -> sys_user_role
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
