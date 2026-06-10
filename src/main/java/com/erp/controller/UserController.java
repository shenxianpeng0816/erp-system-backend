package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.SysOperationLog;
import com.erp.entity.User;
import com.erp.mapper.SysOperationLogMapper;
import com.erp.mapper.UserMapper;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Active users for role-based dropdowns.
     * {@code INBOUND} returns INBOUND + WAREHOUSE (both may act as inbound operator).
     * {@code WAREHOUSE} returns WAREHOUSE only.
     */
    @GetMapping("/by-role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE','FINANCE','SALES')")
    public Result<List<User>> listByRole(@PathVariable String role) {
        String r = role == null ? "" : role.trim().toUpperCase();
        if (!Set.of("INBOUND", "WAREHOUSE", "SALES").contains(r)) {
            throw new BusinessException("Unsupported role filter");
        }
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getRealName);
        if ("INBOUND".equals(r)) {
            q.in(User::getRole, List.of("INBOUND", "WAREHOUSE"));
        } else if ("SALES".equals(r)) {
            q.in(User::getRole, List.of("SALES", "ADMIN"));
        } else {
            q.eq(User::getRole, "WAREHOUSE");
        }
        List<User> users = userMapper.selectList(q);
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<SysOperationLog>> operationLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        Page<SysOperationLog> p = new Page<>(page, size);
        Page<SysOperationLog> result = operationLogMapper.selectPage(
                p,
                new LambdaQueryWrapper<SysOperationLog>().orderByDesc(SysOperationLog::getOperationAt));
        PageResult<SysOperationLog> pr = new PageResult<>();
        pr.setRecords(result.getRecords());
        pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        return Result.success(pr);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        users.forEach(u -> u.setPassword(null)); // mask password
        return Result.success(users);
    }

    @GetMapping("/me")
    public Result<User> me() {
        User u = SecurityUtil.currentUser();
        u.setPassword(null);
        return Result.success(u);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> create(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        userMapper.updateById(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable Long id) {
        User u = new User();
        u.setId(id);
        u.setStatus(0);
        userMapper.updateById(u);
        return Result.success();
    }
}
