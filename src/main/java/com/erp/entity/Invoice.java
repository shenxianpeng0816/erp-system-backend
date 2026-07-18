package com.erp.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private String paymentMethod;
    private String remark;

    @TableField(exist = false)
    private String billToCustomerName;

    @TableField(exist = false)
    private String orderNo;

    @TableField(exist = false)
    private String salesUserName;

    /** From related sales_order.country_code — for currency display */
    @TableField(exist = false)
    private String countryCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
