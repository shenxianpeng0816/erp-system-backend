package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("inbound_item")
public class InboundItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inboundId;
    private Long productId;
    private Integer qty;
    /** Purchase / receipt unit cost for this line (stored only; confirm-inbound does not post to finance or change product list price). */
    private BigDecimal unitCost;
}
