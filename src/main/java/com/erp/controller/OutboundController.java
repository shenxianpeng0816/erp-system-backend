package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.result.Result;
import com.erp.common.exception.BusinessException;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.InventoryService;
import com.erp.service.StockChangeContext;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final InventoryService inventoryService;
    private final SalesOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<PageResult<OutboundOrder>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        page = PageQuery.normalizePage(page);
        size = PageQuery.normalizeSize(size);

        LambdaQueryWrapper<OutboundOrder> q = new LambdaQueryWrapper<OutboundOrder>()
                .orderByDesc(OutboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(OutboundOrder::getWarehouseId, warehouseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(OutboundOrder::getOutboundNo, keyword.trim());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            List<SalesOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>()
                            .likeRight(SalesOrder::getOrderNo, orderNo.trim()));
            if (orders.isEmpty()) {
                PageResult<OutboundOrder> empty = new PageResult<>();
                empty.setRecords(List.of());
                empty.setTotal(0);
                empty.setCurrent(page);
                empty.setSize(size);
                return Result.success(empty);
            }
            q.in(OutboundOrder::getOrderId,
                    orders.stream().map(SalesOrder::getId).toList());
        }

        Page<OutboundOrder> p = new Page<>(page, size);
        Page<OutboundOrder> result = outboundMapper.selectPage(p, q);
        enrichOutboundOrders(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    /** Export all outbound orders matching list filters (no pagination). */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<OutboundOrder>> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<OutboundOrder> q = new LambdaQueryWrapper<OutboundOrder>()
                .orderByDesc(OutboundOrder::getCreatedAt);
        if (warehouseId != null) {
            q.eq(OutboundOrder::getWarehouseId, warehouseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(OutboundOrder::getOutboundNo, keyword.trim());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            List<SalesOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>()
                            .likeRight(SalesOrder::getOrderNo, orderNo.trim()));
            if (orders.isEmpty()) {
                return Result.success(List.of());
            }
            q.in(OutboundOrder::getOrderId,
                    orders.stream().map(SalesOrder::getId).toList());
        }
        List<OutboundOrder> list = outboundMapper.selectList(q);
        enrichOutboundOrders(list);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<OutboundOrder> detail(@PathVariable Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn != null) {
            enrichOutboundOrders(List.of(dn));
        }
        return Result.success(dn);
    }

    @GetMapping("/{id}/items")
    public Result<List<OutboundItem>> items(@PathVariable Long id) {
        return Result.success(outboundItemMapper.findWithProductByOutboundId(id));
    }

    /** Warehouse marks as printed (ready to ship) */
    @PostMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<OutboundOrder> print(@PathVariable Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) throw new BusinessException("Delivery note not found");
        dn.setStatus("PRINTED");
        outboundMapper.updateById(dn);
        return Result.success(dn);
    }

    /** Warehouse executes outbound → deduct inventory + write log */
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    @Transactional
    public Result<OutboundOrder> ship(@PathVariable Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) throw new BusinessException("Delivery note not found");
        if (!"PRINTED".equals(dn.getStatus()) && !"PENDING".equals(dn.getStatus()))
            throw new BusinessException("Invalid status for shipping");

        SalesOrder linkedOrder = orderMapper.selectById(dn.getOrderId());
        if (linkedOrder != null && "CANCELLED".equals(linkedOrder.getStatus())) {
            throw new BusinessException("Sales order is cancelled");
        }

        Long warehouseId = dn.getWarehouseId();
        if (warehouseId == null && linkedOrder != null) {
            warehouseId = linkedOrder.getShipFromWarehouseId();
        }
        if (warehouseId == null) {
            throw new BusinessException("Outbound order has no warehouse");
        }

        Long operatorId = SecurityUtil.currentUserId();

        List<OutboundItem> items = outboundItemMapper.selectList(
                new LambdaQueryWrapper<OutboundItem>().eq(OutboundItem::getOutboundId, id));

        for (OutboundItem item : items) {
            inventoryService.deductStock(
                    warehouseId,
                    item.getProductId(),
                    item.getQty(),
                    new StockChangeContext(
                            "OUTBOUND", id, "OUTBOUND", operatorId,
                            "Outbound: " + dn.getOutboundNo()));
        }

        dn.setStatus("SHIPPED");
        dn.setOperatorId(operatorId);
        dn.setShippedAt(LocalDateTime.now());
        if (dn.getWarehouseId() == null) {
            dn.setWarehouseId(warehouseId);
        }
        outboundMapper.updateById(dn);

        SalesOrder order = orderMapper.selectById(dn.getOrderId());
        if (order != null) {
            order.setStatus("SHIPPED");
            orderMapper.updateById(order);
        }
        return Result.success(dn);
    }

    private void enrichOutboundOrders(List<OutboundOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        Set<Long> customerIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        Set<Long> warehouseIds = new HashSet<>();
        List<Long> outboundIds = new ArrayList<>();
        for (OutboundOrder o : orders) {
            outboundIds.add(o.getId());
            if (o.getShipToCustomerId() != null) {
                customerIds.add(o.getShipToCustomerId());
            }
            if (o.getOrderId() != null) {
                orderIds.add(o.getOrderId());
            }
            if (o.getWarehouseId() != null) {
                warehouseIds.add(o.getWarehouseId());
            }
        }

        Map<Long, String> customerNames = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>().in(Customer::getId, customerIds));
            for (Customer c : customers) {
                customerNames.put(c.getId(), c.getName());
            }
        }

        Map<Long, SalesOrder> orderMap = new HashMap<>();
        Set<Long> salesUserIds = new HashSet<>();
        if (!orderIds.isEmpty()) {
            List<SalesOrder> salesOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>().in(SalesOrder::getId, orderIds));
            for (SalesOrder so : salesOrders) {
                orderMap.put(so.getId(), so);
                if (so.getSalesUserId() != null) {
                    salesUserIds.add(so.getSalesUserId());
                }
                if (so.getShipFromWarehouseId() != null) {
                    warehouseIds.add(so.getShipFromWarehouseId());
                }
            }
        }

        Map<Long, String> warehouseNames = new HashMap<>();
        if (!warehouseIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, warehouseIds));
            for (Warehouse w : warehouses) {
                warehouseNames.put(w.getId(), w.getName());
            }
        }

        Map<Long, String> salesUserNames = new HashMap<>();
        if (!salesUserIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(salesUserIds);
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    salesUserNames.put(u.getId(), userDisplayName(u));
                }
            }
        }

        Map<Long, List<OutboundItem>> itemsByOutbound = new HashMap<>();
        if (!outboundIds.isEmpty()) {
            List<OutboundItem> items = outboundItemMapper.findWithProductByOutboundIds(outboundIds);
            for (OutboundItem item : items) {
                itemsByOutbound.computeIfAbsent(item.getOutboundId(), k -> new ArrayList<>()).add(item);
            }
        }

        for (OutboundOrder o : orders) {
            if (o.getShipToCustomerId() != null) {
                o.setShipToCustomerName(customerNames.get(o.getShipToCustomerId()));
            }
            SalesOrder so = orderMap.get(o.getOrderId());
            if (so != null) {
                o.setOrderNo(so.getOrderNo());
                if (so.getSalesUserId() != null) {
                    o.setSalesUserName(salesUserNames.get(so.getSalesUserId()));
                }
            }
            Long whId = o.getWarehouseId();
            if (whId == null && so != null) {
                whId = so.getShipFromWarehouseId();
            }
            if (whId != null) {
                o.setWarehouseName(warehouseNames.get(whId));
            }
            List<OutboundItem> items = itemsByOutbound.getOrDefault(o.getId(), List.of());
            o.setTotalQty(items.stream().mapToInt(OutboundItem::getQty).sum());
            o.setProductSummary(formatProductSummary(items));
        }
    }

    private static String formatProductSummary(List<OutboundItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> {
                    String model = productModel(item);
                    return model + " ×" + item.getQty();
                })
                .collect(Collectors.joining(" / "));
    }

    private static String productModel(OutboundItem item) {
        if (item.getProductNo() != null && !item.getProductNo().isBlank()) {
            return item.getProductNo().trim();
        }
        if (item.getSpec() != null && !item.getSpec().isBlank()) {
            return item.getSpec().trim();
        }
        if (item.getProductName() != null && !item.getProductName().isBlank()) {
            return item.getProductName().trim();
        }
        return "Product #" + item.getProductId();
    }

    private static String userDisplayName(User u) {
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            return u.getRealName().trim();
        }
        return u.getUsername() != null ? u.getUsername() : "";
    }
}
