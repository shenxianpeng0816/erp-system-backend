-- Add CANCELLED to receivable.status for voided invoices on order cancellation
ALTER TABLE `receivable`
    MODIFY COLUMN `status` ENUM('OUTSTANDING','PARTIAL','SETTLED','OVERDUE','CANCELLED')
    NOT NULL DEFAULT 'OUTSTANDING';
