-- ============================================================
-- Rename approval statuses: first / final (drop finance/admin naming)
-- Also rename RBAC perms: approve:first / approve:final
-- Safe for DBs that already applied two-level approval migrations
-- ============================================================

-- 1) Expand ENUM to include new values alongside old
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

-- 2) Migrate data
UPDATE `sales_order`
SET `status` = 'PENDING_FIRST_APPROVAL'
WHERE `status` = 'PENDING_FINANCE_APPROVAL';

UPDATE `sales_order`
SET `status` = 'PENDING_FINAL_APPROVAL'
WHERE `status` IN ('PENDING_ADMIN_APPROVAL', 'PENDING_APPROVAL');

-- 3) Shrink ENUM — only clean status names remain
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

-- 4) RBAC permission rename (keep menu_id)
UPDATE `sys_menu`
SET `menu_name` = 'First Approve Order',
    `perms` = 'erp:order:approve:first'
WHERE `menu_id` = 1036
   OR `perms` = 'erp:order:approve:finance';

UPDATE `sys_menu`
SET `menu_name` = 'Final Approve Order',
    `perms` = 'erp:order:approve:final'
WHERE `menu_id` = 1023
   OR `perms` IN ('erp:order:approve:admin', 'erp:order:approve');
