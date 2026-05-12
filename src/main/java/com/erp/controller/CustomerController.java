package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.mapper.CustomerMapper;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;

    /** Auto-complete search by name or customer_no — active customers only */
    @GetMapping("/search")
    public Result<List<Customer>> search(@RequestParam String keyword) {
        return Result.success(customerMapper.searchByKeyword(keyword));
    }

    /**
     * List customers. Default: {@code status = 1} only (dropdowns & all normal list UIs).
     * ADMIN may pass {@code includeDeleted=true} to include logically deleted rows (customer admin screen).
     */
    @GetMapping
    public Result<List<Customer>> list(@RequestParam(required = false) Boolean includeDeleted) {
        LambdaQueryWrapper<Customer> q = new LambdaQueryWrapper<>();
        boolean admin = "ADMIN".equals(SecurityUtil.currentRole());
        if (!admin || !Boolean.TRUE.equals(includeDeleted)) {
            q.eq(Customer::getStatus, 1);
        }
        q.orderByDesc(Customer::getStatus).orderByAsc(Customer::getCustomerNo);
        return Result.success(customerMapper.selectList(q));
    }

    /** Detail: non-admin cannot load logically deleted customers */
    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("Customer not found");
        }
        if (!"ADMIN".equals(SecurityUtil.currentRole())) {
            if (c.getStatus() == null || c.getStatus() != 1) {
                throw new BusinessException("Customer not found");
            }
        }
        return Result.success(c);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<Customer> create(@RequestBody Customer customer) {
        customer.setCreatedBy(SecurityUtil.currentUserId());
        customer.setStatus(1);
        customerMapper.insert(customer);
        return Result.success(customer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public Result<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        Customer existing = customerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Customer not found");
        }
        if (!"ADMIN".equals(SecurityUtil.currentRole())) {
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                throw new BusinessException("Cannot update deleted customer");
            }
        }
        customer.setId(id);
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
}
