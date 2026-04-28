package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerNo;
    private String name;
    private String type; // REGULAR / PICKUP_POINT / DISTRIBUTOR
    private String phone;
    private String email;
    private String address;
    private String contactPerson;
    private Integer isPickupPoint;
    private BigDecimal creditLimit;
    private String remark;

    /** 1=active, 0=disabled (soft delete) */
    @TableLogic(value = "1", delval = "0")
    private Integer status;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
