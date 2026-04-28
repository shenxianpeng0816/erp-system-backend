package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sales_order_item")
public class SalesOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal total;
    private String remark;

    /** Populated via JOIN — not persisted */
    @TableField(exist = false)
    private String productName;

    /** Populated via JOIN — not persisted */
    @TableField(exist = false)
    private String productNo;

    /** Populated via JOIN — not persisted */
    @TableField(exist = false)
    private String spec;

    /** Populated via JOIN — not persisted */
    @TableField(exist = false)
    private String unit;
}
