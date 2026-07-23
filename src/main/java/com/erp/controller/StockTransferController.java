package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.request.CreateStockTransferRequest;
import com.erp.entity.StockTransfer;
import com.erp.entity.StockTransferItem;
import com.erp.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:transfer:list')")
    public Result<PageResult<StockTransfer>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(stockTransferService.pageList(warehouseId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:transfer:list')")
    public Result<StockTransfer> detail(@PathVariable Long id) {
        return Result.success(stockTransferService.getDetail(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("@ss.hasPermi('erp:transfer:list')")
    public Result<List<StockTransferItem>> items(@PathVariable Long id) {
        return Result.success(stockTransferService.listItems(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:transfer:add')")
    public Result<StockTransfer> create(@RequestBody CreateStockTransferRequest req) {
        return Result.success(stockTransferService.create(req));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('erp:transfer:confirm')")
    public Result<StockTransfer> confirm(@PathVariable Long id) {
        return Result.success(stockTransferService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermi('erp:transfer:cancel')")
    public Result<StockTransfer> cancel(@PathVariable Long id) {
        return Result.success(stockTransferService.cancel(id));
    }
}
