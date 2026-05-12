package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    @Select("<script>"
            + "SELECT id, customer_no, name, type, phone, is_pickup_point FROM customer WHERE status = 1 "
            + "AND (name LIKE CONCAT('%',#{keyword},'%') OR customer_no LIKE CONCAT('%',#{keyword},'%')) "
            + "<if test='createdByFilter != null'>AND created_by = #{createdByFilter}</if> "
            + "LIMIT 20"
            + "</script>")
    List<Customer> searchByKeyword(@Param("keyword") String keyword, @Param("createdByFilter") Long createdByFilter);
}
