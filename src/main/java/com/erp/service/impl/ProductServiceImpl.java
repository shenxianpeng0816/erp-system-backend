package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.CountryEnum;
import com.erp.common.exception.BusinessException;
import com.erp.entity.Inventory;
import com.erp.entity.Product;
import com.erp.entity.Warehouse;
import com.erp.mapper.InventoryMapper;
import com.erp.mapper.ProductMapper;
import com.erp.mapper.WarehouseMapper;
import com.erp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;

    @Override
    public List<Product> listProducts(Long warehouseId, String countryCode) {
        String cc = resolveProductCountryFilter(warehouseId, countryCode);
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getProductNo);
        if (cc != null) {
            q.eq(Product::getCountryCode, cc);
        }
        List<Product> products = productMapper.selectList(q);
        enrichProductStock(products, warehouseId);
        return products;
    }

    @Override
    public Product create(Product product) {
        product.setCountryCode(requireCountryCode(product.getCountryCode()));
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BusinessException("Product name is required");
        }
        product.setName(product.getName().trim());
        assertUniqueCountryName(product.getCountryCode(), product.getName(), null);
        product.setStatus(1);
        productMapper.insert(product);
        return product;
    }

    @Override
    public Product update(Long id, Product body) {
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
        return existing;
    }

    @Override
    public void delete(Long id) {
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
