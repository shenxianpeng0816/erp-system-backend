package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long menuId;
    private String menuName;
    private Long parentId;
    private Integer orderNum;
    /** frontend route path e.g. /orders */
    private String path;
    private String component;
    /** M=directory C=menu F=button */
    private String menuType;
    /** 0=show 1=hide */
    private String visible;
    /** 0=normal 1=disabled */
    private String status;
    private String perms;
    private String icon;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
}
