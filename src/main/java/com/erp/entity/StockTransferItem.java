package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("stock_transfer_item")
public class StockTransferItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transferId;
    private Long productId;
    private Integer qty;

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String productNo;
}
