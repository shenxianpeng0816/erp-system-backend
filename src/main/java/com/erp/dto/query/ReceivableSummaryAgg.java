package com.erp.dto.query;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceivableSummaryAgg {
    private BigDecimal totalOutstanding;
    private Long recordCount;
}
