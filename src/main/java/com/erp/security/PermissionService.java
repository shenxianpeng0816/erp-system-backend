package com.erp.security;

import com.erp.entity.SysRole;
import com.erp.entity.User;
import com.erp.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * RuoYi-style permission bean for {@code @PreAuthorize("@ss.hasPermi('erp:order:add')")}.
 */
@Service("ss")
public class PermissionService {

    private static final String ALL = "*:*:*";

    public boolean hasPermi(String permission) {
        if (!StringUtils.hasText(permission)) return false;
        User user = SecurityUtil.currentUser();
        Set<String> perms = user.getPermissions();
        if (CollectionUtils.isEmpty(perms)) {
            // Admin legacy fallback
            return "ADMIN".equalsIgnoreCase(user.getRole());
        }
        return perms.contains(ALL) || perms.contains(permission.trim());
    }

    public boolean hasAnyPermi(String permissions) {
        if (!StringUtils.hasText(permissions)) return false;
        for (String p : permissions.split(",")) {
            if (hasPermi(p.trim())) return true;
        }
        return false;
    }

    /** roleKey without ROLE_ prefix, e.g. admin / sales */
    public boolean hasRole(String role) {
        if (!StringUtils.hasText(role)) return false;
        User user = SecurityUtil.currentUser();
        String want = role.trim().toLowerCase();
        if (user.getRoles() != null) {
            for (SysRole r : user.getRoles()) {
                if (r.getRoleKey() != null && want.equalsIgnoreCase(r.getRoleKey())) {
                    return true;
                }
                if ("admin".equalsIgnoreCase(r.getRoleKey())) {
                    return true;
                }
            }
        }
        return user.getRole() != null && want.equalsIgnoreCase(user.getRole());
    }

    public boolean hasAnyRoles(String roles) {
        if (!StringUtils.hasText(roles)) return false;
        for (String r : roles.split(",")) {
            if (hasRole(r.trim())) return true;
        }
        return false;
    }
}
