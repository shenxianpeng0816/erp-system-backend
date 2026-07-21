package com.erp.service;

import com.erp.entity.SysMenu;
import com.erp.entity.SysRole;
import com.erp.entity.User;

import java.util.List;
import java.util.Set;

public interface SysPermissionService {

    /** Load roles + permission strings onto the User (for Spring Security authorities). */
    void enrichUser(User user);

    Set<String> getRoleKeys(Long userId);

    Set<String> getMenuPermissions(Long userId);

    /** Sidebar menus (type M/C only, visible), as a tree. */
    List<SysMenu> getRouters(Long userId);

    List<SysRole> listRoles();

    void assignUserRoles(Long userId, List<Long> roleIds);

    void assignRoleMenus(Long roleId, List<Long> menuIds);

    List<SysMenu> listAllMenusTree();
}
