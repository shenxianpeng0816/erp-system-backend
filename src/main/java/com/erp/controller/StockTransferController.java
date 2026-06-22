package com.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.Result;
import com.erp.entity.*;
import com.erp.mapper.StockTransferItemMapper;
import com.erp.mapper.StockTransferMapper;
import com.erp.mapper.UserMapper;
import com.erp.mapper.WarehouseMapper;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferMapper transferMapper;
    private final StockTransferItemMapper transferItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final UserMapper userMapper;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final DocSequenceService docSequenceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<PageResult<StockTransfer>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<StockTransfer> q = new LambdaQueryWrapper<StockTransfer>()
                .orderByDesc(StockTransfer::getCreatedAt);
        if (warehouseId != null) {
            q.and(w -> w.eq(StockTransfer::getFromWarehouseId, warehouseId)
                    .or()
                    .eq(StockTransfer::getToWarehouseId, warehouseId));
        }
        Page<StockTransfer> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizeSize(size));
        Page<StockTransfer> result = transferMapper.selectPage(p, q);
        enrichTransfers(result.getRecords());
        return Result.success(PageQuery.from(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<StockTransfer> detail(@PathVariable Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("Stock transfer not found");
        enrichTransfers(List.of(transfer));
        return Result.success(transfer);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    public Result<List<StockTransferItem>> items(@PathVariable Long id) {
        return Result.success(transferItemMapper.findWithProductByTransferId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    @Transactional
    public Result<StockTransfer> create(@RequestBody CreateStockTransferRequest req) {
        validateCreateRequest(req);

        Warehouse from = warehouseService.requireActive(req.getFromWarehouseId());
        Warehouse to = warehouseService.requireActive(req.getToWarehouseId());
        if (from.getId().equals(to.getId())) {
            throw new BusinessException("Source and destination warehouse must differ");
        }
        if (!from.getCountryCode().equals(to.getCountryCode())) {
            throw new BusinessException("Transfers are limited to warehouses in the same country");
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNo(docSequenceService.nextDocNo("ST"));
        transfer.setFromWarehouseId(from.getId());
        transfer.setToWarehouseId(to.getId());
        transfer.setOperatorId(SecurityUtil.currentUserId());
        transfer.setStatus("DRAFT");
        transfer.setRemark(req.getRemark());
        transferMapper.insert(transfer);

        for (CreateStockTransferRequest.ItemReq item : req.getItems()) {
            StockTransferItem line = new StockTransferItem();
            line.setTransferId(transfer.getId());
            line.setProductId(item.getProductId());
            line.setQty(item.getQty());
            transferItemMapper.insert(line);
        }

        enrichTransfers(List.of(transfer));
        return Result.success(transfer);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    @Transactional
    public Result<StockTransfer> confirm(@PathVariable Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("Stock transfer not found");
        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException("Only draft transfers can be confirmed");
        }

        List<StockTransferItem> items = transferItemMapper.selectList(
                new LambdaQueryWrapper<StockTransferItem>().eq(StockTransferItem::getTransferId, id));
        if (items.isEmpty()) {
            throw new BusinessException("Transfer has no line items");
        }

        Long operatorId = SecurityUtil.currentUserId();
        String transferNo = transfer.getTransferNo();

        for (StockTransferItem item : items) {
            inventoryService.deductStock(
                    transfer.getFromWarehouseId(),
                    item.getProductId(),
                    item.getQty(),
                    new StockChangeContext(
                            "TRANSFER_OUT", id, "TRANSFER", operatorId,
                            "Transfer out: " + transferNo));

            inventoryService.addStock(
                    transfer.getToWarehouseId(),
                    item.getProductId(),
                    item.getQty(),
                    new StockChangeContext(
                            "TRANSFER_IN", id, "TRANSFER", operatorId,
                            "Transfer in: " + transferNo));
        }

        transfer.setStatus("CONFIRMED");
        transfer.setConfirmedAt(LocalDateTime.now());
        transferMapper.updateById(transfer);
        enrichTransfers(List.of(transfer));
        return Result.success(transfer);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','INBOUND')")
    @Transactional
    public Result<StockTransfer> cancel(@PathVariable Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("Stock transfer not found");
        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException("Only draft transfers can be cancelled");
        }
        transfer.setStatus("CANCELLED");
        transferMapper.updateById(transfer);
        enrichTransfers(List.of(transfer));
        return Result.success(transfer);
    }

    private void validateCreateRequest(CreateStockTransferRequest req) {
        if (req.getFromWarehouseId() == null || req.getToWarehouseId() == null) {
            throw new BusinessException("Source and destination warehouses are required");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException("At least one line item is required");
        }
        for (CreateStockTransferRequest.ItemReq item : req.getItems()) {
            if (item.getProductId() == null) {
                throw new BusinessException("Each line must have a product");
            }
            if (item.getQty() == null || item.getQty() <= 0) {
                throw new BusinessException("Each line must have quantity greater than zero");
            }
        }
    }

    private void enrichTransfers(List<StockTransfer> transfers) {
        if (transfers == null || transfers.isEmpty()) return;

        Set<Long> whIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (StockTransfer t : transfers) {
            whIds.add(t.getFromWarehouseId());
            whIds.add(t.getToWarehouseId());
            if (t.getOperatorId() != null) userIds.add(t.getOperatorId());
        }

        Map<Long, String> whNames = new HashMap<>();
        if (!whIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, whIds));
            for (Warehouse w : warehouses) {
                whNames.put(w.getId(), w.getName());
            }
        }

        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().in(User::getId, userIds));
            for (User u : users) {
                userNames.put(u.getId(), displayName(u));
            }
        }

        for (StockTransfer t : transfers) {
            t.setFromWarehouseName(whNames.get(t.getFromWarehouseId()));
            t.setToWarehouseName(whNames.get(t.getToWarehouseId()));
            if (t.getOperatorId() != null) {
                t.setOperatorName(userNames.get(t.getOperatorId()));
            }
        }
    }

    private static String displayName(User u) {
        if (u.getRealName() != null && !u.getRealName().isBlank()) {
            return u.getRealName().trim();
        }
        return u.getUsername();
    }

    @Data
    public static class CreateStockTransferRequest {
        private Long fromWarehouseId;
        private Long toWarehouseId;
        private String remark;
        private List<ItemReq> items;

        @Data
        public static class ItemReq {
            private Long productId;
            private Integer qty;
        }
    }
}
