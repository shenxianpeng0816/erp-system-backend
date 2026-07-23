package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.dto.request.CreateInboundRequest;
import com.erp.dto.request.RejectInboundRequest;
import com.erp.dto.response.InboundFormOptions;
import com.erp.entity.InboundItem;
import com.erp.entity.InboundOrder;

import java.util.List;

public interface InboundService {

    /** Warehouses + operators; products when warehouseId set. */
    InboundFormOptions getFormOptions(Long warehouseId);

    PageResult<InboundOrder> pageList(Long warehouseId, long page, long size);

    List<InboundOrder> export(Long warehouseId);

    InboundOrder getDetail(Long id);

    List<InboundItem> listItems(Long id);

    InboundOrder create(CreateInboundRequest req);

    InboundOrder update(Long id, CreateInboundRequest req);

    InboundOrder confirm(Long id);

    InboundOrder reject(Long id, RejectInboundRequest body);

    void delete(Long id);
}
