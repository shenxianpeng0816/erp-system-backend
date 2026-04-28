package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {

    @Select("SELECT * FROM inventory_log WHERE product_id = #{productId} ORDER BY created_at DESC")
    List<InventoryLog> findByProduct(@Param("productId") Long productId);

    @Select("SELECT * FROM inventory_log WHERE ref_id = #{refId} AND ref_type = #{refType} ORDER BY created_at DESC")
    List<InventoryLog> findByRef(@Param("refId") Long refId, @Param("refType") String refType);
}
