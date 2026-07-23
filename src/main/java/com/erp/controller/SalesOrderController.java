package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CancelOrderRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.ApprovalFlow;
import com.erp.entity.SalesOrder;
import com.erp.entity.SalesOrderItem;
import com.erp.mapper.SalesOrderItemMapper;
import com.erp.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService orderService;
    private final SalesOrderItemMapper itemMapper;

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:order:add')")
    public Result<SalesOrder> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.createOrder(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.hasPermi('erp:order:submit')")
    public Result<SalesOrder> submit(@PathVariable Long id) {
        return Result.success(orderService.submitOrder(id));
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("@ss.hasPermi('erp:order:approve')")
    public Result<SalesOrder> approve(@PathVariable Long id,
                                      @RequestBody ApprovalRequest req) {
        return Result.success(orderService.handleApproval(id, req));
    }

    @GetMapping("/mine")
    @PreAuthorize("@ss.hasPermi('erp:order:list:mine')")
    public Result<PageResult<SalesOrder>> mine(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.pageMyOrders(keyword, status, page, size));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:order:list:all')")
    public Result<PageResult<SalesOrder>> all(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long salesUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.pageAllOrders(keyword, status, salesUserId, page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermi('erp:order:pending')")
    public Result<List<SalesOrder>> pending() {
        return Result.success(orderService.listPendingApprovals());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermi('erp:order:query,erp:order:list:mine,erp:order:list:all,erp:order:pending')")
    public Result<SalesOrder> detail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("@ss.hasAnyPermi('erp:order:query,erp:order:list:mine,erp:order:list:all,erp:order:pending')")
    public Result<List<SalesOrderItem>> items(@PathVariable Long id) {
        orderService.assertOrderViewable(id);
        return Result.success(itemMapper.findWithProductByOrderId(id));
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("@ss.hasAnyPermi('erp:order:query,erp:order:list:mine,erp:order:list:all,erp:order:pending')")
    public Result<List<ApprovalFlow>> approvalHistory(@PathVariable Long id) {
        return Result.success(orderService.listApprovalHistory(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPermi('erp:order:confirm')")
    public Result<SalesOrder> confirm(@PathVariable Long id,
                                      @RequestParam(required = false) String signImageUrl) {
        return Result.success(orderService.confirmDelivery(id, signImageUrl));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:order:edit')")
    public Result<SalesOrder> update(@PathVariable Long id,
                                     @Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.updatePendingOrder(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermi('erp:order:cancel')")
    public Result<SalesOrder> cancel(@PathVariable Long id,
                                     @RequestBody(required = false) CancelOrderRequest req) {
        return Result.success(orderService.cancelOrder(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:order:remove')")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success();
    }
}
