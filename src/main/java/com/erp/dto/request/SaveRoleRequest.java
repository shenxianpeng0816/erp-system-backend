package com.erp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 100)
    private String roleName;

    /** lowercase key e.g. sales / custom_ops — stored lowercase */
    @NotBlank(message = "Role key is required")
    @Size(max = 50)
    private String roleKey;

    private Integer roleSort;

    /** 0=normal 1=disabled */
    @Size(max = 1)
    private String status;

    @Size(max = 500)
    private String remark;
}
