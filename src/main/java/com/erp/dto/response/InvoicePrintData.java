package com.erp.dto.response;

import com.erp.entity.Customer;
import com.erp.entity.Invoice;
import com.erp.entity.SalesOrder;
import com.erp.entity.SalesOrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Assembled payload for invoice print preview. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePrintData {
    private Invoice invoice;
    private SalesOrder order;
    private List<SalesOrderItem> items;
    private Customer billTo;
}
