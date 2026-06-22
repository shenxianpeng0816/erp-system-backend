package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.CreateWarehouseRequest;
import com.erp.entity.Warehouse;
import com.erp.mapper.WarehouseMapper;
import com.erp.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private static final Set<String> ALLOWED_COUNTRY_CODES = Set.of("KE", "UG", "TZ");

    private final WarehouseMapper warehouseMapper;

    @Override
    public Warehouse requireActive(Long warehouseId) {
        if (warehouseId == null) {
            throw new BusinessException("Warehouse is required");
        }
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null || wh.getStatus() == null || wh.getStatus() != 1) {
            throw new BusinessException("Warehouse not found or inactive");
        }
        return wh;
    }

    @Override
    public Warehouse requireActiveForCountry(Long warehouseId, String countryCode) {
        Warehouse wh = requireActive(warehouseId);
        String cc = countryCode != null ? countryCode.trim().toUpperCase() : "";
        if (!cc.equals(wh.getCountryCode())) {
            throw new BusinessException(
                    "Warehouse " + wh.getName() + " is in " + wh.getCountryCode()
                            + " but order country is " + cc);
        }
        return wh;
    }

    @Override
    public Warehouse defaultForCountry(String countryCode) {
        String cc = countryCode != null ? countryCode.trim().toUpperCase() : "";
        Warehouse wh = warehouseMapper.selectOne(
                new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getCountryCode, cc)
                        .eq(Warehouse::getIsDefault, true)
                        .eq(Warehouse::getStatus, 1)
                        .orderByAsc(Warehouse::getId)
                        .last("LIMIT 1"));
        if (wh == null) {
            wh = warehouseMapper.selectOne(
                    new LambdaQueryWrapper<Warehouse>()
                            .eq(Warehouse::getCountryCode, cc)
                            .eq(Warehouse::getStatus, 1)
                            .orderByAsc(Warehouse::getId)
                            .last("LIMIT 1"));
        }
        if (wh == null) {
            throw new BusinessException("No active warehouse configured for country " + cc);
        }
        return wh;
    }

    @Override
    public List<Warehouse> listActive(String countryCode) {
        LambdaQueryWrapper<Warehouse> q = new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .orderByDesc(Warehouse::getIsDefault)
                .orderByAsc(Warehouse::getName);
        if (countryCode != null && !countryCode.isBlank()) {
            q.eq(Warehouse::getCountryCode, countryCode.trim().toUpperCase());
        }
        return warehouseMapper.selectList(q);
    }

    @Override
    public List<Warehouse> listAll(String countryCode) {
        LambdaQueryWrapper<Warehouse> q = new LambdaQueryWrapper<Warehouse>()
                .orderByDesc(Warehouse::getStatus)
                .orderByDesc(Warehouse::getIsDefault)
                .orderByAsc(Warehouse::getCountryCode)
                .orderByAsc(Warehouse::getName);
        if (countryCode != null && !countryCode.isBlank()) {
            q.eq(Warehouse::getCountryCode, countryCode.trim().toUpperCase());
        }
        return warehouseMapper.selectList(q);
    }

    @Override
    @Transactional
    public Warehouse create(CreateWarehouseRequest req) {
        if (req.getWarehouseCode() == null || req.getWarehouseCode().isBlank()) {
            throw new BusinessException("Warehouse code is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException("Warehouse name is required");
        }
        String cc = normalizeCountryCode(req.getCountryCode());
        String code = req.getWarehouseCode().trim().toUpperCase();

        long duplicate = warehouseMapper.selectCount(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getWarehouseCode, code));
        if (duplicate > 0) {
            throw new BusinessException("Warehouse code already exists: " + code);
        }

        String type = req.getType() != null && !req.getType().isBlank()
                ? req.getType().trim().toUpperCase() : "BRANCH";
        if (!"MAIN".equals(type) && !"BRANCH".equals(type)) {
            throw new BusinessException("Warehouse type must be MAIN or BRANCH");
        }

        boolean isDefault = Boolean.TRUE.equals(req.getIsDefault());
        if (isDefault) {
            clearDefaultForCountry(cc);
        }

        Warehouse wh = new Warehouse();
        wh.setWarehouseCode(code);
        wh.setName(req.getName().trim());
        wh.setCountryCode(cc);
        wh.setCity(req.getCity() != null && !req.getCity().isBlank() ? req.getCity().trim() : null);
        wh.setType(type);
        wh.setAddress(req.getAddress() != null && !req.getAddress().isBlank() ? req.getAddress().trim() : null);
        wh.setIsDefault(isDefault);
        wh.setStatus(1);
        warehouseMapper.insert(wh);
        return wh;
    }

    private void clearDefaultForCountry(String countryCode) {
        List<Warehouse> defaults = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getCountryCode, countryCode)
                        .eq(Warehouse::getIsDefault, true));
        for (Warehouse w : defaults) {
            w.setIsDefault(false);
            warehouseMapper.updateById(w);
        }
    }

    private static String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        String cc = countryCode.trim().toUpperCase();
        if (!ALLOWED_COUNTRY_CODES.contains(cc)) {
            throw new BusinessException("Invalid country code. Allowed: KE, UG, TZ");
        }
        return cc;
    }
}
