package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Invoice;
import com.erp.entity.PaymentRecord;
import com.erp.entity.Receivable;
import com.erp.mapper.InvoiceMapper;
import com.erp.mapper.ReceivableMapper;
import com.erp.util.SecurityUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final InvoiceMapper invoiceMapper;
    private final ReceivableMapper receivableMapper;

    // ── Invoices ─────────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<List<Invoice>> invoices(
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Invoice> q = new LambdaQueryWrapper<Invoice>()
                .orderByDesc(Invoice::getIssueDate);
        if (status != null) q.eq(Invoice::getStatus, status);
        return Result.success(invoiceMapper.selectList(q));
    }

    @GetMapping("/invoices/{id}")
    public Result<Invoice> invoiceDetail(@PathVariable Long id) {
        return Result.success(invoiceMapper.selectById(id));
    }

    // ── Receivables ──────────────────────────────────────────────────────────

    @GetMapping("/receivables")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<List<Receivable>> receivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId) {
        LambdaQueryWrapper<Receivable> q = new LambdaQueryWrapper<Receivable>()
                .orderByAsc(Receivable::getDueDate);
        if (status != null) q.eq(Receivable::getStatus, status);
        if (customerId != null) q.eq(Receivable::getCustomerId, customerId);
        return Result.success(receivableMapper.selectList(q));
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
}
