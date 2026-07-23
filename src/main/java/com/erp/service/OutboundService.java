package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.dto.response.OutboundPrintData;
import com.erp.entity.OutboundItem;
import com.erp.entity.OutboundOrder;

import java.util.List;

public interface OutboundService {

    PageResult<OutboundOrder> pageList(String keyword, String orderNo, Long warehouseId, long page, long size);

    List<OutboundOrder> export(String keyword, String orderNo, Long warehouseId);

    OutboundOrder getDetail(Long id);

    List<OutboundItem> listItems(Long outboundId);

    /** Assembled DN print payload (order + customer + lines). */
    OutboundPrintData getPrintData(Long id);

    OutboundOrder markPrinted(Long id);

    OutboundOrder ship(Long id);
}
