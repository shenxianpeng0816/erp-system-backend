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
    /** KE / UG / TZ — may differ from ship-to customer country */
    private String countryCode;
    /** Fulfillment warehouse (ship-from) */
    private Long shipFromWarehouseId;

    @TableField(exist = false)
    private String shipFromWarehouseName;
    @TableField(exist = false)
    private String salesUserName;
    @TableField(exist = false)
    private String shipToCustomerName;
    @TableField(exist = false)
    private String billToCustomerName;
    /** DRAFT / PENDING_FIRST_APPROVAL / PENDING_FINAL_APPROVAL / APPROVED / REJECTED / SHIPPED / CONFIRMED / CANCELLED */
    private String status;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String priceTerm;
    private Integer validityDays;
    private String remark;
    /** Whether customer requires ETR (Electronic Tax Register) invoice details */
    private Boolean etrRequired;
    private String etrCompanyName;
    private String etrCompanyKraPin;
    private String rejectReason;
    private String signImageUrl;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
