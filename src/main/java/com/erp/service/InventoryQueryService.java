package com.erp.service;

import com.erp.dto.response.InventoryFormOptions;
import com.erp.entity.Inventory;
import com.erp.entity.InventoryLog;

import java.util.List;

public interface InventoryQueryService {

    InventoryFormOptions getFormOptions(String countryCode);

    List<Inventory> list(Long warehouseId, String countryCode);

    List<Inventory> alerts(Long warehouseId, String countryCode);

    List<InventoryLog> logs(Long productId, Long warehouseId);

    List<InventoryLog> logsByRef(Long refId, String refType);

    List<InventoryLog> transactionLogs(Long warehouseId, String productName);
}
