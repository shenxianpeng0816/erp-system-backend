package com.erp.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateInboundRequest {
    private Long warehouseId;
    private String supplier;
    private String remark;
    /** Must reference an active user with role INBOUND or WAREHOUSE */
    private Long operatorId;
    /** Optional — delivery note / signed receipt image URL from /files/upload */
    private String documentUrl;
    private List<ItemReq> items;

    @Data
    public static class ItemReq {
        private Long productId;
        private Integer qty;
        private BigDecimal unitCost;
    }
}
