package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.DocSequenceService;
import com.erp.util.SecurityUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundOrderMapper inboundMapper;
    private final InboundItemMapper inboundItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final DocSequenceService docSequenceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<List<InboundOrder>> list() {
        return Result.success(inboundMapper.selectList(
                new LambdaQueryWrapper<InboundOrder>().orderByDesc(InboundOrder::getCreatedAt)));
    }

    @GetMapping("/{id}")
    public Result<InboundOrder> detail(@PathVariable Long id) {
        return Result.success(inboundMapper.selectById(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<InboundItem>> items(@PathVariable Long id) {
        return Result.success(inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND')")
    @Transactional
    public Result<InboundOrder> create(@RequestBody CreateInboundRequest req) {
        InboundOrder order = new InboundOrder();
        order.setInboundNo(docSequenceService.nextDocNo("IN"));
        order.setSupplier(req.getSupplier());
        order.setOperatorId(SecurityUtil.currentUserId());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        inboundMapper.insert(order);

        for (CreateInboundRequest.ItemReq i : req.getItems()) {
            InboundItem item = new InboundItem();
            item.setInboundId(order.getId());
            item.setProductId(i.getProductId());
            item.setQty(i.getQty());
            item.setUnitCost(i.getUnitCost());
            inboundItemMapper.insert(item);
        }
        return Result.success(order);
    }

    /** Confirm inbound → add to inventory + write log */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND')")
    @Transactional
    public Result<InboundOrder> confirm(@PathVariable Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) throw new BusinessException("Inbound order not found");
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("Already confirmed");

        Long operatorId = SecurityUtil.currentUserId();

        List<InboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id));

        for (InboundItem item : items) {
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getProductId, item.getProductId()));

            int qtyAfter;
            if (inv == null) {
                inv = new Inventory();
                inv.setProductId(item.getProductId());
                inv.setQty(item.getQty());
                inv.setMinQty(0);
                inventoryMapper.insert(inv);
                qtyAfter = item.getQty();
            } else {
                inv.setQty(inv.getQty() + item.getQty());
                inventoryMapper.updateById(inv);
                qtyAfter = inv.getQty();
            }

            // ── Write inventory log ──────────────────────────────────────────
            InventoryLog log = new InventoryLog();
            log.setProductId(item.getProductId());
            log.setType("INBOUND");
            log.setQtyChange(item.getQty());            // positive = in
            log.setQtyAfter(qtyAfter);
            log.setRefId(id);
            log.setRefType("INBOUND");
            log.setOperatorId(operatorId);
            log.setRemark("Inbound: " + order.getInboundNo());
            inventoryLogMapper.insert(log);
        }

        order.setStatus("CONFIRMED");
        order.setInboundAt(LocalDateTime.now());
        inboundMapper.updateById(order);
        return Result.success(order);
    }

    @Data
    public static class CreateInboundRequest {
        private String supplier;
        private String remark;
        private List<ItemReq> items;

        @Data
        public static class ItemReq {
            private Long productId;
            private Integer qty;
            private java.math.BigDecimal unitCost;
        }
    }
}
