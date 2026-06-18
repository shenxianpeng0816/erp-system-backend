package com.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.dto.query.ReceivableFilterParams;
import com.erp.dto.query.ReceivableSummaryAgg;
import com.erp.entity.Receivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceivableMapper extends BaseMapper<Receivable> {

    @Select("""
            <script>
            SELECT COALESCE(SUM(r.balance), 0) AS total_outstanding,
                   COUNT(*) AS record_count
            FROM receivable r
            WHERE 1 = 1
            <if test="p.status != null and p.status != ''">
              AND r.status = #{p.status}
            </if>
            <if test="p.excludeSettledAndCancelled != null and p.excludeSettledAndCancelled">
              AND r.status NOT IN ('SETTLED', 'CANCELLED')
            </if>
            <if test="p.customerId != null">
              AND r.customer_id = #{p.customerId}
            </if>
            <if test="p.createdFrom != null">
              AND r.created_at &gt;= #{p.createdFrom}
            </if>
            <if test="p.createdToExclusive != null">
              AND r.created_at &lt; #{p.createdToExclusive}
            </if>
            <if test="p.customerName != null and p.customerName != ''">
              AND EXISTS (
                SELECT 1 FROM customer c
                WHERE c.id = r.customer_id
                  AND c.name LIKE CONCAT(#{p.customerName}, '%')
              )
            </if>
            <if test="p.salesUserName != null and p.salesUserName != ''">
              AND EXISTS (
                SELECT 1 FROM invoice i
                INNER JOIN sales_order o ON o.id = i.order_id
                INNER JOIN user u ON u.id = o.sales_user_id
                WHERE i.id = r.invoice_id
                  AND (u.real_name LIKE CONCAT(#{p.salesUserName}, '%')
                       OR u.username LIKE CONCAT(#{p.salesUserName}, '%'))
              )
            </if>
            <if test="p.productName != null and p.productName != ''">
              AND EXISTS (
                SELECT 1 FROM invoice i
                INNER JOIN sales_order_item soi ON soi.order_id = i.order_id
                INNER JOIN product p ON p.id = soi.product_id
                WHERE i.id = r.invoice_id AND p.name LIKE CONCAT(#{p.productName}, '%')
              )
            </if>
            </script>
            """)
    ReceivableSummaryAgg summarizeReceivables(@Param("p") ReceivableFilterParams params);
}
