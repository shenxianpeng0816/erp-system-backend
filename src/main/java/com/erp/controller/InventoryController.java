package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;
import com.erp.entity.Product;
import com.erp.mapper.InventoryLogMapper;
import com.erp.mapper.InventoryMapper;
import com.erp.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final ProductMapper productMapper;

    @GetMapping
    public Result<List<Inventory>> list() {
        return Result.success(inventoryMapper.selectList(null));
    }

    /** Products with stock below minimum threshold */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<Inventory>> alerts() {
        return Result.success(inventoryMapper.selectList(
                new LambdaQueryWrapper<Inventory>()
                        .apply("qty <= min_qty")));
    }

    /** Transaction log for a specific product */
    @GetMapping("/logs/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND','FINANCE')")
    public Result<List<InventoryLog>> logs(@PathVariable Long productId) {
        return Result.success(inventoryLogMapper.findByProduct(productId));
    }

    /** Transaction log for a specific inbound/outbound order */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<InventoryLog>> logsByRef(
            @RequestParam Long refId,
            @RequestParam String refType) {
        return Result.success(inventoryLogMapper.findByRef(refId, refType));
    }

    /**
     * All stock movement rows (inbound/outbound/adjust), newest first.
     * Capped for UI performance.
     */
    @GetMapping("/transaction-logs")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND','FINANCE')")
    public Result<List<InventoryLog>> transactionLogs() {
        return Result.success(inventoryLogMapper.selectList(
                new LambdaQueryWrapper<InventoryLog>()
                        .orderByDesc(InventoryLog::getCreatedAt)
                        .last("LIMIT 3000")));
    }

    @GetMapping("/products")
    public Result<List<Product>> products() {
        return Result.success(productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<Product> createProduct(@RequestBody Product product) {
        product.setStatus(1);
        productMapper.insert(product);
        return Result.success(product);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<Product> updateProduct(@PathVariable Long id, @RequestBody Product body) {
        Product existing = productMapper.selectById(id);
        if (existing == null) throw new BusinessException("Product not found");
        if (body.getProductNo() != null) existing.setProductNo(body.getProductNo());
        if (body.getName() != null) existing.setName(body.getName());
        if (body.getSpec() != null) existing.setSpec(body.getSpec());
        if (body.getCategory() != null) existing.setCategory(body.getCategory());
        if (body.getUnit() != null) existing.setUnit(body.getUnit());
        if (body.getUnitPrice() != null) existing.setUnitPrice(body.getUnitPrice());
        if (body.getImageUrl() != null) existing.setImageUrl(body.getImageUrl());
        if (body.getRemark() != null) existing.setRemark(body.getRemark());
        productMapper.updateById(existing);
        return Result.success(existing);
    }

    /**
     * Soft-deletes the product ({@code status=0}). Blocked while on-hand inventory quantity is positive.
     */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        Product existing = productMapper.selectById(id);
        if (existing == null) throw new BusinessException("Product not found");
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getProductId, id));
        if (inv != null && inv.getQty() != null && inv.getQty() > 0) {
            throw new BusinessException(
                    "Cannot delete product with remaining stock. Please clear the inventory first.");
        }
        productMapper.deleteById(id);
        return Result.success();
    }
}
