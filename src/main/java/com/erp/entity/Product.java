package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productNo;
    private String name;
    private String spec;
    private String category;
    private String unit;
    private BigDecimal unitPrice;
    private String imageUrl;
    private String remark;

    /** 1=active, 0=disabled (soft delete) */
    @TableLogic(value = "1", delval = "0")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
