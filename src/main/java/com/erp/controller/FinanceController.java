package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.dto.request.PaymentRequest;
import com.erp.dto.response.InvoicePrintData;
import com.erp.dto.response.ReceivableSummary;
import com.erp.entity.Invoice;
import com.erp.entity.PaymentRecord;
import com.erp.entity.Receivable;
import com.erp.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/invoices")
    @PreAuthorize("@ss.hasPermi('erp:finance:invoice:list')")
    public Result<PageResult<Invoice>> invoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(financeService.pageInvoices(status, orderNo, page, size));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("@ss.hasPermi('erp:finance:invoice:list')")
    public Result<Invoice> invoiceDetail(@PathVariable Long id) {
        return Result.success(financeService.getInvoiceDetail(id));
    }

    @GetMapping("/invoices/{id}/print")
    @PreAuthorize("@ss.hasPermi('erp:finance:invoice:list')")
    public Result<InvoicePrintData> invoicePrintData(@PathVariable Long id) {
        return Result.success(financeService.getInvoicePrintData(id));
    }

    @GetMapping("/receivables/summary")
    @PreAuthorize("@ss.hasPermi('erp:finance:receivable:list')")
    public Result<ReceivableSummary> receivablesSummary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo) {
        return Result.success(financeService.getReceivablesSummary(
                status, customerId, createdFrom, createdTo, customerName, shopName, salesUserName, productName, orderNo));
    }

    @GetMapping("/receivables")
    @PreAuthorize("@ss.hasPermi('erp:finance:receivable:list')")
    public Result<PageResult<Receivable>> receivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(financeService.pageReceivables(
                status, customerId, createdFrom, createdTo, customerName, shopName, salesUserName, productName, orderNo,
                page, size));
    }

    @GetMapping("/receivables/export")
    @PreAuthorize("@ss.hasPermi('erp:finance:receivable:export')")
    public Result<List<Receivable>> exportReceivables(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String salesUserName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String orderNo) {
        return Result.success(financeService.exportReceivables(
                status, customerId, createdFrom, createdTo, customerName, shopName, salesUserName, productName, orderNo));
    }

    @GetMapping("/receivables/{id}/payments")
    @PreAuthorize("@ss.hasPermi('erp:finance:receivable:list')")
    public Result<PageResult<PaymentRecord>> receivablePayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(financeService.pageReceivablePayments(id, page, size));
    }

    @PostMapping("/receivables/{id}/pay")
    @PreAuthorize("@ss.hasPermi('erp:finance:pay')")
    public Result<Receivable> recordPayment(@PathVariable Long id,
                                            @RequestBody PaymentRequest req) {
        return Result.success(financeService.recordPayment(id, req));
    }

    @PutMapping("/receivables/{receivableId}/payments/{paymentId}")
    @PreAuthorize("@ss.hasPermi('erp:finance:pay:edit')")
    public Result<Receivable> updatePayment(@PathVariable Long receivableId,
                                            @PathVariable Long paymentId,
                                            @RequestBody PaymentRequest req) {
        return Result.success(financeService.updatePayment(receivableId, paymentId, req));
    }

    @DeleteMapping("/receivables/{receivableId}/payments/{paymentId}")
    @PreAuthorize("@ss.hasPermi('erp:finance:pay:remove')")
    public Result<Receivable> deletePayment(@PathVariable Long receivableId,
                                            @PathVariable Long paymentId) {
        return Result.success(financeService.deletePayment(receivableId, paymentId));
    }
}
