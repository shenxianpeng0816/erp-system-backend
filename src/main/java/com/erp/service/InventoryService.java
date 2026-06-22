package com.erp.service;

import com.erp.entity.Inventory;

public record StockChangeContext(
        String type,
        Long refId,
        String refType,
        Long operatorId,
        String remark
) {}

public interface InventoryService {

    int getAvailableQty(Long warehouseId, Long productId);

    /** Adds stock; creates inventory row if missing. Returns qty after change. */
    int addStock(Long warehouseId, Long productId, int qty, StockChangeContext ctx);

    /** Deducts stock; throws if insufficient. Returns qty after change. */
    int deductStock(Long warehouseId, Long productId, int qty, StockChangeContext ctx);

    Inventory findInventory(Long warehouseId, Long productId);
}
