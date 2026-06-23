package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class MysqlOperationLogRepository implements OperationLogRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlOperationLogRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void add(String operator, String action, String targetType, String targetId, String detail) throws IOException {
        String sql = """
                INSERT INTO operation_logs (operator, action, target_type, target_id, detail)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operator);
            statement.setString(2, action);
            statement.setString(3, targetType);
            statement.setString(4, targetId);
            statement.setString(5, detail);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("写入操作日志失败：" + e.getMessage(), e);
        }
    }

    @Override
    public PageResult<OperationLog> search(int page, int pageSize, String keyword) throws IOException {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String where = normalizedKeyword.isBlank()
                ? ""
                : " WHERE operator LIKE ? OR action LIKE ? OR target_type LIKE ? OR target_id LIKE ? OR detail LIKE ? ";
        String countSql = "SELECT COUNT(*) FROM operation_logs" + where;
        String querySql = """
                SELECT id, operator, action, target_type, target_id, detail, created_at
                FROM operation_logs
                """ + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";

        try (Connection connection = connectionFactory.openConnection()) {
            int total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                fillKeyword(statement, normalizedKeyword);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    total = resultSet.getInt(1);
                }
            }
            List<OperationLog> logs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(querySql)) {
                int next = fillKeyword(statement, normalizedKeyword);
                statement.setInt(next, safePageSize);
                statement.setInt(next + 1, (safePage - 1) * safePageSize);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Timestamp createdAt = resultSet.getTimestamp("created_at");
                        logs.add(new OperationLog(
                                resultSet.getLong("id"),
                                resultSet.getString("operator"),
                                resultSet.getString("action"),
                                resultSet.getString("target_type"),
                                resultSet.getString("target_id"),
                                resultSet.getString("detail"),
                                createdAt == null ? null : createdAt.toLocalDateTime()));
                    }
                }
            }
            return new PageResult<>(logs, total, safePage, safePageSize);
        } catch (SQLException e) {
            throw new IOException("读取操作日志失败：" + e.getMessage(), e);
        }
    }

    private int fillKeyword(PreparedStatement statement, String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) {
            return 1;
        }
        String like = "%" + keyword + "%";
        for (int i = 1; i <= 5; i++) {
            statement.setString(i, like);
        }
        return 6;
    }
}
