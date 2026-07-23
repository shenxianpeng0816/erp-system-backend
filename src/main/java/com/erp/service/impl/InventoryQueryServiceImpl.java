package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.CountryEnum;
import com.erp.common.exception.BusinessException;
import com.erp.dto.response.InventoryFormOptions;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;
import com.erp.entity.Product;
import com.erp.entity.Warehouse;
import com.erp.mapper.InventoryLogMapper;
import com.erp.mapper.InventoryMapper;
import com.erp.mapper.ProductMapper;
import com.erp.mapper.WarehouseMapper;
import com.erp.service.InventoryQueryService;
import com.erp.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseService warehouseService;

    @Override
    public InventoryFormOptions getFormOptions(String countryCode) {
        return new InventoryFormOptions(warehouseService.listActive(countryCode));
    }

    @Override
    public List<Inventory> list(Long warehouseId, String countryCode) {
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
        return rows;
    }

    @Override
    public List<Inventory> alerts(Long warehouseId, String countryCode) {
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
        return rows;
    }

    @Override
    public List<InventoryLog> logs(Long productId, Long warehouseId) {
        LambdaQueryWrapper<InventoryLog> q = new LambdaQueryWrapper<InventoryLog>()
                .eq(InventoryLog::getProductId, productId)
                .orderByDesc(InventoryLog::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InventoryLog::getWarehouseId, warehouseId);
        }
        List<InventoryLog> logs = inventoryLogMapper.selectList(q);
        enrichLogWarehouseNames(logs);
        return logs;
    }

    @Override
    public List<InventoryLog> logsByRef(Long refId, String refType) {
        List<InventoryLog> logs = inventoryLogMapper.findByRef(refId, refType);
        enrichLogWarehouseNames(logs);
        return logs;
    }

    @Override
    public List<InventoryLog> transactionLogs(Long warehouseId, String productName) {
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
                return List.of();
            }
            q.in(InventoryLog::getProductId, productIds);
        }
        List<InventoryLog> logs = inventoryLogMapper.selectList(q);
        enrichLogWarehouseNames(logs);
        return logs;
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
