package com.erp.util;

import com.erp.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static User currentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof User u) return u;
        throw new RuntimeException("Not authenticated");
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    public static String currentRole() {
        return currentUser().getRole();
    }
}
