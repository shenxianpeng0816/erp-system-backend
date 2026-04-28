package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    @Select("SELECT id, customer_no, name, type, phone, is_pickup_point FROM customer WHERE status = 1 AND (name LIKE CONCAT('%',#{keyword},'%') OR customer_no LIKE CONCAT('%',#{keyword},'%')) LIMIT 20")
    List<Customer> searchByKeyword(String keyword);
}
