package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.CreateInboundRequest;
import com.erp.dto.request.RejectInboundRequest;
import com.erp.dto.response.InboundFormOptions;
import com.erp.entity.InboundItem;
import com.erp.entity.InboundOrder;
import com.erp.entity.User;
import com.erp.entity.Warehouse;
import com.erp.mapper.InboundItemMapper;
import com.erp.mapper.InboundOrderMapper;
import com.erp.mapper.UserMapper;
import com.erp.mapper.WarehouseMapper;
import com.erp.service.DocSequenceService;
import com.erp.service.InboundService;
import com.erp.service.InventoryService;
import com.erp.service.ProductService;
import com.erp.service.StockChangeContext;
import com.erp.service.WarehouseService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InboundServiceImpl implements InboundService {

    /** Users eligible as inbound document operator (WAREHOUSE aligned with INBOUND). */
    private static final Set<String> INBOUND_OPERATOR_ROLES = Set.of("INBOUND", "WAREHOUSE");

    private final InboundOrderMapper inboundMapper;
    private final InboundItemMapper inboundItemMapper;
    private final UserMapper userMapper;
    private final DocSequenceService docSequenceService;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseMapper warehouseMapper;

    @Override
    public InboundFormOptions getFormOptions(Long warehouseId) {
        InboundFormOptions opts = new InboundFormOptions();
        opts.setWarehouses(warehouseService.listActive(null));
        List<User> operators = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .in(User::getRole, List.copyOf(INBOUND_OPERATOR_ROLES))
                .orderByAsc(User::getRealName));
        // Strip password hashes from form dropdown payload
        for (User u : operators) {
            u.setPassword(null);
        }
        opts.setOperators(operators);
        if (warehouseId != null && warehouseId > 0) {
            opts.setProducts(productService.listProducts(warehouseId, null));
        } else {
            opts.setProducts(List.of());
        }
        return opts;
    }

    @Override
    public PageResult<InboundOrder> pageList(Long warehouseId, long page, long size) {
        LambdaQueryWrapper<InboundOrder> q = new LambdaQueryWrapper<InboundOrder>()
                .orderByDesc(InboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        Page<InboundOrder> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<InboundOrder> result = inboundMapper.selectPage(p, q);
        enrichOperatorNames(result.getRecords());
        return PageQuery.from(result);
    }

    @Override
    public List<InboundOrder> export(Long warehouseId) {
        LambdaQueryWrapper<InboundOrder> q = new LambdaQueryWrapper<InboundOrder>()
                .orderByDesc(InboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        List<InboundOrder> list = inboundMapper.selectList(q);
        enrichOperatorNames(list);
        return list;
    }

    @Override
    public InboundOrder getDetail(Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order != null) {
            enrichOperatorName(order);
        }
        return order;
    }

    @Override
    public List<InboundItem> listItems(Long id) {
        return inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getInboundId, id));
    }

    @Override
    @Transactional
    public InboundOrder create(CreateInboundRequest req) {
        validateOperator(req.getOperatorId());
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
        return order;
    }

    @Override
    @Transactional
    public InboundOrder update(Long id, CreateInboundRequest req) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) throw new BusinessException("Inbound order not found");
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("Only draft inbound orders can be edited");

        validateOperator(req.getOperatorId());
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
        return updated;
    }

    @Override
    @Transactional
    public InboundOrder confirm(Long id) {
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
        return order;
    }

    @Override
    @Transactional
    public InboundOrder reject(Long id, RejectInboundRequest body) {
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
        return order;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InboundOrder order = inboundMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("Inbound order not found");
        }
        if ("CONFIRMED".equals(order.getStatus())) {
            throw new BusinessException("Cannot delete a confirmed inbound order");
        }
        inboundMapper.deleteById(id);
    }

    private void validateOperator(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException("Inbound operator is required");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new BusinessException("Invalid inbound operator");
        }
        if (!INBOUND_OPERATOR_ROLES.contains(operator.getRole())) {
            throw new BusinessException("Operator must be a warehouse or inbound user");
        }
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
}
