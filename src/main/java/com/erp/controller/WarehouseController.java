package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.dto.request.CreateWarehouseRequest;
import com.erp.entity.Warehouse;
import com.erp.service.WarehouseService;
import com.erp.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * List warehouses for operational use (active only).
     * Admin may pass {@code all=true} to include inactive warehouses.
     */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:warehouse:list')")
    public Result<List<Warehouse>> list(
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "false") boolean all) {
        if (all && !"ADMIN".equals(SecurityUtil.currentRole())) {
            all = false;
        }
        if (all) {
            return Result.success(warehouseService.listAll(countryCode));
        }
        return Result.success(warehouseService.listActive(countryCode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:warehouse:list')")
    public Result<Warehouse> detail(@PathVariable Long id) {
        return Result.success(warehouseService.requireActive(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:warehouse:add')")
    public Result<Warehouse> create(@Valid @RequestBody CreateWarehouseRequest req) {
        return Result.success(warehouseService.create(req));
    }
}
