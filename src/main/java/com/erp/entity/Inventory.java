package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    /** Denormalized from warehouse.country_code for filtering */
    private String countryCode;
    private Long productId;
    private Integer qty;
    private Integer minQty;
    private LocalDateTime lastUpdated;

    @TableField(exist = false)
    private String warehouseName;
}
