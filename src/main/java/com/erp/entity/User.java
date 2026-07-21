package com.erp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@TableName("user")
public class User implements UserDetails {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    /** Legacy primary role ENUM: ADMIN / SALES / FINANCE / WAREHOUSE / INBOUND */
    private String role;

    /** 1=active, 0=disabled (soft delete) */
    @TableLogic(value = "1", delval = "0")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** RBAC roles (not a DB column) */
    @TableField(exist = false)
    private List<SysRole> roles = new ArrayList<>();

    /** Permission strings e.g. erp:order:add (not a DB column) */
    @TableField(exist = false)
    private Set<String> permissions = new HashSet<>();

    /** Role ids for admin UI (not a DB column) */
    @TableField(exist = false)
    private List<Long> roleIds;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        }
        if (roles != null) {
            for (SysRole r : roles) {
                if (r.getRoleKey() != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r.getRoleKey().toUpperCase()));
                }
            }
        }
        if (permissions != null) {
            for (String p : permissions) {
                if (p != null && !p.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(p));
                }
            }
        }
        return authorities;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return status != null && status == 1; }
}
