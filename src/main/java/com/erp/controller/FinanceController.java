package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.entity.Invoice;
import com.erp.entity.Receivable;
import com.erp.entity.SalesOrder;
import com.erp.entity.SalesOrderItem;
import com.erp.entity.User;
import com.erp.mapper.CustomerMapper;
import com.erp.mapper.InvoiceMapper;
import com.erp.mapper.ReceivableMapper;
import com.erp.mapper.SalesOrderItemMapper;
import com.erp.mapper.SalesOrderMapper;
import com.erp.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final InvoiceMapper invoiceMapper;
    private final ReceivableMapper receivableMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final UserMapper userMapper;

    // ── Invoices ─────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<PageResult<Invoice>> invoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Invoice> q = new LambdaQueryWrapper<Invoice>()
                .orderByDesc(Invoice::getIssueDate);
        if (status != null) q.eq(Invoice::getStatus, status);
        if (orderNo != null && !orderNo.isBlank()) {
            List<SalesOrder> orders = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>()
                            .like(SalesOrder::getOrderNo, orderNo.trim()));
            if (orders.isEmpty()) {
                PageResult<Invoice> empty = new PageResult<>();
                empty.setRecords(List.of());
                empty.setTotal(0);
                empty.setCurrent(PageQuery.normalizePage(page));
                empty.setSize(PageQuery.normalizeSize(size));
                return Result.success(empty);
            }
            q.in(Invoice::getOrderId,
                    orders.stream().map(SalesOrder::getId).toList());
        }
        Page<Invoice> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<Invoice> result = invoiceMapper.selectPage(p, q);
        enrichInvoices(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    @GetMapping("/invoices/{id}")
    public Result<Invoice> invoiceDetail(@PathVariable Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice != null) {
            enrichInvoices(List.of(invoice));
        }
        return Result.success(invoice);
    }

    // ── Receivables ──────────────────────────────────────────────────────────

    @GetMapping("/receivables/summary")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<ReceivableSummary> receivablesSummary(
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Receivable> q = new LambdaQueryWrapper<>();
        if (status != null) {
            q.eq(Receivable::getStatus, status);
        } else {
            q.notIn(Receivable::getStatus, "SETTLED", "CANCELLED");
        }
        List<Receivable> recs = receivableMapper.selectList(q);
        BigDecimal total = recs.stream()
                .map(Receivable::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ReceivableSummary summary = new ReceivableSummary();
        summary.setTotalOutstanding(total);
        summary.setCount(recs.size());
        return Result.success(summary);
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<PageResult<Receivable>> receivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Receivable> q = receivableQuery(status, customerId);

        Page<Receivable> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<Receivable> result = receivableMapper.selectPage(p, q);
        enrichReceivables(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    /** Export all receivables matching list filters (no pagination). */
    @GetMapping("/receivables/export")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<List<Receivable>> exportReceivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId) {
        List<Receivable> recs = receivableMapper.selectList(receivableQuery(status, customerId));
        enrichReceivables(recs);
        return Result.success(recs);
    }

    private LambdaQueryWrapper<Receivable> receivableQuery(String status, Long customerId) {
        LambdaQueryWrapper<Receivable> q = new LambdaQueryWrapper<Receivable>()
                .orderByAsc(Receivable::getDueDate);
        if (status != null) q.eq(Receivable::getStatus, status);
        if (customerId != null) q.eq(Receivable::getCustomerId, customerId);
        return q;
    }

    /** Record a payment against a receivable */
    @PostMapping("/receivables/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Transactional
    public Result<Receivable> recordPayment(@PathVariable Long id,
                                            @RequestBody PaymentRequest req) {
        Receivable rec = receivableMapper.selectById(id);
        if (rec == null) throw new BusinessException("Receivable not found");
        if ("CANCELLED".equals(rec.getStatus())) throw new BusinessException("Receivable is cancelled");
        if ("SETTLED".equals(rec.getStatus())) throw new BusinessException("Already settled");

        Invoice invCheck = invoiceMapper.selectById(rec.getInvoiceId());
        if (invCheck != null && "CANCELLED".equals(invCheck.getStatus())) {
            throw new BusinessException("Invoice is cancelled");
        }

        BigDecimal newReceived = rec.getReceivedAmount().add(req.getAmount());
        if (newReceived.compareTo(rec.getAmount()) > 0)
            throw new BusinessException("Payment exceeds balance");

        rec.setReceivedAmount(newReceived);
        rec.setBalance(rec.getAmount().subtract(newReceived));

        if (rec.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            rec.setStatus("SETTLED");
        } else {
            rec.setStatus("PARTIAL");
        }
        receivableMapper.updateById(rec);

        // Update invoice status
        updateInvoiceStatus(rec.getInvoiceId());
        return Result.success(rec);
    }

    private void enrichInvoices(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return;
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (Invoice inv : invoices) {
            if (inv.getBillToCustomerId() != null) {
                customerIds.add(inv.getBillToCustomerId());
            }
            if (inv.getOrderId() != null) {
                orderIds.add(inv.getOrderId());
            }
        }
        Map<Long, String> customerNames = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>().in(Customer::getId, customerIds));
            for (Customer c : customers) {
                customerNames.put(c.getId(), c.getName());
            }
        }
        Map<Long, SalesOrder> orderMap = new HashMap<>();
        Set<Long> salesUserIds = new HashSet<>();
        if (!orderIds.isEmpty()) {
            List<SalesOrder> orders = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>().in(SalesOrder::getId, orderIds));
            for (SalesOrder order : orders) {
                orderMap.put(order.getId(), order);
                if (order.getSalesUserId() != null) {
                    salesUserIds.add(order.getSalesUserId());
                }
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
        for (Invoice inv : invoices) {
            if (inv.getBillToCustomerId() != null) {
                inv.setBillToCustomerName(customerNames.get(inv.getBillToCustomerId()));
            }
            if (inv.getOrderId() != null) {
                SalesOrder order = orderMap.get(inv.getOrderId());
                if (order != null) {
                    inv.setOrderNo(order.getOrderNo());
                    if (order.getSalesUserId() != null) {
                        inv.setSalesUserName(salesUserNames.get(order.getSalesUserId()));
                    }
                }
            }
        }
    }

    private void enrichReceivables(List<Receivable> recs) {
        if (recs == null || recs.isEmpty()) {
            return;
        }

        Set<Long> customerIds = new HashSet<>();
        Set<Long> invoiceIds = new HashSet<>();
        for (Receivable rec : recs) {
            if (rec.getCustomerId() != null) {
                customerIds.add(rec.getCustomerId());
            }
            if (rec.getInvoiceId() != null) {
                invoiceIds.add(rec.getInvoiceId());
            }
        }

        Map<Long, String> customerNames = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>().in(Customer::getId, customerIds));
            for (Customer c : customers) {
                customerNames.put(c.getId(), c.getName());
            }
        }

        Map<Long, Long> invoiceToOrderId = new HashMap<>();
        if (!invoiceIds.isEmpty()) {
            List<Invoice> invoices = invoiceMapper.selectList(
                    new LambdaQueryWrapper<Invoice>().in(Invoice::getId, invoiceIds));
            for (Invoice inv : invoices) {
                if (inv.getOrderId() != null) {
                    invoiceToOrderId.put(inv.getId(), inv.getOrderId());
                }
            }
        }

        Set<Long> orderIds = new HashSet<>(invoiceToOrderId.values());
        Map<Long, Long> orderToSalesUserId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<SalesOrder> orders = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>().in(SalesOrder::getId, orderIds));
            for (SalesOrder order : orders) {
                if (order.getSalesUserId() != null) {
                    orderToSalesUserId.put(order.getId(), order.getSalesUserId());
                }
            }
        }

        Set<Long> salesUserIds = new HashSet<>(orderToSalesUserId.values());
        Map<Long, String> salesUserNames = new HashMap<>();
        if (!salesUserIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(salesUserIds);
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    salesUserNames.put(u.getId(), userDisplayName(u));
                }
            }
        }

        Map<Long, List<SalesOrderItem>> itemsByOrderId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<SalesOrderItem> items = salesOrderItemMapper.findWithProductByOrderIds(new ArrayList<>(orderIds));
            for (SalesOrderItem item : items) {
                itemsByOrderId.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
            }
        }

        for (Receivable rec : recs) {
            if (rec.getCustomerId() != null) {
                rec.setCustomerName(customerNames.get(rec.getCustomerId()));
            }
            Long orderId = invoiceToOrderId.get(rec.getInvoiceId());
            if (orderId != null) {
                Long salesUserId = orderToSalesUserId.get(orderId);
                if (salesUserId != null) {
                    rec.setSalesUserName(salesUserNames.get(salesUserId));
                }
                rec.setProductName(formatProductNameSummary(itemsByOrderId.getOrDefault(orderId, List.of())));
            }
        }
    }

    private static String formatProductNameSummary(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> {
                    String name = item.getProductName() != null && !item.getProductName().isBlank()
                            ? item.getProductName().trim()
                            : "Product #" + item.getProductId();
                    return name + " ×" + item.getQty();
                })
                .collect(Collectors.joining(" / "));
    }

    private static String userDisplayName(User u) {
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            return u.getRealName().trim();
        }
        return u.getUsername() != null ? u.getUsername() : "";
    }

    private void updateInvoiceStatus(Long invoiceId) {
        // Sum all receivables for this invoice
        List<Receivable> recs = receivableMapper.selectList(
                new LambdaQueryWrapper<Receivable>().eq(Receivable::getInvoiceId, invoiceId));
        boolean allSettled = recs.stream().allMatch(r -> "SETTLED".equals(r.getStatus()));
        boolean anyPartial = recs.stream().anyMatch(r -> "PARTIAL".equals(r.getStatus()));

        Invoice inv = invoiceMapper.selectById(invoiceId);
        if (inv == null) return;
        if (allSettled) inv.setStatus("PAID");
        else if (anyPartial) inv.setStatus("PARTIAL");
        invoiceMapper.updateById(inv);
    }

    @Data
    public static class PaymentRequest {
        private BigDecimal amount;
        private String paymentMethod;
        private String mpesaRef;
        private LocalDate paidAt;
        private String remark;
    }

    @Data
    public static class ReceivableSummary {
        private BigDecimal totalOutstanding;
        private int count;
    }
}
