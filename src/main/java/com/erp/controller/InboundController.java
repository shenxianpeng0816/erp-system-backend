package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.request.CreateInboundRequest;
import com.erp.dto.request.RejectInboundRequest;
import com.erp.dto.response.InboundFormOptions;
import com.erp.entity.InboundItem;
import com.erp.entity.InboundOrder;
import com.erp.service.InboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    @GetMapping("/form-options")
    @PreAuthorize("@ss.hasPermi('erp:inbound:list')")
    public Result<InboundFormOptions> formOptions(@RequestParam(required = false) Long warehouseId) {
        return Result.success(inboundService.getFormOptions(warehouseId));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:inbound:list')")
    public Result<PageResult<InboundOrder>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(inboundService.pageList(warehouseId, page, size));
    }

    /** Export all inbound orders (no pagination). */
    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermi('erp:inbound:export')")
    public Result<List<InboundOrder>> export(@RequestParam(required = false) Long warehouseId) {
        return Result.success(inboundService.export(warehouseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:inbound:query')")
    public Result<InboundOrder> detail(@PathVariable Long id) {
        return Result.success(inboundService.getDetail(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("@ss.hasPermi('erp:inbound:query')")
    public Result<List<InboundItem>> items(@PathVariable Long id) {
        return Result.success(inboundService.listItems(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:inbound:add')")
    public Result<InboundOrder> create(@RequestBody CreateInboundRequest req) {
        return Result.success(inboundService.create(req));
    }

    /** Confirm inbound → add to inventory + write log */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('erp:inbound:confirm')")
    public Result<InboundOrder> confirm(@PathVariable Long id) {
        return Result.success(inboundService.confirm(id));
    }

    /** Reject draft — closes order without inventory change */
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermi('erp:inbound:reject')")
    public Result<InboundOrder> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectInboundRequest body) {
        return Result.success(inboundService.reject(id, body));
    }

    /** Admin only — cannot delete confirmed (stock already applied). */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:inbound:remove')")
    public Result<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:inbound:edit')")
    public Result<InboundOrder> update(@PathVariable Long id, @RequestBody CreateInboundRequest req) {
        return Result.success(inboundService.update(id, req));
    }
}
