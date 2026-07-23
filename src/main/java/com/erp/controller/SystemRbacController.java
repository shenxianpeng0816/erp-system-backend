package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.dto.request.SaveRoleRequest;
import com.erp.entity.SysMenu;
import com.erp.entity.SysRole;
import com.erp.service.SysPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemRbacController {

    private final SysPermissionService permissionService;

    @GetMapping("/roles")
    @PreAuthorize("@ss.hasPermi('erp:role:list')")
    public Result<List<SysRole>> listRoles(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.success(includeDisabled
                ? permissionService.listRolesAll()
                : permissionService.listRoles());
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("@ss.hasPermi('erp:role:list')")
    public Result<Map<String, Object>> roleDetail(@PathVariable Long roleId) {
        return Result.success(permissionService.getRoleDetail(roleId));
    }

    @PostMapping("/roles")
    @PreAuthorize("@ss.hasPermi('erp:role:edit')")
    public Result<SysRole> createRole(@Valid @RequestBody SaveRoleRequest req) {
        return Result.success(permissionService.createRole(req));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@ss.hasPermi('erp:role:edit')")
    public Result<SysRole> updateRole(@PathVariable Long roleId,
                                      @Valid @RequestBody SaveRoleRequest req) {
        return Result.success(permissionService.updateRole(roleId, req));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("@ss.hasPermi('erp:role:edit')")
    public Result<Void> disableRole(@PathVariable Long roleId) {
        permissionService.disableRole(roleId);
        return Result.success();
    }

    @PutMapping("/roles/{roleId}/menus")
    @PreAuthorize("@ss.hasPermi('erp:role:edit')")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        permissionService.assignRoleMenus(roleId, menuIds);
        return Result.success();
    }

    @GetMapping("/menus/tree")
    @PreAuthorize("@ss.hasPermi('erp:menu:list')")
    public Result<List<SysMenu>> menuTree() {
        return Result.success(permissionService.listAllMenusTree());
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("@ss.hasPermi('erp:user:role:edit')")
    public Result<List<Long>> userRoleIds(@PathVariable Long userId) {
        return Result.success(permissionService.listUserRoleIds(userId));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("@ss.hasPermi('erp:user:role:edit')")
    public Result<Void> assignUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        permissionService.assignUserRoles(userId, roleIds);
        return Result.success();
    }
}
