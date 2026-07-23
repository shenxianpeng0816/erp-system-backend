package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.entity.Customer;
import com.erp.entity.User;
import com.erp.mapper.CustomerMapper;
import com.erp.mapper.UserMapper;
import com.erp.service.CustomerService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    @Override
    public List<Customer> search(String keyword) {
        Long createdByFilter = "SALES".equals(SecurityUtil.currentRole()) ? SecurityUtil.currentUserId() : null;
        return customerMapper.searchByKeyword(keyword, createdByFilter);
    }

    @Override
    public PageResult<Customer> pageList(Boolean includeDeleted, Long createdBy, String keyword,
                                         long page, long size) {
        LambdaQueryWrapper<Customer> q = customerQuery(includeDeleted, createdBy, keyword);
        Page<Customer> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<Customer> result = customerMapper.selectPage(p, q);
        enrichCreatedByUsernames(result.getRecords());
        return PageQuery.from(result);
    }

    @Override
    public List<Customer> listOptions() {
        return customerMapper.selectList(customerQuery(false, null, null));
    }

    @Override
    public Customer getById(Long id) {
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
        return c;
    }

    @Override
    public Customer create(Customer customer) {
        customer.setCreatedBy(SecurityUtil.currentUserId());
        customer.setStatus(1);
        customerMapper.insert(customer);
        return customer;
    }

    @Override
    public Customer update(Long id, Customer customer) {
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
        return customerMapper.selectById(id);
    }

    @Override
    public void softDelete(Long id) {
        Customer existing = customerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("Customer not found");
        }
        if (existing.getStatus() != null && existing.getStatus() == 0) {
            return;
        }
        customerMapper.update(
                null,
                new LambdaUpdateWrapper<Customer>().eq(Customer::getId, id).set(Customer::getStatus, 0));
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
            q.and(w -> w.likeRight(Customer::getName, kw).or().likeRight(Customer::getCustomerNo, kw));
        }

        q.orderByDesc(Customer::getStatus).orderByAsc(Customer::getCustomerNo);
        return q;
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
