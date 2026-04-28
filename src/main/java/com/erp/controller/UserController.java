package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.result.Result;
import com.erp.entity.User;
import com.erp.mapper.UserMapper;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        users.forEach(u -> u.setPassword(null)); // mask password
        return Result.success(users);
    }

    @GetMapping("/me")
    public Result<User> me() {
        User u = SecurityUtil.currentUser();
        u.setPassword(null);
        return Result.success(u);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> create(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        userMapper.updateById(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable Long id) {
        User u = new User();
        u.setId(id);
        u.setStatus(0);
        userMapper.updateById(u);
        return Result.success();
    }
}
