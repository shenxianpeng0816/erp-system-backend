package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.result.Result;
import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.ApprovalFlow;
import com.erp.entity.SalesOrder;
import com.erp.entity.SalesOrderItem;
import com.erp.mapper.ApprovalFlowMapper;
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
    private final ApprovalFlowMapper approvalFlowMapper;

    /** Sales: create draft order */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<SalesOrder> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.createOrder(req));
    }

    /** Sales: submit draft for approval */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<SalesOrder> submit(@PathVariable Long id) {
        return Result.success(orderService.submitOrder(id));
    }

    /** Admin: approve / reject / redirect */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SalesOrder> approve(@PathVariable Long id,
                                      @RequestBody ApprovalRequest req) {
        return Result.success(orderService.handleApproval(id, req));
    }

    /** Sales: my orders */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<List<SalesOrder>> mine() {
        return Result.success(orderService.listMyOrders());
    }

    /** Admin: all orders (full list for PC management view) */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','WAREHOUSE')")
    public Result<List<SalesOrder>> all() {
        return Result.success(orderService.listAllOrders());
    }

    /** Admin: pending approvals only */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SalesOrder>> pending() {
        return Result.success(orderService.listPendingApprovals());
    }

    /** Detail */
    @GetMapping("/{id}")
    public Result<SalesOrder> detail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    /** Order items — includes product name/spec/unit via JOIN */
    @GetMapping("/{id}/items")
    public Result<List<SalesOrderItem>> items(@PathVariable Long id) {
        return Result.success(itemMapper.findWithProductByOrderId(id));
    }

    /** Approval history for an order */
    @GetMapping("/{id}/approvals")
    public Result<List<ApprovalFlow>> approvalHistory(@PathVariable Long id) {
        return Result.success(approvalFlowMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getOrderId, id)
                        .orderByAsc(ApprovalFlow::getStep)));
    }

    /** Sales: confirm delivery + upload sign image URL */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<SalesOrder> confirm(@PathVariable Long id,
                                      @RequestParam(required = false) String signImageUrl) {
        return Result.success(orderService.confirmDelivery(id, signImageUrl));
    }
}
