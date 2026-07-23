-- Allow custom RBAC role keys on legacy user.role (was ENUM of 5 values)
ALTER TABLE `user`
  MODIFY COLUMN `role` VARCHAR(50) NOT NULL DEFAULT 'SALES'
  COMMENT 'Primary role key UPPERCASE; synced from sys_user_role';
