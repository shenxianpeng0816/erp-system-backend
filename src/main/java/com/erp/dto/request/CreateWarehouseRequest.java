package com.erp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWarehouseRequest {
    @NotBlank(message = "Warehouse code is required")
    private String warehouseCode;

    @NotBlank(message = "Warehouse name is required")
    private String name;

    @NotBlank(message = "Country code is required")
    private String countryCode;

    private String city;
    /** MAIN or BRANCH — defaults to BRANCH */
    private String type;
    private String address;
    /** When true, becomes the default ship-from warehouse for this country */
    private Boolean isDefault;
}
