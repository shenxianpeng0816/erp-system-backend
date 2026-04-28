package com.erp.controller;

import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.mapper.CustomerMapper;
import com.erp.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;

    /** Auto-complete search by name or customer_no */
    @GetMapping("/search")
    public Result<List<Customer>> search(@RequestParam String keyword) {
        return Result.success(customerMapper.searchByKeyword(keyword));
    }

    @GetMapping
    public Result<List<Customer>> list() {
        return Result.success(customerMapper.selectList(
                new LambdaQueryWrapper<Customer>().eq(Customer::getStatus, 1)
                        .orderByAsc(Customer::getCustomerNo)));
    }

    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.success(customerMapper.selectById(id));
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
        customer.setId(id);
        customerMapper.updateById(customer);
        return Result.success(customer);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> delete(@PathVariable Long id) {
        Customer c = new Customer();
        c.setId(id);
        c.setStatus(0);
        customerMapper.updateById(c);
        return Result.success();
    }
}
