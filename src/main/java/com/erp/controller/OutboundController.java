package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.result.Result;
import com.erp.common.exception.BusinessException;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final SalesOrderMapper orderMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public Result<List<OutboundOrder>> list() {
        return Result.success(outboundMapper.selectList(
                new LambdaQueryWrapper<OutboundOrder>().orderByDesc(OutboundOrder::getCreatedAt)));
    }

    @GetMapping("/{id}")
    public Result<OutboundOrder> detail(@PathVariable Long id) {
        return Result.success(outboundMapper.selectById(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<OutboundItem>> items(@PathVariable Long id) {
        return Result.success(outboundItemMapper.findWithProductByOutboundId(id));
    }

    /** Warehouse marks as printed (ready to ship) */
    @PostMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public Result<OutboundOrder> print(@PathVariable Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) throw new BusinessException("Delivery note not found");
        dn.setStatus("PRINTED");
        outboundMapper.updateById(dn);
        return Result.success(dn);
    }

    /** Warehouse executes outbound → deduct inventory + write log */
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @Transactional
    public Result<OutboundOrder> ship(@PathVariable Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) throw new BusinessException("Delivery note not found");
        if (!"PRINTED".equals(dn.getStatus()) && !"PENDING".equals(dn.getStatus()))
            throw new BusinessException("Invalid status for shipping");

        Long operatorId = SecurityUtil.currentUserId();

        List<OutboundItem> items = outboundItemMapper.selectList(
                new LambdaQueryWrapper<OutboundItem>().eq(OutboundItem::getOutboundId, id));

        for (OutboundItem item : items) {
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getProductId, item.getProductId()));
            if (inv == null)
                throw new BusinessException("No inventory record for product: " + item.getProductId());
            if (inv.getQty() < item.getQty())
                throw new BusinessException("Insufficient stock for product: " + item.getProductId());

            int qtyBefore = inv.getQty();
            inv.setQty(qtyBefore - item.getQty());
            inventoryMapper.updateById(inv);

            // ── Write inventory log ──────────────────────────────────────────
            InventoryLog log = new InventoryLog();
            log.setProductId(item.getProductId());
            log.setType("OUTBOUND");
            log.setQtyChange(-item.getQty());          // negative = out
            log.setQtyAfter(inv.getQty());
            log.setRefId(id);
            log.setRefType("OUTBOUND");
            log.setOperatorId(operatorId);
            log.setRemark("Outbound: " + dn.getOutboundNo());
            inventoryLogMapper.insert(log);
        }

        dn.setStatus("SHIPPED");
        dn.setOperatorId(operatorId);
        dn.setShippedAt(LocalDateTime.now());
        outboundMapper.updateById(dn);

        // Update sales order status to SHIPPED
        SalesOrder order = orderMapper.selectById(dn.getOrderId());
        if (order != null) {
            order.setStatus("SHIPPED");
            orderMapper.updateById(order);
        }
        return Result.success(dn);
    }
}
