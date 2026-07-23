package com.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.dto.PageQuery;
import com.erp.common.dto.PageResult;
import com.erp.common.exception.BusinessException;
import com.erp.dto.request.CreateStockTransferRequest;
import com.erp.dto.response.TransferFormOptions;
import com.erp.entity.StockTransfer;
import com.erp.entity.StockTransferItem;
import com.erp.entity.User;
import com.erp.entity.Warehouse;
import com.erp.mapper.StockTransferItemMapper;
import com.erp.mapper.StockTransferMapper;
import com.erp.mapper.UserMapper;
import com.erp.mapper.WarehouseMapper;
import com.erp.service.DocSequenceService;
import com.erp.service.InventoryService;
import com.erp.service.ProductService;
import com.erp.service.StockChangeContext;
import com.erp.service.StockTransferService;
import com.erp.service.WarehouseService;
import com.erp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferMapper transferMapper;
    private final StockTransferItemMapper transferItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final UserMapper userMapper;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final DocSequenceService docSequenceService;

    @Override
    public TransferFormOptions getFormOptions(Long fromWarehouseId) {
        TransferFormOptions opts = new TransferFormOptions();
        opts.setWarehouses(warehouseService.listActive(null));
        if (fromWarehouseId != null && fromWarehouseId > 0) {
            opts.setProducts(productService.listProducts(fromWarehouseId, null));
        } else {
            opts.setProducts(List.of());
        }
        return opts;
    }

    @Override
    public PageResult<StockTransfer> pageList(Long warehouseId, long page, long size) {
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
        return PageQuery.from(result);
    }

    @Override
    public StockTransfer getDetail(Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw new BusinessException("Stock transfer not found");
        }
        enrichTransfers(List.of(transfer));
        return transfer;
    }

    @Override
    public List<StockTransferItem> listItems(Long id) {
        return transferItemMapper.findWithProductByTransferId(id);
    }

    @Override
    @Transactional
    public StockTransfer create(CreateStockTransferRequest req) {
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
        return transfer;
    }

    @Override
    @Transactional
    public StockTransfer confirm(Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw new BusinessException("Stock transfer not found");
        }
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
        return transfer;
    }

    @Override
    @Transactional
    public StockTransfer cancel(Long id) {
        StockTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw new BusinessException("Stock transfer not found");
        }
        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException("Only draft transfers can be cancelled");
        }
        transfer.setStatus("CANCELLED");
        transferMapper.updateById(transfer);
        enrichTransfers(List.of(transfer));
        return transfer;
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
        if (transfers == null || transfers.isEmpty()) {
            return;
        }

        Set<Long> whIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (StockTransfer t : transfers) {
            whIds.add(t.getFromWarehouseId());
            whIds.add(t.getToWarehouseId());
            if (t.getOperatorId() != null) {
                userIds.add(t.getOperatorId());
            }
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
}
