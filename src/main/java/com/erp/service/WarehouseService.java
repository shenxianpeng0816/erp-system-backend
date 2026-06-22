package com.erp.service;

import com.erp.dto.request.CreateWarehouseRequest;
import com.erp.entity.Warehouse;

import java.util.List;

public interface WarehouseService {

    Warehouse requireActive(Long warehouseId);

    Warehouse requireActiveForCountry(Long warehouseId, String countryCode);

    Warehouse defaultForCountry(String countryCode);

    List<Warehouse> listActive(String countryCode);

    List<Warehouse> listAll(String countryCode);

    Warehouse create(CreateWarehouseRequest req);
}
