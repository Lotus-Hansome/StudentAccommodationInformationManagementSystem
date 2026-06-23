package com.dormitory;

import java.io.IOException;
import java.util.List;

public interface OperationLogRepository {
    void add(String operator, String action, String targetType, String targetId, String detail) throws IOException;

    PageResult<OperationLog> search(int page, int pageSize, String keyword) throws IOException;
}
