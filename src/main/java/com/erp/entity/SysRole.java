package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;
    private String roleName;
    /** lowercase key: admin / sales / finance / warehouse / inbound */
    private String roleKey;
    private Integer roleSort;
    /** 0=normal 1=disabled */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** menu ids for assign UI (not a column) */
    @TableField(exist = false)
    private List<Long> menuIds;
}
