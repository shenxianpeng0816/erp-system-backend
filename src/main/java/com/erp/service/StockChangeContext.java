package com.erp.service;

public record StockChangeContext(
        String type,
        Long refId,
        String refType,
        Long operatorId,
        String remark
) {}
