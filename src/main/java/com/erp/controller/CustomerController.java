package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.entity.User;
import com.erp.mapper.CustomerMapper;
import com.erp.mapper.UserMapper;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    /** Active customers only; SALES scope = own {@code created_by} */
    @GetMapping("/search")
    public Result<List<Customer>> search(@RequestParam String keyword) {
        Long createdByFilter = "SALES".equals(SecurityUtil.currentRole()) ? SecurityUtil.currentUserId() : null;
        return Result.success(customerMapper.searchByKeyword(keyword, createdByFilter));
    }

    /**
     * Paginated customer list. Default {@code status = 1} only.
     * SALES: only rows they created. ADMIN/FINANCE: all active (FINANCE) or all + deleted with {@code includeDeleted=true} (ADMIN).
     * Optional {@code createdBy} filters by creator (ignored for SALES — always scoped to self).
     */
    @GetMapping
    public Result<PageResult<Customer>> list(
            @RequestParam(required = false) Boolean includeDeleted,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Customer> q = customerQuery(includeDeleted, createdBy, keyword);
        Page<Customer> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<Customer> result = customerMapper.selectPage(p, q);
        enrichCreatedByUsernames(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    /** Active customers for order form dropdowns (no pagination). */
    @GetMapping("/options")
    public Result<List<Customer>> options() {
        List<Customer> list = customerMapper.selectList(customerQuery(false, null, null));
        return Result.success(list);
    }

    private LambdaQueryWrapper<Customer> customerQuery(Boolean includeDeleted, Long createdBy, String keyword) {
        LambdaQueryWrapper<Customer> q = new LambdaQueryWrapper<>();
        String role = SecurityUtil.currentRole();
        boolean admin = "ADMIN".equals(role);

        if ("SALES".equals(role)) {
            q.eq(Customer::getCreatedBy, SecurityUtil.currentUserId());
        } else if (createdBy != null) {
            q.eq(Customer::getCreatedBy, createdBy);
        }

        if (!admin || !Boolean.TRUE.equals(includeDeleted)) {
            q.eq(Customer::getStatus, 1);
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(Customer::getName, kw).or().like(Customer::getCustomerNo, kw));
        }

        q.orderByDesc(Customer::getStatus).orderByAsc(Customer::getCustomerNo);
        return q;
    }

    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("Customer not found");
        }
        String role = SecurityUtil.currentRole();
        if ("SALES".equals(role)) {
            if (c.getCreatedBy() == null || !c.getCreatedBy().equals(SecurityUtil.currentUserId())) {
                throw new BusinessException("Customer not found");
            }
            if (c.getStatus() == null || c.getStatus() != 1) {
                throw new BusinessException("Customer not found");
            }
        } else if (!"ADMIN".equals(role)) {
            if (c.getStatus() == null || c.getStatus() != 1) {
                throw new BusinessException("Customer not found");
            }
        }
        return Result.success(c);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','SALES')")
    public Result<Customer> create(@RequestBody Customer customer) {
        customer.setCreatedBy(SecurityUtil.currentUserId());
        customer.setStatus(1);
        customerMapper.insert(customer);
        return Result.success(customer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','SALES')")
    public Result<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        Customer existing = customerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Customer not found");
        }
        String role = SecurityUtil.currentRole();
        if ("SALES".equals(role)) {
            if (existing.getCreatedBy() == null || !existing.getCreatedBy().equals(SecurityUtil.currentUserId())) {
                throw new BusinessException("Access denied");
            }
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                throw new BusinessException("Cannot update deleted customer");
            }
        } else if (!"ADMIN".equals(role)) {
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                throw new BusinessException("Cannot update deleted customer");
            }
        }

        customer.setId(id);
        if (!"ADMIN".equals(role)) {
            customer.setCreatedBy(existing.getCreatedBy());
        }
        customerMapper.updateById(customer);
        return Result.success(customerMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable Long id) {
        Customer existing = customerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Customer not found");
        }
        if (existing.getStatus() != null && existing.getStatus() == 0) {
            return Result.success();
        }
        customerMapper.update(
                null,
                new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id).set(Customer::getStatus, 0));
        return Result.success();
    }

    private void enrichCreatedByUsernames(List<Customer> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> ids = list.stream()
                .map(Customer::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, ids));
        Map<Long, String> usernames = new HashMap<>();
        for (User u : users) {
            usernames.put(u.getId(), u.getUsername());
        }
        for (Customer c : list) {
            if (c.getCreatedBy() != null) {
                c.setCreatedByUsername(usernames.get(c.getCreatedBy()));
            }
        }
    }
}
