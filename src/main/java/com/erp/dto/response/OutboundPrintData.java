package com.erp.dto.response;

import com.erp.entity.Customer;
import com.erp.entity.OutboundItem;
import com.erp.entity.OutboundOrder;
import com.erp.entity.SalesOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Assembled payload for Delivery Note print preview. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboundPrintData {
    private OutboundOrder dn;
    private List<OutboundItem> items;
    private SalesOrder order;
    private Customer customer;
}
