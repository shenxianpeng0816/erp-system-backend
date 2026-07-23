package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.entity.SysOperationLog;
import com.erp.entity.User;
import com.erp.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAdminService userAdminService;

    @GetMapping("/by-role/{role}")
    @PreAuthorize("@ss.hasPermi('erp:user:byRole')")
    public Result<List<User>> listByRole(@PathVariable String role) {
        return Result.success(userAdminService.listByRole(role));
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("@ss.hasPermi('erp:user:operlog')")
    public Result<PageResult<SysOperationLog>> operationLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(userAdminService.operationLogs(page, size));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:user:list')")
    public Result<List<User>> list() {
        return Result.success(userAdminService.list());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<User> me() {
        return Result.success(userAdminService.getCurrentUser());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:user:edit')")
    public Result<User> create(@RequestBody User user) {
        return Result.success(userAdminService.create(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:user:edit')")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        return Result.success(userAdminService.update(id, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:user:remove')")
    public Result<?> delete(@PathVariable Long id) {
        userAdminService.delete(id);
        return Result.success();
    }
}
