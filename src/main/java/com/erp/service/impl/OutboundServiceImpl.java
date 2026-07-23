package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.dto.response.OutboundPrintData;
import com.erp.entity.*;
import com.erp.mapper.*;
import com.erp.service.InventoryService;
import com.erp.service.OutboundService;
import com.erp.service.StockChangeContext;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutboundServiceImpl implements OutboundService {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final SalesOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryService inventoryService;

    @Override
    public PageResult<OutboundOrder> pageList(String keyword, String orderNo, Long warehouseId,
                                              long page, long size) {
        page = PageQuery.normalizePage(page);
        size = PageQuery.normalizeSize(size);
        LambdaQueryWrapper<OutboundOrder> q = buildListQuery(keyword, orderNo, warehouseId);
        if (q == null) {
            PageResult<OutboundOrder> empty = new PageResult<>();
            empty.setRecords(List.of());
            empty.setTotal(0);
            empty.setCurrent(page);
            empty.setSize(size);
            return empty;
        }
        Page<OutboundOrder> result = outboundMapper.selectPage(new Page<>(page, size), q);
        enrichOutboundOrders(result.getRecords());
        return PageQuery.from(result);
    }

    @Override
    public List<OutboundOrder> export(String keyword, String orderNo, Long warehouseId) {
        LambdaQueryWrapper<OutboundOrder> q = buildListQuery(keyword, orderNo, warehouseId);
        if (q == null) {
            return List.of();
        }
        List<OutboundOrder> list = outboundMapper.selectList(q);
        enrichOutboundOrders(list);
        return list;
    }

    @Override
    public OutboundOrder getDetail(Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn != null) {
            enrichOutboundOrders(List.of(dn));
        }
        return dn;
    }

    @Override
    public List<OutboundItem> listItems(Long outboundId) {
        return outboundItemMapper.findWithProductByOutboundId(outboundId);
    }

    @Override
    public OutboundPrintData getPrintData(Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) {
            throw new BusinessException("Delivery note not found");
        }
        enrichOutboundOrders(List.of(dn));
        List<OutboundItem> items = outboundItemMapper.findWithProductByOutboundId(id);
        SalesOrder order = dn.getOrderId() != null ? orderMapper.selectById(dn.getOrderId()) : null;
        Customer customer = dn.getShipToCustomerId() != null
                ? customerMapper.selectById(dn.getShipToCustomerId()) : null;
        return new OutboundPrintData(dn, items, order, customer);
    }

    @Override
    @Transactional
    public OutboundOrder markPrinted(Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) {
            throw new BusinessException("Delivery note not found");
        }
        dn.setStatus("PRINTED");
        outboundMapper.updateById(dn);
        return dn;
    }

    @Override
    @Transactional
    public OutboundOrder ship(Long id) {
        OutboundOrder dn = outboundMapper.selectById(id);
        if (dn == null) {
            throw new BusinessException("Delivery note not found");
        }
        if (!"PRINTED".equals(dn.getStatus()) && !"PENDING".equals(dn.getStatus())) {
            throw new BusinessException("Invalid status for shipping");
        }

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

        if (linkedOrder != null) {
            linkedOrder.setStatus("SHIPPED");
            orderMapper.updateById(linkedOrder);
        }
        return dn;
    }

    /** @return null when orderNo filter matches nothing */
    private LambdaQueryWrapper<OutboundOrder> buildListQuery(String keyword, String orderNo, Long warehouseId) {
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
                return null;
            }
            q.in(OutboundOrder::getOrderId, orders.stream().map(SalesOrder::getId).toList());
        }
        return q;
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
                customerNames.put(c.getId(), c.getShopName());
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
                .map(item -> productModel(item) + " ×" + item.getQty())
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
