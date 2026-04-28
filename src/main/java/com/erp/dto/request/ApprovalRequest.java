package com.erp.dto.request;

import lombok.Data;

@Data
public class ApprovalRequest {
    private String action;     // APPROVE / REJECT / REDIRECT
    private String comment;
    private Long redirectTo;   // only when action = REDIRECT
}
