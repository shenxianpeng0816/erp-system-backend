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
import com.erp.security.PermissionService;
import com.erp.service.DocSequenceService;
import com.erp.service.InventoryService;
import com.erp.service.SalesOrderService;
import com.erp.service.StockChangeContext;
import com.erp.service.WarehouseService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PermissionService permissionService;

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING_FIRST = "PENDING_FIRST_APPROVAL";
    private static final String STATUS_PENDING_FINAL = "PENDING_FINAL_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String PERM_APPROVE_FIRST = "erp:order:approve:first";
    private static final String PERM_APPROVE_FINAL = "erp:order:approve:final";
    /** Legacy permission codes (pre rename) */
    private static final String PERM_APPROVE_FIRST_LEGACY = "erp:order:approve:finance";
    private static final String PERM_APPROVE_FINAL_LEGACY = "erp:order:approve:admin";
    private static final String PERM_APPROVE_LEGACY = "erp:order:approve";

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

        validateOrderItemsStock(req.getItems(), warehouse.getId(), order.getCountryCode());

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
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new BusinessException("Only DRAFT orders can be submitted");
        }

        order.setStatus(STATUS_PENDING_FIRST);
        order.setRejectReason(null);
        orderMapper.updateById(order);

        // Unassigned pending step — any user with first-approve permission may act
        ApprovalFlow flow = newApprovalStep(orderId, nextApprovalStep(orderId));
        approvalMapper.insert(flow);
        return order;
    }

    @Override
    @Transactional
    public SalesOrder handleApproval(Long orderId, ApprovalRequest req) {
        if (req == null || !StringUtils.hasText(req.getAction())) {
            throw new BusinessException("Approval action is required");
        }
        String action = req.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("Invalid action. Allowed: APPROVE, REJECT");
        }

        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("Order not found");

        String status = order.getStatus();
        assertCanActOnPendingStatus(status);

        ApprovalFlow flow = approvalMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getOrderId, orderId)
                        .eq(ApprovalFlow::getStatus, "PENDING")
                        .orderByDesc(ApprovalFlow::getStep)
                        .last("LIMIT 1"));
        if (flow == null) throw new BusinessException("No pending approval step found");

        User actor = SecurityUtil.currentUser();
        flow.setApproverId(actor.getId());
        flow.setApproverRole(resolveActorRoleSnapshot(actor));
        flow.setActedAt(LocalDateTime.now());
        flow.setComment(req.getComment());

        if ("REJECT".equals(action)) {
            if (!StringUtils.hasText(req.getComment())) {
                throw new BusinessException("Reject reason is required");
            }
            flow.setStatus("REJECTED");
            order.setStatus(STATUS_DRAFT);
            order.setRejectReason(req.getComment().trim());
            approvalMapper.updateById(flow);
            orderMapper.updateById(order);
            return order;
        }

        // APPROVE
        flow.setStatus("APPROVED");
        if (STATUS_PENDING_FIRST.equals(status)) {
            ApprovalFlow next = newApprovalStep(orderId, flow.getStep() + 1);
            approvalMapper.insert(next);
            order.setStatus(STATUS_PENDING_FINAL);
            order.setRejectReason(null);
        } else if (STATUS_PENDING_FINAL.equals(status)) {
            order.setStatus(STATUS_APPROVED);
            order.setRejectReason(null);
            generateInvoice(order);
            generateOutboundOrder(order);
        } else {
            throw new BusinessException("Order is not pending approval");
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
        List<SalesOrder> filtered = list.stream()
                .filter(o -> isPendingVisibleToCurrentUser(o.getStatus()))
                .toList();
        enrichCustomerNames(filtered);
        return filtered;
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
     * SALES: only own orders. ADMIN: all.
     * Users with first/final approve perms: pending + post-approval pipeline.
     * FINANCE role (legacy): same pipeline. WAREHOUSE / INBOUND: all.
     * Also: erp:order:list:all / outbound print|list (DN print & warehouse list).
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
        if (canViewViaApprovalPermission(order.getStatus())) {
            return;
        }
        if ("FINANCE".equals(role) && isApprovalPipelineStatus(order.getStatus())) {
            return;
        }
        if ("WAREHOUSE".equals(role) || "INBOUND".equals(role)) {
            return;
        }
        // RBAC: warehouse-like custom roles, or list:all without legacy role string
        if (permissionService.hasPermi("erp:order:list:all")
                || permissionService.hasAnyPermi("erp:outbound:print,erp:outbound:list")) {
            return;
        }
        if (permissionService.hasPermi("erp:order:list:mine")
                && order.getSalesUserId() != null
                && order.getSalesUserId().equals(uid)) {
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

    /** Pending approvals + post-approval pipeline (invoice / outbound linked). */
    private static boolean isApprovalPipelineStatus(String status) {
        if (status == null) return false;
        return STATUS_PENDING_FIRST.equals(status)
                || STATUS_PENDING_FINAL.equals(status)
                || STATUS_APPROVED.equals(status)
                || "SHIPPED".equals(status)
                || "CONFIRMED".equals(status);
    }

    private boolean canViewViaApprovalPermission(String status) {
        if (!hasFirstApprovePerm() && !hasFinalApprovePerm()) {
            return false;
        }
        return isApprovalPipelineStatus(status);
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
        if (!STATUS_DRAFT.equals(st)
                && !STATUS_PENDING_FIRST.equals(st)
                && !STATUS_PENDING_FINAL.equals(st)
                && !"REJECTED".equals(st)) {
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

        String st = order.getStatus();
        boolean pending = STATUS_PENDING_FIRST.equals(st) || STATUS_PENDING_FINAL.equals(st);
        if (!STATUS_DRAFT.equals(st) && !pending) {
            throw new BusinessException("Only draft or pending approval orders can be edited");
        }

        String role = SecurityUtil.currentRole();
        Long uid = SecurityUtil.currentUserId();
        if ("ADMIN".equals(role)) {
            // Admin may edit draft and pending
        } else if ("SALES".equals(role) && order.getSalesUserId().equals(uid)) {
            if (!STATUS_DRAFT.equals(st)) {
                throw new BusinessException("Sales can only edit draft orders; wait for rejection or ask admin");
            }
        } else {
            throw new BusinessException("Access denied");
        }

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

        validateOrderItemsStock(req.getItems(), warehouse.getId(), order.getCountryCode());

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

    private void validateOrderItemsStock(
            List<CreateOrderRequest.OrderItemRequest> items, Long warehouseId, String orderCountryCode) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Order must contain at least one line item");
        }
        if (warehouseId == null) {
            throw new BusinessException("Ship-from warehouse is required");
        }
        String orderCc = normalizeCountryCode(orderCountryCode);
        Warehouse warehouse = warehouseService.requireActive(warehouseId);
        for (CreateOrderRequest.OrderItemRequest i : items) {
            if (i.getProductId() == null) {
                throw new BusinessException("Each line item must have a product");
            }
            if (i.getQty() == null || i.getQty() < 1) {
                throw new BusinessException("Each line item must have quantity at least 1");
            }
            Product product = productMapper.selectById(i.getProductId());
            if (product == null) {
                throw new BusinessException("Product #" + i.getProductId() + " not found");
            }
            String productLabel = product.getProductNo() + " — " + product.getName();
            String productCc = product.getCountryCode() != null
                    ? product.getCountryCode().trim().toUpperCase() : "";
            if (!orderCc.equals(productCc)) {
                throw new BusinessException(
                        "Product " + productLabel + " is for country " + productCc
                                + " but order country is " + orderCc);
            }

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

    private void assertCanActOnPendingStatus(String status) {
        if (STATUS_PENDING_FIRST.equals(status)) {
            if (!hasFirstApprovePerm()) {
                throw new BusinessException("First approval permission required");
            }
            return;
        }
        if (STATUS_PENDING_FINAL.equals(status)) {
            if (!hasFinalApprovePerm()) {
                throw new BusinessException("Final approval permission required");
            }
            return;
        }
        throw new BusinessException("Order is not pending approval");
    }

    private boolean isPendingVisibleToCurrentUser(String status) {
        if (STATUS_PENDING_FIRST.equals(status)) {
            return hasFirstApprovePerm();
        }
        if (STATUS_PENDING_FINAL.equals(status)) {
            return hasFinalApprovePerm();
        }
        return false;
    }

    private boolean hasFirstApprovePerm() {
        return permissionService.hasPermi(PERM_APPROVE_FIRST)
                || permissionService.hasPermi(PERM_APPROVE_FIRST_LEGACY);
    }

    private boolean hasFinalApprovePerm() {
        return permissionService.hasPermi(PERM_APPROVE_FINAL)
                || permissionService.hasPermi(PERM_APPROVE_FINAL_LEGACY)
                || permissionService.hasPermi(PERM_APPROVE_LEGACY);
    }

    private ApprovalFlow newApprovalStep(Long orderId, int step) {
        ApprovalFlow flow = new ApprovalFlow();
        flow.setOrderId(orderId);
        flow.setStep(step);
        flow.setApproverId(null);
        flow.setApproverRole(null);
        flow.setStatus("PENDING");
        return flow;
    }

    private int nextApprovalStep(Long orderId) {
        List<ApprovalFlow> existing = approvalMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getOrderId, orderId)
                        .orderByDesc(ApprovalFlow::getStep)
                        .last("LIMIT 1"));
        if (existing.isEmpty() || existing.get(0).getStep() == null) {
            return 1;
        }
        return existing.get(0).getStep() + 1;
    }

    /**
     * Snapshot of system role keys at action time for audit trail.
     * Prefers RBAC {@code sys_role.role_key}; falls back to legacy {@code user.role}.
     */
    private static String resolveActorRoleSnapshot(User user) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (SysRole r : user.getRoles()) {
                if (r == null) continue;
                if (StringUtils.hasText(r.getRoleKey())) {
                    keys.add(r.getRoleKey().trim().toUpperCase());
                } else if (StringUtils.hasText(r.getRoleName())) {
                    keys.add(r.getRoleName().trim());
                }
            }
        }
        if (keys.isEmpty() && StringUtils.hasText(user.getRole())) {
            keys.add(user.getRole().trim().toUpperCase());
        }
        if (keys.isEmpty()) {
            return "UNKNOWN";
        }
        return keys.stream().collect(Collectors.joining(","));
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
