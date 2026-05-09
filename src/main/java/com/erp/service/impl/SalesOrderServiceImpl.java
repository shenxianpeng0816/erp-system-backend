package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.DocSequenceService;
import com.erp.service.SalesOrderService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper itemMapper;
    private final ApprovalFlowMapper approvalMapper;
    private final InvoiceMapper invoiceMapper;
    private final OutboundOrderMapper outboundMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final InventoryMapper inventoryMapper;
    private final ReceivableMapper receivableMapper;
    private final DocSequenceService docSequenceService;
    private final UserMapper userMapper;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public SalesOrder createOrder(CreateOrderRequest req) {
        SalesOrder order = new SalesOrder();
        order.setOrderNo(docSequenceService.nextDocNo("SO"));
        order.setSalesUserId(SecurityUtil.currentUserId());
        order.setShipToCustomerId(req.getShipToCustomerId());
        order.setBillToCustomerId(req.getBillToCustomerId());
        order.setPaymentMethod(req.getPaymentMethod());
        order.setPriceTerm(req.getPriceTerm());
        order.setValidityDays(req.getValidityDays() != null ? req.getValidityDays() : 30);
        order.setRemark(req.getRemark());

        boolean etr = Boolean.TRUE.equals(req.getEtrRequired());
        order.setEtrRequired(etr);
        if (etr) {
            if (req.getEtrCompanyName() == null || req.getEtrCompanyName().isBlank()) {
                throw new BusinessException("Company name is required when ETR is required");
            }
            if (req.getEtrCompanyKraPin() == null || req.getEtrCompanyKraPin().isBlank()) {
                throw new BusinessException("Company KRA PIN is required when ETR is required");
            }
            order.setEtrCompanyName(req.getEtrCompanyName().trim());
            order.setEtrCompanyKraPin(req.getEtrCompanyKraPin().trim());
        } else {
            order.setEtrCompanyName(null);
            order.setEtrCompanyKraPin(null);
        }

        order.setStatus("DRAFT");

        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);

        for (CreateOrderRequest.OrderItemRequest i : req.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(i.getProductId());
            item.setQty(i.getQty());
            item.setUnitPrice(i.getUnitPrice());
            BigDecimal lineTotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQty()));
            item.setTotal(lineTotal);
            item.setRemark(i.getRemark());
            itemMapper.insert(item);
            total = total.add(lineTotal);
        }

        order.setTotalAmount(total);
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public SalesOrder submitOrder(Long orderId) {
        SalesOrder order = getAndValidateOwner(orderId);
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("Only DRAFT orders can be submitted");
        order.setStatus("PENDING_APPROVAL");
        orderMapper.updateById(order);

        User defaultApprover = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "ADMIN")
                        .orderByAsc(User::getId)
                        .last("LIMIT 1"));
        if (defaultApprover == null) {
            throw new BusinessException(
                    "No active ADMIN user found. Create at least one admin account before submitting orders for approval.");
        }

        ApprovalFlow flow = new ApprovalFlow();
        flow.setOrderId(orderId);
        flow.setStep(1);
        flow.setApproverId(defaultApprover.getId());
        flow.setStatus("PENDING");
        approvalMapper.insert(flow);
        return order;
    }

    @Override
    @Transactional
    public SalesOrder handleApproval(Long orderId, ApprovalRequest req) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!"PENDING_APPROVAL".equals(order.getStatus()))
            throw new BusinessException("Order is not pending approval");

        ApprovalFlow flow = approvalMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getOrderId, orderId)
                        .eq(ApprovalFlow::getStatus, "PENDING")
                        .orderByDesc(ApprovalFlow::getStep)
                        .last("LIMIT 1"));
        if (flow == null) throw new BusinessException("No pending approval step found");

        flow.setActedAt(LocalDateTime.now());
        flow.setComment(req.getComment());

        switch (req.getAction().toUpperCase()) {
            case "APPROVE" -> {
                flow.setStatus("APPROVED");
                order.setStatus("APPROVED");
                // Auto-generate Invoice and Outbound Order
                generateInvoice(order);
                generateOutboundOrder(order);
            }
            case "REJECT" -> {
                flow.setStatus("REJECTED");
                order.setStatus("REJECTED");
                order.setRejectReason(req.getComment());
            }
            case "REDIRECT" -> {
                if (req.getRedirectTo() == null) throw new BusinessException("Redirect target user is required");
                flow.setStatus("REDIRECTED");
                flow.setRedirectTo(req.getRedirectTo());
                // Create next step
                ApprovalFlow next = new ApprovalFlow();
                next.setOrderId(orderId);
                next.setStep(flow.getStep() + 1);
                next.setApproverId(req.getRedirectTo());
                next.setStatus("PENDING");
                approvalMapper.insert(next);
            }
        }

        approvalMapper.updateById(flow);
        orderMapper.updateById(order);
        return order;
    }

    @Override
    public List<SalesOrder> listAllOrders() {
        List<SalesOrder> list = orderMapper.findAll();
        enrichCustomerNames(list);
        return list;
    }

    @Override
    public List<SalesOrder> listMyOrders() {
        List<SalesOrder> list = orderMapper.findBySalesUser(SecurityUtil.currentUserId());
        enrichCustomerNames(list);
        return list;
    }

    @Override
    public List<SalesOrder> listPendingApprovals() {
        List<SalesOrder> list = orderMapper.findPendingApproval();
        enrichCustomerNames(list);
        return list;
    }

    @Override
    public SalesOrder getDetail(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        assertCanViewOrder(order);
        enrichCustomerNames(List.of(order));
        return order;
    }

    /** Fills {@code shipToCustomerName} / {@code billToCustomerName} for list and detail APIs. */
    private void enrichCustomerNames(List<SalesOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (SalesOrder o : orders) {
            if (o.getShipToCustomerId() != null) {
                ids.add(o.getShipToCustomerId());
            }
            if (o.getBillToCustomerId() != null) {
                ids.add(o.getBillToCustomerId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<Customer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<Customer>().in(Customer::getId, ids));
        Map<Long, String> names = new HashMap<>();
        for (Customer c : customers) {
            names.put(c.getId(), c.getName());
        }
        for (SalesOrder o : orders) {
            if (o.getShipToCustomerId() != null) {
                o.setShipToCustomerName(names.get(o.getShipToCustomerId()));
            }
            if (o.getBillToCustomerId() != null) {
                o.setBillToCustomerName(names.get(o.getBillToCustomerId()));
            }
        }
    }

    @Override
    public void assertOrderViewable(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        assertCanViewOrder(order);
    }

    /**
     * SALES: only own orders. ADMIN: all. FINANCE: post-approval pipeline (e.g. from invoice).
     * WAREHOUSE: all (ops / outbound). Others: no access.
     */
    private void assertCanViewOrder(SalesOrder order) {
        String role = SecurityUtil.currentRole();
        Long uid = SecurityUtil.currentUserId();
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("SALES".equals(role)) {
            if (!order.getSalesUserId().equals(uid)) {
                throw new BusinessException("Access denied");
            }
            return;
        }
        if ("FINANCE".equals(role)) {
            if (isFinanceViewableStatus(order.getStatus())) {
                return;
            }
            throw new BusinessException("Access denied");
        }
        if ("WAREHOUSE".equals(role)) {
            return;
        }
        throw new BusinessException("Access denied");
    }

    @Override
    public List<ApprovalFlow> listApprovalHistory(Long orderId) {
        assertOrderViewable(orderId);
        List<ApprovalFlow> flows = approvalMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getOrderId, orderId)
                        .orderByAsc(ApprovalFlow::getStep));
        if (flows.isEmpty()) {
            return flows;
        }
        Set<Long> userIds = new HashSet<>();
        for (ApprovalFlow f : flows) {
            if (f.getApproverId() != null) {
                userIds.add(f.getApproverId());
            }
            if (f.getRedirectTo() != null) {
                userIds.add(f.getRedirectTo());
            }
        }
        if (userIds.isEmpty()) {
            return flows;
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> idToDisplay = new HashMap<>();
        for (User u : users) {
            if (u != null && u.getId() != null) {
                idToDisplay.put(u.getId(), userDisplayName(u));
            }
        }
        for (ApprovalFlow f : flows) {
            if (f.getApproverId() != null) {
                f.setApproverName(idToDisplay.get(f.getApproverId()));
            }
            if (f.getRedirectTo() != null) {
                f.setRedirectToName(idToDisplay.get(f.getRedirectTo()));
            }
        }
        return flows;
    }

    /** After approval: finance can open order (e.g. linked from Finance module). */
    private static boolean isFinanceViewableStatus(String status) {
        if (status == null) return false;
        return "APPROVED".equals(status) || "SHIPPED".equals(status) || "CONFIRMED".equals(status);
    }

    @Override
    @Transactional
    public SalesOrder confirmDelivery(Long orderId, String signImageUrl) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!"SHIPPED".equals(order.getStatus())) throw new BusinessException("Order has not been shipped yet");
        order.setStatus("CONFIRMED");
        order.setSignImageUrl(signImageUrl);
        order.setConfirmedBy(SecurityUtil.currentUserId());
        order.setConfirmedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static String userDisplayName(User u) {
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            return u.getRealName().trim();
        }
        return u.getUsername() != null ? u.getUsername() : "";
    }

    private SalesOrder getAndValidateOwner(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!order.getSalesUserId().equals(SecurityUtil.currentUserId()))
            throw new BusinessException("Access denied");
        return order;
    }

    private void generateInvoice(SalesOrder order) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(docSequenceService.nextDocNo("INV"));
        invoice.setOrderId(order.getId());
        invoice.setBillToCustomerId(order.getBillToCustomerId());
        invoice.setAmount(order.getTotalAmount());
        invoice.setStatus("PENDING");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setPaymentMethod(order.getPaymentMethod());
        invoiceMapper.insert(invoice);

        // Create matching receivable
        Receivable rec = new Receivable();
        rec.setInvoiceId(invoice.getId());
        rec.setCustomerId(order.getBillToCustomerId());
        rec.setAmount(order.getTotalAmount());
        rec.setReceivedAmount(BigDecimal.ZERO);
        rec.setBalance(order.getTotalAmount());
        rec.setDueDate(invoice.getDueDate());
        rec.setStatus("OUTSTANDING");
        receivableMapper.insert(rec);
    }

    private void generateOutboundOrder(SalesOrder order) {
        OutboundOrder dn = new OutboundOrder();
        dn.setOutboundNo(docSequenceService.nextDocNo("DN"));
        dn.setOrderId(order.getId());
        dn.setShipToCustomerId(order.getShipToCustomerId());
        dn.setStatus("PENDING");
        outboundMapper.insert(dn);

        // Copy items from sales order to outbound
        List<SalesOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getOrderId, order.getId()));
        for (SalesOrderItem si : items) {
            OutboundItem oi = new OutboundItem();
            oi.setOutboundId(dn.getId());
            oi.setProductId(si.getProductId());
            oi.setQty(si.getQty());
            outboundItemMapper.insert(oi);
        }
    }
}
