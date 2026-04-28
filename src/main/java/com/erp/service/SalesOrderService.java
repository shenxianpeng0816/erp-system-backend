package com.erp.service;

import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.SalesOrder;

import java.util.List;

public interface SalesOrderService {
    SalesOrder createOrder(CreateOrderRequest req);
    SalesOrder submitOrder(Long orderId);
    SalesOrder handleApproval(Long orderId, ApprovalRequest req);
    List<SalesOrder> listMyOrders();
    List<SalesOrder> listPendingApprovals();
    List<SalesOrder> listAllOrders();
    SalesOrder getDetail(Long orderId);
    SalesOrder confirmDelivery(Long orderId, String signImageUrl);
}
