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
    /** Null while PENDING (unassigned); set to actor on approve/reject. */
    private Long approverId;
    /** System role snapshot at action time, e.g. FINANCE or admin,sales. */
    private String approverRole;
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
