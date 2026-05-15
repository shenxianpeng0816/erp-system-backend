package com.erp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** Snapshot: realName or username */
    private String userName;

    private String userRole;

    /** e.g. 新增 /api/inbound */
    private String operationName;

    private LocalDateTime operationAt;

    private String clientIp;

    private String requestUri;

    private String requestParams;
}
