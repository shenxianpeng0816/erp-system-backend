package com.erp.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceivableSummary {
    private BigDecimal totalOutstanding;
    private int count;
}
