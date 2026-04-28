package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sales_order")
public class SalesOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long salesUserId;
    private Long shipToCustomerId;
    private Long billToCustomerId;
    private String status; // DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/SHIPPED/CONFIRMED/CANCELLED
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String priceTerm;
    private Integer validityDays;
    private String remark;
    private String rejectReason;
    private String signImageUrl;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
