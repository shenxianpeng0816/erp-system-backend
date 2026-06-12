package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CancelOrderRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.ApprovalFlow;
import com.erp.entity.SalesOrder;

import java.util.List;

public interface SalesOrderService {
    SalesOrder createOrder(CreateOrderRequest req);
    SalesOrder submitOrder(Long orderId);
    SalesOrder handleApproval(Long orderId, ApprovalRequest req);
    List<SalesOrder> listMyOrders();
    PageResult<SalesOrder> pageMyOrders(String keyword, String status, long page, long size);
    List<SalesOrder> listPendingApprovals();
    List<SalesOrder> listAllOrders();
    PageResult<SalesOrder> pageAllOrders(String keyword, String status, Long salesUserId, long page, long size);
    SalesOrder getDetail(Long orderId);
    /** Ensures the current user may read this order (used for items & approvals). */
    void assertOrderViewable(Long orderId);
    List<ApprovalFlow> listApprovalHistory(Long orderId);
    SalesOrder confirmDelivery(Long orderId, String signImageUrl);

    /** Deletes draft / pending / rejected orders (no invoice). Admin or order owner (sales). */
    void deleteOrder(Long orderId);

    /** Rewrites header + line items while status is {@code DRAFT} or {@code PENDING_APPROVAL}. Admin or order owner. */
    SalesOrder updatePendingOrder(Long orderId, CreateOrderRequest req);

    /** Cancel an approved/shipped order, void linked docs, restore inventory when shipped. */
    SalesOrder cancelOrder(Long orderId, CancelOrderRequest req);
}
