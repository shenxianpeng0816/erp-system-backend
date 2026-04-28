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
    private Long shipToCustomerId;
    private Long operatorId;
    private String status; // PENDING / PRINTED / SHIPPED / CONFIRMED / CANCELLED
    private LocalDateTime shippedAt;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
