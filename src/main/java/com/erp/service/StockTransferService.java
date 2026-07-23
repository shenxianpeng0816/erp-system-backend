package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.dto.request.CreateStockTransferRequest;
import com.erp.dto.response.TransferFormOptions;
import com.erp.entity.StockTransfer;
import com.erp.entity.StockTransferItem;

import java.util.List;

public interface StockTransferService {

    PageResult<StockTransfer> pageList(Long warehouseId, long page, long size);

    TransferFormOptions getFormOptions(Long fromWarehouseId);

    StockTransfer getDetail(Long id);

    List<StockTransferItem> listItems(Long id);

    StockTransfer create(CreateStockTransferRequest req);

    StockTransfer confirm(Long id);

    StockTransfer cancel(Long id);
}
