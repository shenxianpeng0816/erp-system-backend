package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.entity.*;
import com.erp.mapper.SysMenuMapper;
import com.erp.mapper.SysRoleMapper;
import com.erp.mapper.SysRoleMenuMapper;
import com.erp.mapper.SysUserRoleMapper;
import com.erp.mapper.UserMapper;
import com.erp.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final UserMapper userMapper;

    @Override
    public void enrichUser(User user) {
        if (user == null || user.getId() == null) return;
        List<SysRole> roles = roleMapper.selectRolesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            // Fallback: derive from legacy user.role ENUM
            roles = fallbackRolesFromLegacy(user.getRole());
        }
        user.setRoles(roles);
        Set<String> perms = new LinkedHashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        if (perms.isEmpty() && roles.stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getRoleKey()))) {
            perms.add("*:*:*");
        }
        // If no RBAC rows yet, grant nothing extra — ROLE_ still comes from user.role
        user.setPermissions(perms);
    }

    private List<SysRole> fallbackRolesFromLegacy(String legacyRole) {
        if (legacyRole == null || legacyRole.isBlank()) return List.of();
        String key = legacyRole.trim().toLowerCase();
        SysRole r = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, key));
        return r != null ? List.of(r) : List.of();
    }

    @Override
    public Set<String> getRoleKeys(Long userId) {
        return roleMapper.selectRolesByUserId(userId).stream()
                .map(SysRole::getRoleKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<String> getMenuPermissions(Long userId) {
        List<String> list = menuMapper.selectPermsByUserId(userId);
        Set<String> set = new LinkedHashSet<>(list);
        if (getRoleKeys(userId).stream().anyMatch("admin"::equalsIgnoreCase)) {
            set.add("*:*:*");
        }
        return set;
    }

    @Override
    public List<SysMenu> getRouters(Long userId) {
        List<SysMenu> all = menuMapper.selectMenusByUserId(userId);
        List<SysMenu> nav = all.stream()
                .filter(m -> "M".equals(m.getMenuType()) || "C".equals(m.getMenuType()))
                .filter(m -> "0".equals(m.getVisible()))
                .collect(Collectors.toList());
        return buildTree(nav, 0L);
    }

    @Override
    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, "0")
                .orderByAsc(SysRole::getRoleSort));
    }

    @Override
    @Transactional
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId != null) {
                    userRoleMapper.insert(new SysUserRole(userId, roleId));
                }
            }
        }
        // Keep legacy user.role in sync (primary = first role)
        if (roleIds != null && !roleIds.isEmpty()) {
            SysRole primary = roleMapper.selectById(roleIds.get(0));
            if (primary != null && primary.getRoleKey() != null) {
                User u = new User();
                u.setId(userId);
                u.setRole(primary.getRoleKey().toUpperCase());
                userMapper.updateById(u);
            }
        }
    }

    @Override
    @Transactional
    public void assignRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                if (menuId != null) {
                    roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
                }
            }
        }
    }

    @Override
    public List<SysMenu> listAllMenusTree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum));
        return buildTree(all, 0L);
    }

    private List<SysMenu> buildTree(List<SysMenu> menus, Long parentId) {
        List<SysMenu> tree = new ArrayList<>();
        for (SysMenu m : menus) {
            Long pid = m.getParentId() == null ? 0L : m.getParentId();
            if (Objects.equals(pid, parentId)) {
                m.setChildren(buildTree(menus, m.getMenuId()));
                tree.add(m);
            }
        }
        return tree;
    }
}
