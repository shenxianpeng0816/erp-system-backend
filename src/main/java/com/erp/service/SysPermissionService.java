package com.erp.service;

import com.erp.dto.request.SaveRoleRequest;
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

    /** Include disabled roles (admin UI). */
    List<SysRole> listRolesAll();

    SysRole createRole(SaveRoleRequest req);

    SysRole updateRole(Long roleId, SaveRoleRequest req);

    /** Soft-disable role (status=1). Built-in admin cannot be disabled. */
    void disableRole(Long roleId);

    void assignUserRoles(Long userId, List<Long> roleIds);

    void assignRoleMenus(Long roleId, List<Long> menuIds);

    List<SysMenu> listAllMenusTree();
}
