package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbound_order")
public class OutboundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String outboundNo;
    private Long orderId;
    private Long warehouseId;
    private Long shipToCustomerId;
    private Long operatorId;
    private String status; // PENDING / PRINTED / SHIPPED / CONFIRMED / CANCELLED
    private LocalDateTime shippedAt;
    private String remark;

    @TableField(exist = false)
    private String shipToCustomerName;
    @TableField(exist = false)
    private String salesUserName;
    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String warehouseName;
    /** e.g. "ABC-100 ×2 / XYZ-200 ×1" */
    @TableField(exist = false)
    private String productSummary;
    @TableField(exist = false)
    private Integer totalQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
