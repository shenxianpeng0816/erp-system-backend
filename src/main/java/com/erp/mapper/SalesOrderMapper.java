package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    @Select("SELECT * FROM sales_order WHERE sales_user_id = #{userId} ORDER BY created_at DESC")
    List<SalesOrder> findBySalesUser(Long userId);

    @Select("SELECT * FROM sales_order WHERE status = 'PENDING_APPROVAL' ORDER BY created_at ASC")
    List<SalesOrder> findPendingApproval();

    @Select("SELECT * FROM sales_order ORDER BY created_at DESC")
    List<SalesOrder> findAll();
}
