package com.erp.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRequest {
    private BigDecimal amount;
    private Integer qty;
    private String paymentMethod;
    private String transactionRef;
    private LocalDateTime paidAt;
    private String remark;
}
