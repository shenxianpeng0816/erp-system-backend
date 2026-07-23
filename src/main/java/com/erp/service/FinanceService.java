package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.dto.request.PaymentRequest;
import com.erp.dto.response.InvoicePrintData;
import com.erp.dto.response.ReceivableSummary;
import com.erp.entity.Invoice;
import com.erp.entity.PaymentRecord;
import com.erp.entity.Receivable;

import java.time.LocalDate;
import java.util.List;

public interface FinanceService {

    PageResult<Invoice> pageInvoices(String status, String orderNo, long page, long size);

    Invoice getInvoiceDetail(Long id);

    InvoicePrintData getInvoicePrintData(Long invoiceId);

    ReceivableSummary getReceivablesSummary(
            String status,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo,
            String customerName,
            String shopName,
            String salesUserName,
            String productName,
            String orderNo);

    PageResult<Receivable> pageReceivables(
            String status,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo,
            String customerName,
            String shopName,
            String salesUserName,
            String productName,
            String orderNo,
            long page,
            long size);

    List<Receivable> exportReceivables(
            String status,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo,
            String customerName,
            String shopName,
            String salesUserName,
            String productName,
            String orderNo);

    PageResult<PaymentRecord> pageReceivablePayments(Long receivableId, long page, long size);

    Receivable recordPayment(Long receivableId, PaymentRequest req);

    Receivable updatePayment(Long receivableId, Long paymentId, PaymentRequest req);

    Receivable deletePayment(Long receivableId, Long paymentId);
}
