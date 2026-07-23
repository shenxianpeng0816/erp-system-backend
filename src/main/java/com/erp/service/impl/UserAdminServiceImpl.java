package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.entity.SysOperationLog;
import com.erp.entity.SysRole;
import com.erp.entity.User;
import com.erp.mapper.SysOperationLogMapper;
import com.erp.mapper.SysRoleMapper;
import com.erp.mapper.SysUserRoleMapper;
import com.erp.mapper.UserMapper;
import com.erp.service.SysPermissionService;
import com.erp.service.UserAdminService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserMapper userMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final SysPermissionService permissionService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<User> listByRole(String role) {
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
        return users;
    }

    @Override
    public PageResult<SysOperationLog> operationLogs(long page, long size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        Page<SysOperationLog> p = new Page<>(page, size);
        Page<SysOperationLog> result = operationLogMapper.selectPage(
                p,
                new LambdaQueryWrapper<SysOperationLog>().orderByDesc(SysOperationLog::getOperationAt));
        PageResult<SysOperationLog> pr = new PageResult<>();
        pr.setRecords(result.getRecords());
        pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        return pr;
    }

    @Override
    public List<User> list() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        users.forEach(u -> {
            u.setPassword(null);
            u.setRoleIds(userRoleMapper.selectRoleIdsByUserId(u.getId()));
        });
        return users;
    }

    @Override
    public User getCurrentUser() {
        User u = SecurityUtil.currentUser();
        permissionService.enrichUser(u);
        u.setPassword(null);
        u.setRoleIds(userRoleMapper.selectRoleIdsByUserId(u.getId()));
        return u;
    }

    @Override
    public User create(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        syncRolesFromRequest(user);
        user.setPassword(null);
        user.setRoleIds(userRoleMapper.selectRoleIdsByUserId(user.getId()));
        return user;
    }

    @Override
    public User update(Long id, User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        userMapper.updateById(user);
        syncRolesFromRequest(user);
        user.setPassword(null);
        user.setRoleIds(userRoleMapper.selectRoleIdsByUserId(id));
        return user;
    }

    @Override
    public void delete(Long id) {
        User u = new User();
        u.setId(id);
        u.setStatus(0);
        userMapper.updateById(u);
    }

    /** Prefer roleIds from body; else map legacy role ENUM to a single sys_role. */
    private void syncRolesFromRequest(User user) {
        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            permissionService.assignUserRoles(user.getId(), user.getRoleIds());
            return;
        }
        if (user.getRole() != null && !user.getRole().isBlank()) {
            SysRole role = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleKey, user.getRole().trim().toLowerCase()));
            if (role != null) {
                permissionService.assignUserRoles(user.getId(), List.of(role.getRoleId()));
            }
        }
    }
}
