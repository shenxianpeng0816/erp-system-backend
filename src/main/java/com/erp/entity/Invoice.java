package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("invoice")
public class Invoice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private Long orderId;
    private Long billToCustomerId;
    private BigDecimal amount;
    private String status; // PENDING / PARTIAL / PAID / CANCELLED
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String paymentMethod;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
