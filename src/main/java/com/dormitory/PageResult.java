package com.dormitory;

import java.util.List;

public class PageResult<T> {
    private final List<T> items;
    private final int total;
    private final int page;
    private final int pageSize;

    public PageResult(List<T> items, int total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
