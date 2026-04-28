package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("outbound_item")
public class OutboundItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long outboundId;
    private Long productId;
    private Integer qty;

    /** Populated via JOIN — not persisted in outbound_item table */
    @TableField(exist = false)
    private String productName;

    /** Populated via JOIN — not persisted in outbound_item table */
    @TableField(exist = false)
    private String productNo;

    /** Populated via JOIN — not persisted in outbound_item table */
    @TableField(exist = false)
    private String spec;

    /** Populated via JOIN — not persisted in outbound_item table */
    @TableField(exist = false)
    private String unit;
}
