package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @GetMapping("/products")
    public Result<List<Product>> products() {
        return Result.success(productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public Result<Product> createProduct(@RequestBody Product product) {
        product.setStatus(1);
        productMapper.insert(product);
        return Result.success(product);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public Result<Product> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productMapper.updateById(product);
        return Result.success(product);
    }
}
