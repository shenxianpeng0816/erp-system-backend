package com.erp.service;

import com.erp.common.dto.PageResult;
import com.erp.entity.SysOperationLog;
import com.erp.entity.User;

import java.util.List;

public interface UserAdminService {

    List<User> listByRole(String role);

    PageResult<SysOperationLog> operationLogs(long page, long size);

    List<User> list();

    User getCurrentUser();

    User create(User user);

    User update(Long id, User user);

    void delete(Long id);
}
