package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.SaveRoleRequest;
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
    public List<SysRole> listRolesAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getRoleSort, SysRole::getRoleId));
    }

    @Override
    @Transactional
    public SysRole createRole(SaveRoleRequest req) {
        String key = normalizeRoleKey(req.getRoleKey());
        assertRoleKeyAvailable(key, null);
        SysRole role = new SysRole();
        role.setRoleName(req.getRoleName().trim());
        role.setRoleKey(key);
        role.setRoleSort(req.getRoleSort() != null ? req.getRoleSort() : nextSort());
        role.setStatus(normalizeStatus(req.getStatus()));
        role.setRemark(blankToNull(req.getRemark()));
        roleMapper.insert(role);
        return roleMapper.selectById(role.getRoleId());
    }

    @Override
    @Transactional
    public SysRole updateRole(Long roleId, SaveRoleRequest req) {
        SysRole existing = requireRole(roleId);
        String key = normalizeRoleKey(req.getRoleKey());
        // Built-in admin key is locked
        if ("admin".equalsIgnoreCase(existing.getRoleKey()) && !"admin".equals(key)) {
            throw new BusinessException("Cannot change the administrator role key");
        }
        assertRoleKeyAvailable(key, roleId);
        existing.setRoleName(req.getRoleName().trim());
        existing.setRoleKey(key);
        if (req.getRoleSort() != null) {
            existing.setRoleSort(req.getRoleSort());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if ("admin".equalsIgnoreCase(existing.getRoleKey()) && "1".equals(normalizeStatus(req.getStatus()))) {
                throw new BusinessException("Cannot disable the administrator role");
            }
            existing.setStatus(normalizeStatus(req.getStatus()));
        }
        existing.setRemark(blankToNull(req.getRemark()));
        roleMapper.updateById(existing);
        return roleMapper.selectById(roleId);
    }

    @Override
    @Transactional
    public void disableRole(Long roleId) {
        SysRole existing = requireRole(roleId);
        if ("admin".equalsIgnoreCase(existing.getRoleKey())) {
            throw new BusinessException("Cannot disable the administrator role");
        }
        existing.setStatus("1");
        roleMapper.updateById(existing);
        // Drop role-menu bindings so disabled roles grant nothing if somehow re-linked
        roleMenuMapper.deleteByRoleId(roleId);
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("Role not found");
        }
        return role;
    }

    private String normalizeRoleKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Role key is required");
        }
        String key = raw.trim().toLowerCase();
        if (!key.matches("^[a-z][a-z0-9_]{0,49}$")) {
            throw new BusinessException("Role key must start with a letter and contain only a-z, 0-9, underscore");
        }
        return key;
    }

    private void assertRoleKeyAvailable(String key, Long excludeRoleId) {
        LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, key);
        if (excludeRoleId != null) {
            q.ne(SysRole::getRoleId, excludeRoleId);
        }
        if (roleMapper.selectCount(q) > 0) {
            throw new BusinessException("Role key already exists: " + key);
        }
    }

    private int nextSort() {
        List<SysRole> all = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByDesc(SysRole::getRoleSort)
                .last("LIMIT 1"));
        if (all.isEmpty() || all.get(0).getRoleSort() == null) return 10;
        return all.get(0).getRoleSort() + 1;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "0";
        return "1".equals(status.trim()) ? "1" : "0";
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
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
