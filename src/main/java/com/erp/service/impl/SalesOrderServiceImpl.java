package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.enums.CountryEnum;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.ApprovalRequest;
import com.erp.dto.request.CancelOrderRequest;
import com.erp.dto.request.CreateOrderRequest;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.DocSequenceService;
import com.erp.service.InventoryService;
import com.erp.service.SalesOrderService;
import com.erp.service.StockChangeContext;
import com.erp.service.WarehouseService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final InventoryService inventoryService;
    private final ReceivableMapper receivableMapper;
    private final DocSequenceService docSequenceService;
    private final UserMapper userMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final WarehouseService warehouseService;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional
    public SalesOrder createOrder(CreateOrderRequest req) {
        SalesOrder order = new SalesOrder();
        order.setOrderNo(docSequenceService.nextDocNo("SO"));
        order.setSalesUserId(SecurityUtil.currentUserId());
        order.setShipToCustomerId(req.getShipToCustomerId());
        order.setBillToCustomerId(req.getBillToCustomerId());
        order.setCountryCode(normalizeCountryCode(req.getCountryCode()));
        Warehouse warehouse = warehouseService.requireActiveForCountry(
                req.getWarehouseId(), order.getCountryCode());
        order.setShipFromWarehouseId(warehouse.getId());
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

        validateOrderItemsStock(req.getItems(), warehouse.getId());

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
    public PageResult<SalesOrder> pageAllOrders(String keyword, String status, Long salesUserId,
                                                long page, long size) {
        LambdaQueryWrapper<SalesOrder> q = new LambdaQueryWrapper<SalesOrder>()
                .orderByDesc(SalesOrder::getCreatedAt);
        applyOrderListFilters(q, keyword, status, salesUserId, null);
        Page<SalesOrder> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<SalesOrder> result = orderMapper.selectPage(p, q);
        enrichCustomerNames(result.getRecords());
        return PageQuery.from(result);
    }

    @Override
    public List<SalesOrder> listMyOrders() {
        List<SalesOrder> list = orderMapper.findBySalesUser(SecurityUtil.currentUserId());
        enrichCustomerNames(list);
        return list;
    }

    @Override
    public PageResult<SalesOrder> pageMyOrders(String keyword, String status, long page, long size) {
        LambdaQueryWrapper<SalesOrder> q = new LambdaQueryWrapper<SalesOrder>()
                .orderByDesc(SalesOrder::getCreatedAt);
        applyOrderListFilters(q, keyword, status, null, SecurityUtil.currentUserId());
        Page<SalesOrder> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<SalesOrder> result = orderMapper.selectPage(p, q);
        enrichCustomerNames(result.getRecords());
        return PageQuery.from(result);
    }

    private static void applyOrderListFilters(LambdaQueryWrapper<SalesOrder> q,
                                              String keyword,
                                              String status,
                                              Long salesUserId,
                                              Long ownerUserId) {
        if (ownerUserId != null) {
            q.eq(SalesOrder::getSalesUserId, ownerUserId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(SalesOrder::getOrderNo, keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            q.eq(SalesOrder::getStatus, status.trim());
        }
        if (salesUserId != null) {
            q.eq(SalesOrder::getSalesUserId, salesUserId);
        }
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

    /** Fills display names for list and detail APIs (salesperson, ship-to, bill-to). */
    private void enrichCustomerNames(List<SalesOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> salesUserIds = new HashSet<>();
        Set<Long> warehouseIds = new HashSet<>();
        for (SalesOrder o : orders) {
            if (o.getShipToCustomerId() != null) {
                customerIds.add(o.getShipToCustomerId());
            }
            if (o.getBillToCustomerId() != null) {
                customerIds.add(o.getBillToCustomerId());
            }
            if (o.getSalesUserId() != null) {
                salesUserIds.add(o.getSalesUserId());
            }
            if (o.getShipFromWarehouseId() != null) {
                warehouseIds.add(o.getShipFromWarehouseId());
            }
        }
        Map<Long, String> customerNames = new HashMap<>();
        Map<Long, String> shipToShopNames = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>().in(Customer::getId, customerIds));
            for (Customer c : customers) {
                customerNames.put(c.getId(), c.getName());
                shipToShopNames.put(c.getId(), customerShopDisplayName(c));
            }
        }
        Map<Long, String> salesUserNames = new HashMap<>();
        if (!salesUserIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(salesUserIds);
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    salesUserNames.put(u.getId(), userDisplayName(u));
                }
            }
        }
        Map<Long, String> warehouseNames = new HashMap<>();
        if (!warehouseIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, warehouseIds));
            for (Warehouse w : warehouses) {
                warehouseNames.put(w.getId(), w.getName());
            }
        }
        for (SalesOrder o : orders) {
            if (o.getSalesUserId() != null) {
                o.setSalesUserName(salesUserNames.get(o.getSalesUserId()));
            }
            if (o.getShipToCustomerId() != null) {
                o.setShipToCustomerName(shipToShopNames.get(o.getShipToCustomerId()));
            }
            if (o.getBillToCustomerId() != null) {
                o.setBillToCustomerName(customerNames.get(o.getBillToCustomerId()));
            }
            if (o.getShipFromWarehouseId() != null) {
                o.setShipFromWarehouseName(warehouseNames.get(o.getShipFromWarehouseId()));
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
     * WAREHOUSE / INBOUND: all (ops / outbound / inbound). Others: no access.
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
        if ("WAREHOUSE".equals(role) || "INBOUND".equals(role)) {
            return;
        }
        throw new BusinessException("Access denied");
    }

    /** Admin may modify any order; sales may modify only own orders. */
    private void assertAdminOrOrderOwner(SalesOrder order) {
        String role = SecurityUtil.currentRole();
        Long uid = SecurityUtil.currentUserId();
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("SALES".equals(role) && order.getSalesUserId().equals(uid)) {
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

    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!"ADMIN".equals(SecurityUtil.currentRole())) {
            throw new BusinessException("Access denied");
        }
        String st = order.getStatus();
        if (!"DRAFT".equals(st) && !"PENDING_APPROVAL".equals(st) && !"REJECTED".equals(st)) {
            throw new BusinessException("Only draft, pending approval, or rejected orders can be deleted");
        }
        approvalMapper.delete(
                new LambdaQueryWrapper<ApprovalFlow>().eq(ApprovalFlow::getOrderId, orderId));
        orderMapper.deleteById(orderId);
    }

    @Override
    @Transactional
    public SalesOrder updatePendingOrder(Long orderId, CreateOrderRequest req) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!"PENDING_APPROVAL".equals(order.getStatus()) && !"DRAFT".equals(order.getStatus())) {
            throw new BusinessException("Only draft or pending approval orders can be edited");
        }
        assertAdminOrOrderOwner(order);

        order.setShipToCustomerId(req.getShipToCustomerId());
        order.setBillToCustomerId(req.getBillToCustomerId());
        order.setCountryCode(normalizeCountryCode(req.getCountryCode()));
        Warehouse warehouse = warehouseService.requireActiveForCountry(
                req.getWarehouseId(), order.getCountryCode());
        order.setShipFromWarehouseId(warehouse.getId());
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

        itemMapper.delete(
                new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getOrderId, orderId));

        validateOrderItemsStock(req.getItems(), warehouse.getId());

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest i : req.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
            item.setOrderId(orderId);
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
        enrichCustomerNames(List.of(order));
        return order;
    }

    @Override
    @Transactional
    public SalesOrder cancelOrder(Long orderId, CancelOrderRequest req) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");

        String role = SecurityUtil.currentRole();
        Long uid = SecurityUtil.currentUserId();
        if ("ADMIN".equals(role)) {
            // allowed
        } else if ("SALES".equals(role) && order.getSalesUserId().equals(uid)) {
            // allowed
        } else {
            throw new BusinessException("Access denied");
        }

        String status = order.getStatus();
        if ("CANCELLED".equals(status)) {
            throw new BusinessException("Order is already cancelled");
        }
        if (!"APPROVED".equals(status) && !"SHIPPED".equals(status)) {
            throw new BusinessException("Only approved or shipped orders can be cancelled");
        }

        assertNoPaymentsReceived(orderId);

        List<OutboundOrder> outbounds = outboundMapper.selectList(
                new LambdaQueryWrapper<OutboundOrder>().eq(OutboundOrder::getOrderId, orderId));
        for (OutboundOrder dn : outbounds) {
            if ("CANCELLED".equals(dn.getStatus())) continue;
            if ("SHIPPED".equals(dn.getStatus())) {
                restoreInventoryForOutbound(dn);
            } else if (!"PENDING".equals(dn.getStatus()) && !"PRINTED".equals(dn.getStatus())) {
                throw new BusinessException("Cannot cancel order: outbound " + dn.getOutboundNo()
                        + " is in status " + dn.getStatus());
            }
            dn.setStatus("CANCELLED");
            outboundMapper.updateById(dn);
        }

        List<Invoice> invoices = invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getOrderId, orderId));
        for (Invoice inv : invoices) {
            if ("CANCELLED".equals(inv.getStatus())) continue;
            inv.setStatus("CANCELLED");
            invoiceMapper.updateById(inv);

            List<Receivable> recs = receivableMapper.selectList(
                    new LambdaQueryWrapper<Receivable>().eq(Receivable::getInvoiceId, inv.getId()));
            for (Receivable rec : recs) {
                if ("CANCELLED".equals(rec.getStatus())) continue;
                rec.setStatus("CANCELLED");
                rec.setBalance(BigDecimal.ZERO);
                receivableMapper.updateById(rec);
            }
        }

        order.setStatus("CANCELLED");
        String reason = req != null && req.getReason() != null ? req.getReason().trim() : "";
        if (!reason.isEmpty()) {
            String prefix = order.getRemark() != null && !order.getRemark().isBlank()
                    ? order.getRemark().trim() + "\n" : "";
            order.setRemark(prefix + "Cancelled: " + reason);
        }
        orderMapper.updateById(order);
        enrichCustomerNames(List.of(order));
        return order;
    }

    private void assertNoPaymentsReceived(Long orderId) {
        List<Invoice> invoices = invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getOrderId, orderId));
        for (Invoice inv : invoices) {
            if ("PAID".equals(inv.getStatus()) || "PARTIAL".equals(inv.getStatus())) {
                throw new BusinessException("Cannot cancel: invoice " + inv.getInvoiceNo() + " has payments recorded");
            }
            List<Receivable> recs = receivableMapper.selectList(
                    new LambdaQueryWrapper<Receivable>().eq(Receivable::getInvoiceId, inv.getId()));
            for (Receivable rec : recs) {
                if (rec.getReceivedAmount() != null
                        && rec.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
                    throw new BusinessException("Cannot cancel: receivable has received payments");
                }
            }
        }
    }

    private void restoreInventoryForOutbound(OutboundOrder dn) {
        Long operatorId = SecurityUtil.currentUserId();
        Long warehouseId = dn.getWarehouseId();
        if (warehouseId == null) {
            SalesOrder order = orderMapper.selectById(dn.getOrderId());
            if (order != null) {
                warehouseId = order.getShipFromWarehouseId();
            }
        }
        if (warehouseId == null) {
            throw new BusinessException("Cannot restore stock: outbound has no warehouse");
        }

        List<OutboundItem> items = outboundItemMapper.selectList(
                new LambdaQueryWrapper<OutboundItem>().eq(OutboundItem::getOutboundId, dn.getId()));
        for (OutboundItem item : items) {
            inventoryService.addStock(
                    warehouseId,
                    item.getProductId(),
                    item.getQty(),
                    new StockChangeContext(
                            "ADJUST", dn.getId(), "ORDER_CANCEL", operatorId,
                            "Order cancel restore: " + dn.getOutboundNo()));
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateOrderItemsStock(List<CreateOrderRequest.OrderItemRequest> items, Long warehouseId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Order must contain at least one line item");
        }
        if (warehouseId == null) {
            throw new BusinessException("Ship-from warehouse is required");
        }
        Warehouse warehouse = warehouseService.requireActive(warehouseId);
        for (CreateOrderRequest.OrderItemRequest i : items) {
            if (i.getProductId() == null) {
                throw new BusinessException("Each line item must have a product");
            }
            if (i.getQty() == null || i.getQty() < 1) {
                throw new BusinessException("Each line item must have quantity at least 1");
            }
            Product product = productMapper.selectById(i.getProductId());
            String productLabel = product != null
                    ? product.getProductNo() + " — " + product.getName()
                    : "Product #" + i.getProductId();

            int available = inventoryService.getAvailableQty(warehouseId, i.getProductId());
            if (available <= 0) {
                throw new BusinessException("No available stock at " + warehouse.getName() + " for " + productLabel);
            }
            if (i.getQty() > available) {
                throw new BusinessException(
                        "Insufficient stock at " + warehouse.getName() + " for " + productLabel
                                + ". Available: " + available);
            }
        }
    }

    private static String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("Country code is required");
        }
        return CountryEnum.ofCountryCode(countryCode)
                .map(CountryEnum::getCountryCode)
                .orElseThrow(() -> new BusinessException(
                        "Invalid country code. Allowed: " + CountryEnum.allowedCodesMessage()));
    }

    private static String userDisplayName(User u) {
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            return u.getRealName().trim();
        }
        return u.getUsername() != null ? u.getUsername() : "";
    }

    /** Ship-to list/detail label: prefer shop/company name, fall back to owner name. */
    private static String customerShopDisplayName(Customer c) {
        if (c.getShopName() != null && !c.getShopName().isBlank()) {
            return c.getShopName().trim();
        }
        return c.getName() != null ? c.getName().trim() : "";
    }

    private SalesOrder getAndValidateOwner(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");
        if (!order.getSalesUserId().equals(SecurityUtil.currentUserId()))
            throw new BusinessException("Access denied");
        return order;
    }

    private void generateInvoice(SalesOrder order) {
        LocalDate issueDate = businessLocalDate(order);
        int paymentDays = order.getValidityDays() != null && order.getValidityDays() > 0
                ? order.getValidityDays() : 30;

        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(docSequenceService.nextDocNo("INV"));
        invoice.setOrderId(order.getId());
        invoice.setBillToCustomerId(order.getBillToCustomerId());
        invoice.setAmount(order.getTotalAmount());
        invoice.setStatus("PENDING");
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(issueDate.plusDays(paymentDays));
        invoice.setPaymentMethod(order.getPaymentMethod());
        invoiceMapper.insert(invoice);

        List<SalesOrderItem> items = itemMapper.findWithProductByOrderId(order.getId());
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Cannot generate invoice: order has no items");
        }
        for (SalesOrderItem item : items) {
            Receivable rec = new Receivable();
            rec.setInvoiceId(invoice.getId());
            rec.setOrderId(order.getId());
            rec.setOrderNo(order.getOrderNo());
            rec.setOrderItemId(item.getId());
            rec.setProductId(item.getProductId());
            rec.setProductName(resolveProductLabel(item));
            rec.setQty(item.getQty());
            rec.setUnitPrice(item.getUnitPrice());
            rec.setCustomerId(order.getBillToCustomerId());
            rec.setAmount(item.getTotal());
            rec.setReceivedAmount(BigDecimal.ZERO);
            rec.setBalance(item.getTotal());
            rec.setDueDate(invoice.getDueDate());
            rec.setStatus("OUTSTANDING");
            receivableMapper.insert(rec);
        }
    }

    private static String resolveProductLabel(SalesOrderItem item) {
        if (item.getProductName() != null && !item.getProductName().isBlank()) {
            return item.getProductName().trim();
        }
        if (item.getProductNo() != null && !item.getProductNo().isBlank()) {
            return item.getProductNo().trim();
        }
        return "Product #" + item.getProductId();
    }

    /** Invoice dates follow the sales order country wall clock, not server default zone. */
    private static LocalDate businessLocalDate(SalesOrder order) {
        ZoneId zone = ZoneId.of(CountryEnum.ofCountryCodeOrDefault(order.getCountryCode()).getTimeZoneCode());
        return LocalDate.now(zone);
    }

    private void generateOutboundOrder(SalesOrder order) {
        OutboundOrder dn = new OutboundOrder();
        dn.setOutboundNo(docSequenceService.nextDocNo("DN"));
        dn.setOrderId(order.getId());
        dn.setWarehouseId(order.getShipFromWarehouseId());
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
