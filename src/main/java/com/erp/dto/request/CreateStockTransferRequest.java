package com.erp.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateStockTransferRequest {
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String remark;
    private List<ItemReq> items;

    @Data
    public static class ItemReq {
        private Long productId;
        private Integer qty;
    }
}
