package com.erp.dto.request;

import lombok.Data;

@Data
public class CancelOrderRequest {
    /** Optional cancellation reason (e.g. customer requested). */
    private String reason;
}
