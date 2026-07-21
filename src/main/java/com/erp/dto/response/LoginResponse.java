package com.erp.dto.response;

import com.erp.entity.SysMenu;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    /** Legacy primary role (ADMIN/SALES/...) — keep for mobile compatibility */
    private String role;
    /** RBAC role keys e.g. admin, sales */
    private Set<String> roles;
    /** Permission strings e.g. erp:order:add */
    private Set<String> permissions;
    /** Sidebar menu tree (M/C only) */
    private List<SysMenu> menus;
}
