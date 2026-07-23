package com.erp.dto.request;

import lombok.Data;

@Data
public class RejectInboundRequest {
    /** Appended to remark when present */
    private String reason;
}
