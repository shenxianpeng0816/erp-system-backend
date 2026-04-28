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
import java.util.List;

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

        // Create initial approval flow entry (assign to any ADMIN)
        ApprovalFlow flow = new ApprovalFlow();
        flow.setOrderId(orderId);
        flow.setStep(1);
        flow.setApproverId(1L); // default admin; production: configurable
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
        return orderMapper.findAll();
    }

    @Override
    public List<SalesOrder> listMyOrders() {
        return orderMapper.findBySalesUser(SecurityUtil.currentUserId());
    }

    @Override
    public List<SalesOrder> listPendingApprovals() {
        return orderMapper.findPendingApproval();
    }

    @Override
    public SalesOrder getDetail(Long orderId) {
        return orderMapper.selectById(orderId);
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
