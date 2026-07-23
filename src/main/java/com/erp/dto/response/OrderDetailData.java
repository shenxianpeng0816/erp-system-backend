package com.erp.dto.response;

import com.erp.entity.ApprovalFlow;
import com.erp.entity.Customer;
import com.erp.entity.SalesOrder;
import com.erp.entity.SalesOrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Assembled payload for order detail page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailData {
    private SalesOrder order;
    private List<SalesOrderItem> items;
    private Customer shipTo;
    private Customer billTo;
    private List<ApprovalFlow> approvals;
}
