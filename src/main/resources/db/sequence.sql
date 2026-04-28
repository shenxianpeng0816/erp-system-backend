-- ============================================================
-- Document Sequence Table — replaces in-memory AtomicInteger
-- Run this once against erp_db to add the table + initial rows
-- ============================================================

USE erp_db;

CREATE TABLE IF NOT EXISTS `doc_sequence` (
    `seq_name`    VARCHAR(30)  NOT NULL COMMENT 'sequence key, e.g. SO, INV, DN, IN',
    `next_val`    BIGINT       NOT NULL DEFAULT 1 COMMENT 'next value to use',
    `date_part`   VARCHAR(8)   NOT NULL DEFAULT '' COMMENT 'yyyyMMdd prefix in use; resets on change',
    PRIMARY KEY (`seq_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-prefix document number sequences';

-- Seed initial rows
INSERT IGNORE INTO `doc_sequence` (`seq_name`, `next_val`, `date_part`) VALUES
('SO',  1, ''),
('INV', 1, ''),
('DN',  1, ''),
('IN',  1, '');
