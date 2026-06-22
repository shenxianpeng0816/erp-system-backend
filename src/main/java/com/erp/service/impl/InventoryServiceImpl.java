package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;
import com.erp.entity.Product;
import com.erp.mapper.InventoryLogMapper;
import com.erp.mapper.InventoryMapper;
import com.erp.mapper.ProductMapper;
import com.erp.service.InventoryService;
import com.erp.service.StockChangeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final ProductMapper productMapper;

    @Override
    public int getAvailableQty(Long warehouseId, Long productId) {
        Inventory inv = findInventory(warehouseId, productId);
        return inv != null && inv.getQty() != null ? inv.getQty() : 0;
    }

    @Override
    public Inventory findInventory(Long warehouseId, Long productId) {
        return inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseId, warehouseId)
                        .eq(Inventory::getProductId, productId));
    }

    @Override
    public int addStock(Long warehouseId, Long productId, int qty, StockChangeContext ctx) {
        if (warehouseId == null || productId == null) {
            throw new BusinessException("Warehouse and product are required");
        }
        if (qty <= 0) {
            throw new BusinessException("Quantity must be positive");
        }

        Inventory inv = findInventory(warehouseId, productId);
        int qtyAfter;
        if (inv == null) {
            inv = new Inventory();
            inv.setWarehouseId(warehouseId);
            inv.setProductId(productId);
            inv.setQty(qty);
            inv.setMinQty(0);
            inventoryMapper.insert(inv);
            qtyAfter = qty;
        } else {
            qtyAfter = inv.getQty() + qty;
            inv.setQty(qtyAfter);
            inventoryMapper.updateById(inv);
        }

        writeLog(warehouseId, productId, ctx.type(), qty, qtyAfter, ctx);
        return qtyAfter;
    }

    @Override
    public int deductStock(Long warehouseId, Long productId, int qty, StockChangeContext ctx) {
        if (warehouseId == null || productId == null) {
            throw new BusinessException("Warehouse and product are required");
        }
        if (qty <= 0) {
            throw new BusinessException("Quantity must be positive");
        }

        Inventory inv = findInventory(warehouseId, productId);
        if (inv == null || inv.getQty() == null || inv.getQty() < qty) {
            String label = productLabel(productId);
            throw new BusinessException("Insufficient stock for " + label + ". Requested: " + qty
                    + ", available: " + (inv != null && inv.getQty() != null ? inv.getQty() : 0));
        }

        int qtyAfter = inv.getQty() - qty;
        inv.setQty(qtyAfter);
        inventoryMapper.updateById(inv);

        writeLog(warehouseId, productId, ctx.type(), -qty, qtyAfter, ctx);
        return qtyAfter;
    }

    private void writeLog(Long warehouseId, Long productId, String type, int qtyChange, int qtyAfter,
                          StockChangeContext ctx) {
        InventoryLog log = new InventoryLog();
        log.setWarehouseId(warehouseId);
        log.setProductId(productId);
        log.setType(type);
        log.setQtyChange(qtyChange);
        log.setQtyAfter(qtyAfter);
        log.setRefId(ctx.refId());
        log.setRefType(ctx.refType());
        log.setOperatorId(ctx.operatorId());
        log.setRemark(ctx.remark());
        inventoryLogMapper.insert(log);
    }

    private String productLabel(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return "Product #" + productId;
        }
        return product.getProductNo() + " — " + product.getName();
    }
}
