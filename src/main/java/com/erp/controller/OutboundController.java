package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.response.OutboundPrintData;
import com.erp.entity.OutboundItem;
import com.erp.entity.OutboundOrder;
import com.erp.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:outbound:list')")
    public Result<PageResult<OutboundOrder>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(outboundService.pageList(keyword, orderNo, warehouseId, page, size));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermi('erp:outbound:export')")
    public Result<List<OutboundOrder>> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(outboundService.export(keyword, orderNo, warehouseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:outbound:list')")
    public Result<OutboundOrder> detail(@PathVariable Long id) {
        return Result.success(outboundService.getDetail(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("@ss.hasPermi('erp:outbound:list')")
    public Result<List<OutboundItem>> items(@PathVariable Long id) {
        return Result.success(outboundService.listItems(id));
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("@ss.hasPermi('erp:outbound:print')")
    public Result<OutboundPrintData> printData(@PathVariable Long id) {
        return Result.success(outboundService.getPrintData(id));
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("@ss.hasPermi('erp:outbound:print')")
    public Result<OutboundOrder> print(@PathVariable Long id) {
        return Result.success(outboundService.markPrinted(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("@ss.hasPermi('erp:outbound:ship')")
    public Result<OutboundOrder> ship(@PathVariable Long id) {
        return Result.success(outboundService.ship(id));
    }
}
