package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.CountryEnum;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;
import com.erp.entity.Product;
import com.erp.entity.Warehouse;
import com.erp.mapper.InventoryLogMapper;
import com.erp.mapper.InventoryMapper;
import com.erp.mapper.ProductMapper;
import com.erp.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    @GetMapping
    public Result<List<Inventory>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        LambdaQueryWrapper<Inventory> q = new LambdaQueryWrapper<Inventory>()
                .orderByAsc(Inventory::getProductId);
        if (warehouseId != null) {
            q.eq(Inventory::getWarehouseId, warehouseId);
        }
        String cc = normalizeOptionalCountry(countryCode);
        if (cc != null) {
            q.eq(Inventory::getCountryCode, cc);
        }
        List<Inventory> rows = inventoryMapper.selectList(q);
        enrichWarehouseNames(rows);
        return Result.success(rows);
    }

    /** Products with stock below minimum threshold */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<Inventory>> alerts(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        LambdaQueryWrapper<Inventory> q = new LambdaQueryWrapper<Inventory>()
                .apply("qty <= min_qty");
        if (warehouseId != null) {
            q.eq(Inventory::getWarehouseId, warehouseId);
        }
        String cc = normalizeOptionalCountry(countryCode);
        if (cc != null) {
            q.eq(Inventory::getCountryCode, cc);
        }
        List<Inventory> rows = inventoryMapper.selectList(q);
        enrichWarehouseNames(rows);
        return Result.success(rows);
    }

    /** Transaction log for a specific product */
    @GetMapping("/logs/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND','FINANCE')")
    public Result<List<InventoryLog>> logs(
            @PathVariable Long productId,
            @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<InventoryLog> q = new LambdaQueryWrapper<InventoryLog>()
                .eq(InventoryLog::getProductId, productId)
                .orderByDesc(InventoryLog::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InventoryLog::getWarehouseId, warehouseId);
        }
        List<InventoryLog> logs = inventoryLogMapper.selectList(q);
        enrichLogWarehouseNames(logs);
        return Result.success(logs);
    }

    /** Transaction log for a specific inbound/outbound order */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<InventoryLog>> logsByRef(
            @RequestParam Long refId,
            @RequestParam String refType) {
        List<InventoryLog> logs = inventoryLogMapper.findByRef(refId, refType);
        enrichLogWarehouseNames(logs);
        return Result.success(logs);
    }

    /**
     * All stock movement rows (inbound/outbound/adjust), newest first.
     * Capped for UI performance.
     */
    @GetMapping("/transaction-logs")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND','FINANCE')")
    public Result<List<InventoryLog>> transactionLogs(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String productName) {
        LambdaQueryWrapper<InventoryLog> q = new LambdaQueryWrapper<InventoryLog>()
                .orderByDesc(InventoryLog::getCreatedAt)
                .last("LIMIT 3000");
        if (warehouseId != null) {
            q.eq(InventoryLog::getWarehouseId, warehouseId);
        }
        if (productName != null && !productName.isBlank()) {
            String keyword = productName.trim();
            List<Long> productIds = productMapper.selectList(
                            new LambdaQueryWrapper<Product>().like(Product::getName, keyword))
                    .stream()
                    .map(Product::getId)
                    .toList();
            if (productIds.isEmpty()) {
                return Result.success(List.of());
            }
            q.in(InventoryLog::getProductId, productIds);
        }
        List<InventoryLog> logs = inventoryLogMapper.selectList(q);
        enrichLogWarehouseNames(logs);
        return Result.success(logs);
    }

    @GetMapping("/products")
    public Result<List<Product>> products(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String countryCode) {
        String cc = resolveProductCountryFilter(warehouseId, countryCode);
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getProductNo);
        if (cc != null) {
            q.eq(Product::getCountryCode, cc);
        }
        List<Product> products = productMapper.selectList(q);
        enrichProductStock(products, warehouseId);
        return Result.success(products);
    }

    private String resolveProductCountryFilter(Long warehouseId, String countryCode) {
        String cc = normalizeOptionalCountry(countryCode);
        if (cc != null) {
            return cc;
        }
        if (warehouseId == null) {
            return null;
        }
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null || wh.getCountryCode() == null || wh.getCountryCode().isBlank()) {
            return null;
        }
        return normalizeOptionalCountry(wh.getCountryCode());
    }

    private void enrichProductStock(List<Product> products, Long warehouseId) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        LambdaQueryWrapper<Inventory> q = new LambdaQueryWrapper<Inventory>()
                .in(Inventory::getProductId, productIds);
        if (warehouseId != null) {
            q.eq(Inventory::getWarehouseId, warehouseId);
        }
        List<Inventory> inventories = inventoryMapper.selectList(q);
        Map<Long, Integer> qtyByProduct = new HashMap<>();
        for (Inventory inv : inventories) {
            int qty = inv.getQty() != null ? inv.getQty() : 0;
            if (warehouseId != null) {
                qtyByProduct.put(inv.getProductId(), qty);
            } else {
                qtyByProduct.merge(inv.getProductId(), qty, Integer::sum);
            }
        }
        for (Product p : products) {
            p.setStockQty(qtyByProduct.getOrDefault(p.getId(), 0));
        }
    }

    private void enrichWarehouseNames(List<Inventory> rows) {
        if (rows == null || rows.isEmpty()) return;
        Set<Long> whIds = rows.stream()
                .map(Inventory::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (whIds.isEmpty()) return;
        Map<Long, String> names = warehouseMapper.selectList(
                        new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, whIds))
                .stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        for (Inventory row : rows) {
            if (row.getWarehouseId() != null) {
                row.setWarehouseName(names.get(row.getWarehouseId()));
            }
        }
    }

    private void enrichLogWarehouseNames(List<InventoryLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        Set<Long> whIds = logs.stream()
                .map(InventoryLog::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (whIds.isEmpty()) return;
        Map<Long, String> names = warehouseMapper.selectList(
                        new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, whIds))
                .stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        for (InventoryLog log : logs) {
            if (log.getWarehouseId() != null) {
                log.setWarehouseName(names.get(log.getWarehouseId()));
            }
        }
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<Product> createProduct(@RequestBody Product product) {
        product.setCountryCode(requireCountryCode(product.getCountryCode()));
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BusinessException("Product name is required");
        }
        product.setName(product.getName().trim());
        assertUniqueCountryName(product.getCountryCode(), product.getName(), null);
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
        if (body.getName() != null) {
            if (body.getName().isBlank()) {
                throw new BusinessException("Product name is required");
            }
            existing.setName(body.getName().trim());
        }
        if (body.getSpec() != null) existing.setSpec(body.getSpec());
        if (body.getCategory() != null) existing.setCategory(body.getCategory());
        if (body.getUnit() != null) existing.setUnit(body.getUnit());
        if (body.getUnitPrice() != null) existing.setUnitPrice(body.getUnitPrice());
        if (body.getCountryCode() != null && !body.getCountryCode().isBlank()) {
            String nextCc = requireCountryCode(body.getCountryCode());
            String prevCc = existing.getCountryCode() != null
                    ? existing.getCountryCode().trim().toUpperCase(Locale.ROOT) : "";
            if (!nextCc.equals(prevCc)) {
                long positiveRows = inventoryMapper.selectCount(
                        new LambdaQueryWrapper<Inventory>()
                                .eq(Inventory::getProductId, id)
                                .gt(Inventory::getQty, 0));
                if (positiveRows > 0) {
                    throw new BusinessException(
                            "Cannot change product country while stock remains. Clear inventory first.");
                }
            }
            existing.setCountryCode(nextCc);
        }
        if (body.getImageUrl() != null) existing.setImageUrl(body.getImageUrl());
        if (body.getRemark() != null) existing.setRemark(body.getRemark());
        assertUniqueCountryName(existing.getCountryCode(), existing.getName(), id);
        productMapper.updateById(existing);
        return Result.success(existing);
    }

    /** country_code + name must be unique among active products. */
    private void assertUniqueCountryName(String countryCode, String name, Long excludeId) {
        if (countryCode == null || name == null || name.isBlank()) {
            return;
        }
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getCountryCode, countryCode)
                .eq(Product::getName, name.trim());
        if (excludeId != null) {
            q.ne(Product::getId, excludeId);
        }
        Long count = productMapper.selectCount(q);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "Product name \"" + name.trim() + "\" already exists for country " + countryCode);
        }
    }

    /**
     * Soft-deletes the product ({@code status=0}). Blocked while on-hand inventory quantity is positive.
     */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        Product existing = productMapper.selectById(id);
        if (existing == null) throw new BusinessException("Product not found");
        long positiveRows = inventoryMapper.selectCount(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getProductId, id)
                        .gt(Inventory::getQty, 0));
        if (positiveRows > 0) {
            throw new BusinessException(
                    "Cannot delete product with remaining stock. Please clear the inventory first.");
        }
        productMapper.deleteById(id);
        return Result.success();
    }

    private static String requireCountryCode(String countryCode) {
        return CountryEnum.ofCountryCode(countryCode)
                .map(CountryEnum::getCountryCode)
                .orElseThrow(() -> new BusinessException(
                        "Invalid country code. Allowed: " + CountryEnum.allowedCodesMessage()));
    }

    private static String normalizeOptionalCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return CountryEnum.ofCountryCode(countryCode)
                .map(CountryEnum::getCountryCode)
                .orElseThrow(() -> new BusinessException(
                        "Invalid country code. Allowed: " + CountryEnum.allowedCodesMessage()));
    }
}
