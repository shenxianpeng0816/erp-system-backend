package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.DocSequenceService;
import com.erp.service.InventoryService;
import com.erp.service.StockChangeContext;
import com.erp.service.WarehouseService;
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

    /** Users eligible as inbound document operator (WAREHOUSE aligned with INBOUND). */
    private static final Set<String> INBOUND_OPERATOR_ROLES = Set.of("INBOUND", "WAREHOUSE");

    private final InboundOrderMapper inboundMapper;
    private final InboundItemMapper inboundItemMapper;
    private final UserMapper userMapper;
    private final DocSequenceService docSequenceService;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final WarehouseMapper warehouseMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<PageResult<InboundOrder>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<InboundOrder> q = new LambdaQueryWrapper<InboundOrder>()
                .orderByDesc(InboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        Page<InboundOrder> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<InboundOrder> result = inboundMapper.selectPage(p, q);
        enrichOperatorNames(result.getRecords());
        enrichWarehouseNames(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    /** Export all inbound orders (no pagination). */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<List<InboundOrder>> export(@RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<InboundOrder> q = new LambdaQueryWrapper<InboundOrder>()
                .orderByDesc(InboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        List<InboundOrder> list = inboundMapper.selectList(q);
        enrichOperatorNames(list);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<InboundOrder> detail(@PathVariable Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order != null) {
            enrichOperatorName(order);
            enrichWarehouseNames(List.of(order));
        }
        return Result.success(order);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    public Result<List<InboundItem>> items(@PathVariable Long id) {
        return Result.success(inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    @Transactional
    public Result<InboundOrder> create(@RequestBody CreateInboundRequest req) {
        if (req.getOperatorId() == null) {
            throw new BusinessException("Inbound operator is required");
        }
        User operator = userMapper.selectById(req.getOperatorId());
        if (operator == null || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new BusinessException("Invalid inbound operator");
        }
        if (!INBOUND_OPERATOR_ROLES.contains(operator.getRole())) {
            throw new BusinessException("Operator must be a warehouse or inbound user");
        }
        if (req.getWarehouseId() == null) {
            throw new BusinessException("Target warehouse is required");
        }
        warehouseService.requireActive(req.getWarehouseId());

        InboundOrder order = new InboundOrder();
        order.setInboundNo(docSequenceService.nextDocNo("IN"));
        order.setWarehouseId(req.getWarehouseId());
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
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    @Transactional
    public Result<InboundOrder> confirm(@PathVariable Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) throw new BusinessException("Inbound order not found");
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException("Only draft inbound orders can be confirmed");
        }

        Long operatorId = SecurityUtil.currentUserId();

        List<InboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id));

        Long warehouseId = order.getWarehouseId();
        if (warehouseId == null) {
            throw new BusinessException("Inbound order has no warehouse");
        }

        for (InboundItem item : items) {
            inventoryService.addStock(
                    warehouseId,
                    item.getProductId(),
                    item.getQty(),
                    new StockChangeContext(
                            "INBOUND", id, "INBOUND", operatorId,
                            "Inbound: " + order.getInboundNo()));
        }

        order.setStatus("CONFIRMED");
        order.setInboundAt(LocalDateTime.now());
        inboundMapper.updateById(order);
        return Result.success(order);
    }

    /** Reject draft — closes order without inventory change */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
    @Transactional
    public Result<InboundOrder> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectInboundRequest body) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("Inbound order not found");
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException("Only draft inbound orders can be rejected");
        }
        order.setStatus("REJECTED");
        String reason = body != null && body.getReason() != null ? body.getReason().trim() : "";
        if (!reason.isEmpty()) {
            String base = order.getRemark() != null ? order.getRemark().trim() : "";
            order.setRemark(base.isEmpty() ? "Rejected: " + reason : base + " | Rejected: " + reason);
        }
        inboundMapper.updateById(order);
        enrichOperatorName(order);
        return Result.success(order);
    }

    /** Admin only — cannot delete confirmed (stock already applied). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("Inbound order not found");
        }
        if ("CONFIRMED".equals(order.getStatus())) {
            throw new BusinessException("Cannot delete a confirmed inbound order");
        }
        inboundMapper.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INBOUND','WAREHOUSE')")
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
        if (!INBOUND_OPERATOR_ROLES.contains(operator.getRole())) {
            throw new BusinessException("Operator must be a warehouse or inbound user");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException("At least one line item is required");
        }
        if (req.getWarehouseId() == null) {
            throw new BusinessException("Target warehouse is required");
        }
        warehouseService.requireActive(req.getWarehouseId());

        order.setSupplier(req.getSupplier());
        order.setWarehouseId(req.getWarehouseId());
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
        enrichWarehouseNames(list);
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
        if (order == null) return;
        enrichWarehouseNames(List.of(order));
        if (order.getOperatorId() == null) return;
        User u = userMapper.selectById(order.getOperatorId());
        if (u != null) {
            order.setOperatorName((u.getRealName() != null && !u.getRealName().isBlank())
                    ? u.getRealName()
                    : u.getUsername());
        }
    }

    private void enrichWarehouseNames(List<InboundOrder> list) {
        Set<Long> whIds = list.stream()
                .map(InboundOrder::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (whIds.isEmpty()) return;
        Map<Long, String> names = warehouseMapper.selectList(
                        new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, whIds))
                .stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName, (a, b) -> a));
        for (InboundOrder o : list) {
            if (o.getWarehouseId() != null) {
                o.setWarehouseName(names.get(o.getWarehouseId()));
            }
        }
    }

    @Data
    public static class CreateInboundRequest {
        private Long warehouseId;
        private String supplier;
        private String remark;
        /** Must reference an active user with role INBOUND or WAREHOUSE */
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

    @Data
    public static class RejectInboundRequest {
        /** Appended to remark when present */
        private String reason;
    }
}
