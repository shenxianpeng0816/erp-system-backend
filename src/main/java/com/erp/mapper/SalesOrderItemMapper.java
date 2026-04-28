package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.SalesOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {

    /**
     * Fetch order items with product info joined — used for order detail and invoice print.
     */
    @Select("""
            SELECT soi.id, soi.order_id, soi.product_id, soi.qty,
                   soi.unit_price, soi.total, soi.remark,
                   p.name AS product_name, p.product_no, p.spec, p.unit
            FROM sales_order_item soi
            JOIN product p ON p.id = soi.product_id
            WHERE soi.order_id = #{orderId}
            ORDER BY soi.id
            """)
    List<SalesOrderItem> findWithProductByOrderId(@Param("orderId") Long orderId);
}
