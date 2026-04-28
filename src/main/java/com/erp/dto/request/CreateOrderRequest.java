package com.erp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull(message = "Ship-to customer is required")
    private Long shipToCustomerId;

    @NotNull(message = "Bill-to customer is required")
    private Long billToCustomerId;

    private String paymentMethod;
    private String priceTerm;
    private Integer validityDays = 30;
    private String remark;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull private Long productId;
        @NotNull private Integer qty;
        @NotNull private BigDecimal unitPrice;
        private String remark;
    }
}
