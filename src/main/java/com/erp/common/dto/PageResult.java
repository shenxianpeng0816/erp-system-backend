package com.erp.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long current;
    private long size;
}
