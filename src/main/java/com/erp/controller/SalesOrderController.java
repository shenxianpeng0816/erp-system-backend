package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.request.ApprovalRequest;
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

    /** Sales: create draft order */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<SalesOrder> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.createOrder(req));
    }

    /** Creator submits draft (owner check in service) */
    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
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

    /** Sales: my orders (paginated) */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<PageResult<SalesOrder>> mine(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.pageMyOrders(keyword, status, page, size));
    }

    /** Admin + warehouse + inbound: full list (paginated). FINANCE uses /finance; SALES uses /orders/mine. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<PageResult<SalesOrder>> all(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long salesUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.pageAllOrders(keyword, status, salesUserId, page, size));
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
        orderService.assertOrderViewable(id);
        return Result.success(itemMapper.findWithProductByOrderId(id));
    }

    /** Approval history for an order */
    @GetMapping("/{id}/approvals")
    public Result<List<ApprovalFlow>> approvalHistory(@PathVariable Long id) {
        return Result.success(orderService.listApprovalHistory(id));
    }

    /** Sales: confirm delivery + upload sign image URL */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SALES','ADMIN')")
    public Result<SalesOrder> confirm(@PathVariable Long id,
                                      @RequestParam(required = false) String signImageUrl) {
        return Result.success(orderService.confirmDelivery(id, signImageUrl));
    }

    /** Admin or sales owner: update draft or pending-approval order */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES')")
    public Result<SalesOrder> update(@PathVariable Long id,
                                     @Valid @RequestBody CreateOrderRequest req) {
        return Result.success(orderService.updatePendingOrder(id, req));
    }

    /** Admin only (same as order approval): delete draft / pending / rejected orders */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success();
    }
}
