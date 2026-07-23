package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.dto.response.InventoryFormOptions;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;
import com.erp.entity.Product;
import com.erp.service.InventoryQueryService;
import com.erp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final ProductService productService;

    @GetMapping("/form-options")
    @PreAuthorize("@ss.hasPermi('erp:inventory:list')")
    public Result<InventoryFormOptions> formOptions(
            @RequestParam(required = false) String countryCode) {
        return Result.success(inventoryQueryService.getFormOptions(countryCode));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:inventory:list')")
    public Result<List<Inventory>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        return Result.success(inventoryQueryService.list(warehouseId, countryCode));
    }

    @GetMapping("/alerts")
    @PreAuthorize("@ss.hasPermi('erp:inventory:alert')")
    public Result<List<Inventory>> alerts(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        return Result.success(inventoryQueryService.alerts(warehouseId, countryCode));
    }

    @GetMapping("/logs/{productId}")
    @PreAuthorize("@ss.hasPermi('erp:inventory:log')")
    public Result<List<InventoryLog>> logs(
            @PathVariable Long productId,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryQueryService.logs(productId, warehouseId));
    }

    @GetMapping("/logs")
    @PreAuthorize("@ss.hasPermi('erp:inventory:log')")
    public Result<List<InventoryLog>> logsByRef(
            @RequestParam Long refId,
            @RequestParam String refType) {
        return Result.success(inventoryQueryService.logsByRef(refId, refType));
    }

    @GetMapping("/transaction-logs")
    @PreAuthorize("@ss.hasPermi('erp:inventory:transaction')")
    public Result<List<InventoryLog>> transactionLogs(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String productName) {
        return Result.success(inventoryQueryService.transactionLogs(warehouseId, productName));
    }

    @GetMapping("/products")
    @PreAuthorize("@ss.hasPermi('erp:inventory:list')")
    public Result<List<Product>> products(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        return Result.success(productService.listProducts(warehouseId, countryCode));
    }

    @PostMapping("/products")
    @PreAuthorize("@ss.hasPermi('erp:inventory:product:add')")
    public Result<Product> createProduct(@RequestBody Product product) {
        return Result.success(productService.create(product));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("@ss.hasPermi('erp:inventory:product:edit')")
    public Result<Product> updateProduct(@PathVariable Long id, @RequestBody Product body) {
        return Result.success(productService.update(id, body));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("@ss.hasPermi('erp:inventory:product:remove')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }
}
