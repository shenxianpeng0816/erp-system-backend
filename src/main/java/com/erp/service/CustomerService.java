package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.entity.Customer;

import java.util.List;

public interface CustomerService {

    List<Customer> search(String keyword);

    PageResult<Customer> pageList(Boolean includeDeleted, Long createdBy, String keyword, long page, long size);

    List<Customer> listOptions();

    Customer getById(Long id);

    Customer create(Customer customer);

    Customer update(Long id, Customer customer);

    void softDelete(Long id);
}
