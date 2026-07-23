package com.erp.controller;

import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.entity.Customer;
import com.erp.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** Active customers only; SALES scope = own {@code created_by} */
    @GetMapping("/search")
    @PreAuthorize("@ss.hasPermi('erp:customer:list')")
    public Result<List<Customer>> search(@RequestParam String keyword) {
        return Result.success(customerService.search(keyword));
    }

    /**
     * Paginated customer list. Default {@code status = 1} only.
     * SALES: only rows they created. ADMIN/FINANCE: all active (FINANCE) or all + deleted with {@code includeDeleted=true} (ADMIN).
     * Optional {@code createdBy} filters by creator (ignored for SALES — always scoped to self).
     */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('erp:customer:list')")
    public Result<PageResult<Customer>> list(
            @RequestParam(required = false) Boolean includeDeleted,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(customerService.pageList(includeDeleted, createdBy, keyword, page, size));
    }

    /** Active customers for order form dropdowns (no pagination). */
    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermi('erp:customer:list')")
    public Result<List<Customer>> options() {
        return Result.success(customerService.listOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:customer:list')")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.success(customerService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('erp:customer:add')")
    public Result<Customer> create(@RequestBody Customer customer) {
        return Result.success(customerService.create(customer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:customer:edit')")
    public Result<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        return Result.success(customerService.update(id, customer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('erp:customer:remove')")
    public Result<?> delete(@PathVariable Long id) {
        customerService.softDelete(id);
        return Result.success();
    }
}
