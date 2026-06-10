package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.entity.Invoice;
import com.erp.entity.Receivable;
import com.erp.entity.SalesOrder;
import com.erp.entity.User;
import com.erp.mapper.CustomerMapper;
import com.erp.mapper.InvoiceMapper;
import com.erp.mapper.ReceivableMapper;
import com.erp.mapper.SalesOrderMapper;
import com.erp.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final UserMapper userMapper;

    // ── Invoices ─────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<List<Invoice>> invoices(
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Invoice> q = new LambdaQueryWrapper<Invoice>()
                .orderByDesc(Invoice::getIssueDate);
        if (status != null) q.eq(Invoice::getStatus, status);
        List<Invoice> invoices = invoiceMapper.selectList(q);
        enrichInvoices(invoices);
        return Result.success(invoices);
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
            q.ne(Receivable::getStatus, "SETTLED");
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
            @RequestParam(defaultValue = "20") long size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        LambdaQueryWrapper<Receivable> q = new LambdaQueryWrapper<Receivable>()
                .orderByAsc(Receivable::getDueDate);
        if (status != null) q.eq(Receivable::getStatus, status);
        if (customerId != null) q.eq(Receivable::getCustomerId, customerId);

        Page<Receivable> p = new Page<>(page, size);
        Page<Receivable> result = receivableMapper.selectPage(p, q);
        enrichReceivables(result.getRecords());

        PageResult<Receivable> pr = new PageResult<>();
        pr.setRecords(result.getRecords());
        pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        return Result.success(pr);
    }

    /** Record a payment against a receivable */
    @PostMapping("/receivables/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Transactional
    public Result<Receivable> recordPayment(@PathVariable Long id,
                                            @RequestBody PaymentRequest req) {
        Receivable rec = receivableMapper.selectById(id);
        if (rec == null) throw new BusinessException("Receivable not found");
        if ("SETTLED".equals(rec.getStatus())) throw new BusinessException("Already settled");

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
        for (Invoice inv : invoices) {
            if (inv.getBillToCustomerId() != null) {
                customerIds.add(inv.getBillToCustomerId());
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
        for (Invoice inv : invoices) {
            if (inv.getBillToCustomerId() != null) {
                inv.setBillToCustomerName(customerNames.get(inv.getBillToCustomerId()));
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
