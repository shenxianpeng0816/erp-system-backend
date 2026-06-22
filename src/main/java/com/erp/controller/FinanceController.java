package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.dto.query.ReceivableFilterParams;
import com.erp.dto.query.ReceivableSummaryAgg;
import com.erp.entity.Customer;
import com.erp.entity.Invoice;
import com.erp.entity.PaymentRecord;
import com.erp.entity.Receivable;
import com.erp.entity.SalesOrder;
import com.erp.entity.User;
import com.erp.mapper.CustomerMapper;
import com.erp.mapper.InvoiceMapper;
import com.erp.mapper.PaymentRecordMapper;
import com.erp.mapper.ReceivableMapper;
import com.erp.mapper.SalesOrderMapper;
import com.erp.mapper.UserMapper;
import com.erp.util.SecurityUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final InvoiceMapper invoiceMapper;
    private final ReceivableMapper receivableMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo) {
        ReceivableFilterParams params = toFilterParams(
                status, customerId, createdFrom, createdTo, customerName, salesUserName, productName, orderNo,
                status == null);
        ReceivableSummaryAgg agg = receivableMapper.summarizeReceivables(params);
        ReceivableSummary summary = new ReceivableSummary();
        summary.setTotalOutstanding(
                agg.getTotalOutstanding() != null ? agg.getTotalOutstanding() : BigDecimal.ZERO);
        summary.setCount(agg.getRecordCount() != null ? agg.getRecordCount().intValue() : 0);
        return Result.success(summary);
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<PageResult<Receivable>> receivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Receivable> q = receivableQuery(
                status, customerId, createdFrom, createdTo, customerName, salesUserName, productName, orderNo);

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
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo) {
        List<Receivable> recs = receivableMapper.selectList(receivableQuery(
                status, customerId, createdFrom, createdTo, customerName, salesUserName, productName, orderNo));
        enrichReceivables(recs);
        return Result.success(recs);
    }

    private LambdaQueryWrapper<Receivable> receivableQuery(
            String status,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo,
            String customerName,
            String salesUserName,
            String productName,
            String orderNo) {
        ReceivableFilterParams params = toFilterParams(
                status, customerId, createdFrom, createdTo, customerName, salesUserName, productName, orderNo, null);
        LambdaQueryWrapper<Receivable> q = new LambdaQueryWrapper<Receivable>()
                .orderByDesc(Receivable::getCreatedAt);
        applyReceivableFilters(q, params);
        return q;
    }

    private ReceivableFilterParams toFilterParams(
            String status,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo,
            String customerName,
            String salesUserName,
            String productName,
            String orderNo,
            Boolean excludeSettledAndCancelled) {
        ReceivableFilterParams params = new ReceivableFilterParams();
        params.setStatus(status);
        params.setCustomerId(customerId);
        if (createdFrom != null) {
            params.setCreatedFrom(createdFrom.atStartOfDay());
        }
        if (createdTo != null) {
            params.setCreatedToExclusive(createdTo.plusDays(1).atStartOfDay());
        }
        if (customerName != null && !customerName.isBlank()) {
            params.setCustomerName(customerName.trim());
        }
        if (salesUserName != null && !salesUserName.isBlank()) {
            params.setSalesUserName(salesUserName.trim());
        }
        if (productName != null && !productName.isBlank()) {
            params.setProductName(productName.trim());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            params.setOrderNo(orderNo.trim());
        }
        params.setExcludeSettledAndCancelled(excludeSettledAndCancelled);
        return params;
    }

    private void applyReceivableFilters(LambdaQueryWrapper<Receivable> q, ReceivableFilterParams params) {
        if (params.getStatus() != null) {
            q.eq(Receivable::getStatus, params.getStatus());
        }
        if (Boolean.TRUE.equals(params.getExcludeSettledAndCancelled())) {
            q.notIn(Receivable::getStatus, "SETTLED", "CANCELLED");
        }
        if (params.getCustomerId() != null) {
            q.eq(Receivable::getCustomerId, params.getCustomerId());
        }
        if (params.getCreatedFrom() != null) {
            q.ge(Receivable::getCreatedAt, params.getCreatedFrom());
        }
        if (params.getCreatedToExclusive() != null) {
            q.lt(Receivable::getCreatedAt, params.getCreatedToExclusive());
        }
        if (params.getCustomerName() != null) {
            q.apply("""
                    EXISTS (
                        SELECT 1 FROM customer c
                        WHERE c.id = receivable.customer_id
                          AND c.name LIKE {0}
                    )
                    """, params.getCustomerName() + "%");
        }
        if (params.getSalesUserName() != null) {
            String prefix = params.getSalesUserName() + "%";
            q.apply("""
                    order_id IN (
                        SELECT o.id FROM sales_order o
                        INNER JOIN user u ON u.id = o.sales_user_id
                        WHERE u.real_name LIKE {0} OR u.username LIKE {0}
                    )
                    """, prefix, prefix);
        }
        if (params.getProductName() != null) {
            q.likeRight(Receivable::getProductName, params.getProductName());
        }
        if (params.getOrderNo() != null) {
            q.likeRight(Receivable::getOrderNo, params.getOrderNo());
        }
    }

    /** Paginated payment records for a receivable (supports multiple partial payments). */
    @GetMapping("/receivables/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<PageResult<PaymentRecord>> receivablePayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Receivable rec = receivableMapper.selectById(id);
        if (rec == null) throw new BusinessException("Receivable not found");

        Page<PaymentRecord> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<PaymentRecord> result = paymentRecordMapper.selectPage(p,
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getReceivableId, id)
                        .orderByDesc(PaymentRecord::getPaidAt)
                        .orderByDesc(PaymentRecord::getId));
        enrichPaymentRecords(result.getRecords());
        return Result.success(PageQuery.from(result));
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
        if (req.getQty() == null || req.getQty() <= 0) {
            throw new BusinessException("Payment quantity must be positive");
        }
        if (rec.getUnitPrice() == null || rec.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Unit price is not set for this receivable");
        }

        int receivedQty = rec.getReceivedQty() != null ? rec.getReceivedQty() : 0;
        int totalQty = rec.getQty() != null ? rec.getQty() : 0;
        int unpaidQty = Math.max(0, totalQty - receivedQty);
        if (req.getQty() > unpaidQty) {
            throw new BusinessException("Payment quantity exceeds unpaid quantity (" + unpaidQty + ")");
        }

        BigDecimal payAmount = rec.getUnitPrice()
                .multiply(BigDecimal.valueOf(req.getQty()))
                .setScale(2, RoundingMode.HALF_UP);
        if (req.getAmount() != null && req.getAmount().compareTo(payAmount) != 0) {
            throw new BusinessException("Payment amount does not match unit price × quantity");
        }

        String paymentMethod = normalizePaymentMethod(req.getPaymentMethod());
        String transactionRef = resolveTransactionRef(paymentMethod, req.getTransactionRef());
        if (transactionRef != null) {
            long duplicateCount = paymentRecordMapper.selectCount(
                    new LambdaQueryWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getPaymentMethod, paymentMethod)
                            .apply("UPPER(TRIM(transaction_ref)) = {0}", transactionRef));
            if (duplicateCount > 0) {
                throw new BusinessException(
                        "Transaction reference already exists for " + paymentMethod + ": " + transactionRef);
            }
        }

        BigDecimal newReceived = rec.getReceivedAmount().add(payAmount);
        if (newReceived.compareTo(rec.getAmount()) > 0)
            throw new BusinessException("Payment exceeds balance");

        PaymentRecord payment = new PaymentRecord();
        payment.setReceivableId(id);
        payment.setQty(req.getQty());
        payment.setAmount(payAmount);
        payment.setPaymentMethod(paymentMethod);
        payment.setTransactionRef(transactionRef);
        payment.setPaidAt(req.getPaidAt() != null ? req.getPaidAt() : LocalDateTime.now());
        payment.setRemark(req.getRemark() != null && !req.getRemark().isBlank() ? req.getRemark().trim() : null);
        payment.setCreatedBy(SecurityUtil.currentUserId());
        paymentRecordMapper.insert(payment);

        rec.setReceivedAmount(newReceived);
        rec.setReceivedQty(receivedQty + req.getQty());
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
        Set<Long> orderIds = new HashSet<>();
        for (Receivable rec : recs) {
            if (rec.getCustomerId() != null) {
                customerIds.add(rec.getCustomerId());
            }
            if (rec.getOrderId() != null) {
                orderIds.add(rec.getOrderId());
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

        for (Receivable rec : recs) {
            if (rec.getCustomerId() != null) {
                rec.setCustomerName(customerNames.get(rec.getCustomerId()));
            }
            SalesOrder order = rec.getOrderId() != null ? orderMap.get(rec.getOrderId()) : null;
            if (order != null) {
                if (rec.getOrderNo() == null || rec.getOrderNo().isBlank()) {
                    rec.setOrderNo(order.getOrderNo());
                }
                if (order.getSalesUserId() != null) {
                    rec.setSalesUserName(salesUserNames.get(order.getSalesUserId()));
                }
            }
            int totalQty = rec.getQty() != null ? rec.getQty() : 0;
            int receivedQty = rec.getReceivedQty() != null ? rec.getReceivedQty() : 0;
            rec.setUnpaidQty(Math.max(0, totalQty - receivedQty));
        }
    }

    private void enrichPaymentRecords(List<PaymentRecord> payments) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (PaymentRecord payment : payments) {
            if (payment.getCreatedBy() != null) {
                userIds.add(payment.getCreatedBy());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        List<User> users = userMapper.selectBatchIds(userIds);
        for (User u : users) {
            if (u != null && u.getId() != null) {
                names.put(u.getId(), userDisplayName(u));
            }
        }
        for (PaymentRecord payment : payments) {
            if (payment.getCreatedBy() != null) {
                payment.setCreatedByName(names.get(payment.getCreatedBy()));
            }
        }
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

    private static final String METHOD_CASH = "Cash";

    private static String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new BusinessException("Payment method is required");
        }
        String trimmed = method.trim();
        if ("Bank Transfer".equalsIgnoreCase(trimmed)) return "Bank Transfer";
        if ("Mpesa".equalsIgnoreCase(trimmed)) return "Mpesa";
        if ("Cash".equalsIgnoreCase(trimmed)) return "Cash";
        if ("Cheque".equalsIgnoreCase(trimmed)) return "Cheque";
        return trimmed;
    }

    private static boolean requiresTransactionRef(String paymentMethod) {
        return !METHOD_CASH.equalsIgnoreCase(paymentMethod);
    }

    /** Returns null for Cash; otherwise normalized transaction ref (uppercase, trimmed). */
    private static String resolveTransactionRef(String paymentMethod, String rawRef) {
        if (!requiresTransactionRef(paymentMethod)) {
            return null;
        }
        if (rawRef == null || rawRef.isBlank()) {
            throw new BusinessException("Transaction reference is required for " + paymentMethod);
        }
        return rawRef.trim().toUpperCase();
    }

    @Data
    public static class PaymentRequest {
        private BigDecimal amount;
        private Integer qty;
        private String paymentMethod;
        private String transactionRef;
        private LocalDateTime paidAt;
        private String remark;
    }

    @Data
    public static class ReceivableSummary {
        private BigDecimal totalOutstanding;
        private int count;
    }
}
