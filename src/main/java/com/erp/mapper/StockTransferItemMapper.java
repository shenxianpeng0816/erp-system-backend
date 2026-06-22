package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.entity.StockTransferItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockTransferItemMapper extends BaseMapper<StockTransferItem> {

    @Select("""
            SELECT sti.*, p.name AS productName, p.product_no AS productNo
            FROM stock_transfer_item sti
            INNER JOIN product p ON p.id = sti.product_id
            WHERE sti.transfer_id = #{transferId}
            ORDER BY sti.id
            """)
    List<StockTransferItem> findWithProductByTransferId(@Param("transferId") Long transferId);
}
