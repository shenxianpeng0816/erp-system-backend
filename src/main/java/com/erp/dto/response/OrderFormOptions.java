package com.erp.dto.response;

import com.erp.entity.Product;
import com.erp.entity.Warehouse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Dropdown data for new/edit order forms (assembled server-side). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderFormOptions {
    private List<Warehouse> warehouses = new ArrayList<>();
    /** Empty until warehouseId is provided. */
    private List<Product> products = new ArrayList<>();
}
