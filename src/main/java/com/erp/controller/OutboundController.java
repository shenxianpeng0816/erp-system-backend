package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageResult;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundOrderMapper outboundMapper;
    private final OutboundItemMapper outboundItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final SalesOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<PageResult<OutboundOrder>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        LambdaQueryWrapper<OutboundOrder> q = new LambdaQueryWrapper<OutboundOrder>()
                .orderByDesc(OutboundOrder::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            q.like(OutboundOrder::getOutboundNo, keyword.trim());
        }

        Page<OutboundOrder> p = new Page<>(page, size);
        Page<OutboundOrder> result = outboundMapper.selectPage(p, q);
        enrichOutboundOrders(result.getRecords());

        PageResult<OutboundOrder> pr = new PageResult<>();
        pr.setRecords(result.getRecords());
        pr.setTotal(result.getTotal());
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        return Result.success(pr);
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

    private void enrichOutboundOrders(List<OutboundOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        Set<Long> customerIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        List<Long> outboundIds = new ArrayList<>();
        for (OutboundOrder o : orders) {
            outboundIds.add(o.getId());
            if (o.getShipToCustomerId() != null) {
                customerIds.add(o.getShipToCustomerId());
            }
            if (o.getOrderId() != null) {
                orderIds.add(o.getOrderId());
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
