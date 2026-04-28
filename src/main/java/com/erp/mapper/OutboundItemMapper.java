package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.OutboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutboundItemMapper extends BaseMapper<OutboundItem> {

    /**
     * Fetch outbound items with product info joined for print pages.
     */
    @Select("""
            SELECT oi.id, oi.outbound_id, oi.product_id, oi.qty,
                   p.name AS product_name, p.product_no, p.spec, p.unit
            FROM outbound_item oi
            JOIN product p ON p.id = oi.product_id
            WHERE oi.outbound_id = #{outboundId}
            ORDER BY oi.id
            """)
    List<OutboundItem> findWithProductByOutboundId(@Param("outboundId") Long outboundId);
}
