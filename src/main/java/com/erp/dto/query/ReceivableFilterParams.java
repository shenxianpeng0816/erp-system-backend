package com.erp.dto.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReceivableFilterParams {
    private String status;
    private Long customerId;
    private LocalDateTime createdFrom;
    private LocalDateTime createdToExclusive;
    private String customerName;
    private String salesUserName;
    private String productName;
    private String orderNo;
    /** When true, exclude SETTLED and CANCELLED (summary default). */
    private Boolean excludeSettledAndCancelled;
}
