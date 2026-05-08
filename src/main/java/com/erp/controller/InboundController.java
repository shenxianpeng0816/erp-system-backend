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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundOrderMapper inboundMapper;
    private final InboundItemMapper inboundItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final UserMapper userMapper;
    private final DocSequenceService docSequenceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<List<InboundOrder>> list() {
        List<InboundOrder> list = inboundMapper.selectList(
                new LambdaQueryWrapper<InboundOrder>().orderByDesc(InboundOrder::getCreatedAt));
        enrichOperatorNames(list);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<InboundOrder> detail(@PathVariable Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order != null) enrichOperatorName(order);
        return Result.success(order);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<List<InboundItem>> items(@PathVariable Long id) {
        return Result.success(inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND')")
    @Transactional
    public Result<InboundOrder> create(@RequestBody CreateInboundRequest req) {
        if (req.getOperatorId() == null) {
            throw new BusinessException("Inbound operator is required");
        }
        User operator = userMapper.selectById(req.getOperatorId());
        if (operator == null || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new BusinessException("Invalid inbound operator");
        }
        if (!"INBOUND".equals(operator.getRole())) {
            throw new BusinessException("Operator must be an INBOUND user");
        }

        InboundOrder order = new InboundOrder();
        order.setInboundNo(docSequenceService.nextDocNo("IN"));
        order.setSupplier(req.getSupplier());
        order.setOperatorId(req.getOperatorId());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        if (req.getDocumentUrl() != null && !req.getDocumentUrl().isBlank()) {
            order.setDocumentUrl(req.getDocumentUrl().trim());
        }
        inboundMapper.insert(order);

        for (CreateInboundRequest.ItemReq i : req.getItems()) {
            InboundItem item = new InboundItem();
            item.setInboundId(order.getId());
            item.setProductId(i.getProductId());
            item.setQty(i.getQty());
            item.setUnitCost(i.getUnitCost());
            inboundItemMapper.insert(item);
        }
        enrichOperatorName(order);
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND')")
    @Transactional
    public Result<InboundOrder> update(@PathVariable Long id, @RequestBody CreateInboundRequest req) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) throw new BusinessException("Inbound order not found");
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("Only draft inbound orders can be edited");

        if (req.getOperatorId() == null) {
            throw new BusinessException("Inbound operator is required");
        }
        User operator = userMapper.selectById(req.getOperatorId());
        if (operator == null || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new BusinessException("Invalid inbound operator");
        }
        if (!"INBOUND".equals(operator.getRole())) {
            throw new BusinessException("Operator must be an INBOUND user");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException("At least one line item is required");
        }

        order.setSupplier(req.getSupplier());
        order.setOperatorId(req.getOperatorId());
        order.setRemark(req.getRemark());
        if (req.getDocumentUrl() != null && !req.getDocumentUrl().isBlank()) {
            order.setDocumentUrl(req.getDocumentUrl().trim());
        } else {
            order.setDocumentUrl(null);
        }
        inboundMapper.updateById(order);

        inboundItemMapper.delete(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id));
        for (CreateInboundRequest.ItemReq i : req.getItems()) {
            InboundItem item = new InboundItem();
            item.setInboundId(id);
            item.setProductId(i.getProductId());
            item.setQty(i.getQty());
            item.setUnitCost(i.getUnitCost());
            inboundItemMapper.insert(item);
        }

        InboundOrder updated = inboundMapper.selectById(id);
        enrichOperatorName(updated);
        return Result.success(updated);
    }

    private void enrichOperatorNames(List<InboundOrder> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> ids = list.stream()
                .map(InboundOrder::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, ids));
        Map<Long, String> names = new HashMap<>();
        for (User u : users) {
            String n = (u.getRealName() != null && !u.getRealName().isBlank())
                    ? u.getRealName()
                    : u.getUsername();
            names.put(u.getId(), n);
        }
        for (InboundOrder o : list) {
            if (o.getOperatorId() != null) {
                o.setOperatorName(names.get(o.getOperatorId()));
            }
        }
    }

    private void enrichOperatorName(InboundOrder order) {
        if (order == null || order.getOperatorId() == null) return;
        User u = userMapper.selectById(order.getOperatorId());
        if (u != null) {
            order.setOperatorName((u.getRealName() != null && !u.getRealName().isBlank())
                    ? u.getRealName()
                    : u.getUsername());
        }
    }

    @Data
    public static class CreateInboundRequest {
        private String supplier;
        private String remark;
        /** Must reference an active user with role INBOUND */
        private Long operatorId;
        /** Optional — delivery note / signed receipt image URL from /files/upload */
        private String documentUrl;
        private List<ItemReq> items;

        @Data
        public static class ItemReq {
            private Long productId;
            private Integer qty;
            private java.math.BigDecimal unitCost;
        }
    }
}
