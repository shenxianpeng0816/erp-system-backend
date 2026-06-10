package com.erp.common.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Set;

public final class PageQuery {

    public static final int DEFAULT_SIZE = 10;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50, 100);

    private PageQuery() {
    }

    public static long normalizePage(long page) {
        return page < 1 ? 1 : page;
    }

    public static long normalizeSize(long size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        int s = (int) size;
        return ALLOWED_SIZES.contains(s) ? s : DEFAULT_SIZE;
    }

    public static <T> PageResult<T> from(Page<T> page) {
        PageResult<T> pr = new PageResult<>();
        pr.setRecords(page.getRecords());
        pr.setTotal(page.getTotal());
        pr.setCurrent(page.getCurrent());
        pr.setSize(page.getSize());
        return pr;
    }
}
