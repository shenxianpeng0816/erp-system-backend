package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.dto.request.LoginRequest;
import com.erp.dto.response.LoginResponse;
import com.erp.entity.SysMenu;
import com.erp.entity.User;
import com.erp.service.SysPermissionService;
import com.erp.util.JwtUtil;
import com.erp.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final SysPermissionService permissionService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            User user = (User) auth.getPrincipal();
            permissionService.enrichUser(user);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            return Result.success(buildLoginResponse(user, token));
        } catch (BadCredentialsException e) {
            return Result.fail("Incorrect username or password");
        }
    }

    /** Refresh roles / permissions / menus for current session (after role change). */
    @GetMapping("/getInfo")
    public Result<LoginResponse> getInfo() {
        User user = SecurityUtil.currentUser();
        permissionService.enrichUser(user);
        return Result.success(buildLoginResponse(user, null));
    }

    /** Sidebar routers only. */
    @GetMapping("/getRouters")
    public Result<List<SysMenu>> getRouters() {
        return Result.success(permissionService.getRouters(SecurityUtil.currentUserId()));
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        Set<String> roleKeys = user.getRoles() == null ? new LinkedHashSet<>()
                : user.getRoles().stream()
                .map(r -> r.getRoleKey())
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleKeys.isEmpty() && user.getRole() != null) {
            roleKeys.add(user.getRole().toLowerCase());
        }
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .roles(roleKeys)
                .permissions(user.getPermissions() != null ? user.getPermissions() : Set.of())
                .menus(permissionService.getRouters(user.getId()))
                .build();
    }
}
