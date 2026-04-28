package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory_log")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** INBOUND / OUTBOUND / ADJUST */
    private String type;

    /** Positive = stock in, Negative = stock out */
    private Integer qtyChange;

    /** Stock level AFTER this transaction */
    private Integer qtyAfter;

    /** inbound_order.id or outbound_order.id */
    private Long refId;

    /** "INBOUND" or "OUTBOUND" */
    private String refType;

    private Long operatorId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
