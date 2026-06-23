package com.dormitory;

import java.io.IOException;

public class OperationLogService {
    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    public void record(String operator, String action, String targetType, String targetId, String detail) {
        try {
            repository.add(operator, action, targetType, targetId, detail);
        } catch (IOException e) {
            System.err.println("操作日志写入失败：" + e.getMessage());
        }
    }

    public PageResult<OperationLog> search(int page, int pageSize, String keyword) {
        try {
            return repository.search(page, pageSize, keyword);
        } catch (IOException e) {
            throw new IllegalStateException("读取操作日志失败：" + e.getMessage(), e);
        }
    }
}
