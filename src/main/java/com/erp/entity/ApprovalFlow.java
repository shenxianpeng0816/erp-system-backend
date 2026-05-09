package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_flow")
public class ApprovalFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer step;
    private Long approverId;
    /** Filled when listing history; not persisted. */
    @TableField(exist = false)
    private String approverName;
    private String status; // PENDING / APPROVED / REJECTED / REDIRECTED
    private Long redirectTo;
    @TableField(exist = false)
    private String redirectToName;
    private String comment;
    private LocalDateTime actedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
