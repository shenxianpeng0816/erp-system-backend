package com.erp.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receivableId;
    private BigDecimal amount;
    private Integer qty;
    private String paymentMethod;
    private String transactionRef;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paidAt;
    private String remark;
    private Long createdBy;

    @TableField(exist = false)
    private String createdByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
