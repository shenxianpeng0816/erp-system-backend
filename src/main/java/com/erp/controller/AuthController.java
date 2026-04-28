package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.dto.request.LoginRequest;
import com.erp.dto.response.LoginResponse;
import com.erp.entity.User;
import com.erp.mapper.UserMapper;
import com.erp.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        User user = (User) auth.getPrincipal();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return Result.success(LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build());
    }
}
