package com.erp.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;
    private Long customerId;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private Integer receivedQty;
    private BigDecimal balance;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private String status; // OUTSTANDING / PARTIAL / SETTLED / OVERDUE / CANCELLED

    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private String salesUserName;
    @TableField(exist = false)
    private Integer unpaidQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
