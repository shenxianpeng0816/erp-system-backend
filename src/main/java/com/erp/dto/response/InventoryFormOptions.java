package com.erp.dto.response;

import com.erp.entity.Warehouse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Warehouse filter options for inventory screens. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFormOptions {
    private List<Warehouse> warehouses = new ArrayList<>();
}
