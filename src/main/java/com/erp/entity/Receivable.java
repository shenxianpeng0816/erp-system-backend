package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("receivable")
public class Receivable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invoiceId;
    private Long customerId;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private BigDecimal balance;
    private LocalDate dueDate;
    private String status; // OUTSTANDING / PARTIAL / SETTLED / OVERDUE

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
