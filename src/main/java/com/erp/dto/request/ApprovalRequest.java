package com.erp.dto.request;

import lombok.Data;

@Data
public class ApprovalRequest {
    /** APPROVE / REJECT */
    private String action;
    private String comment;
}
