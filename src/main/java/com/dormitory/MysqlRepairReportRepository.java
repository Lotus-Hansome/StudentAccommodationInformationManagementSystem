package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class MysqlRepairReportRepository implements RepairReportRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlRepairReportRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<RepairReport> load() throws IOException {
        String sql = """
                SELECT id, student_id, dorm_number, category, description, status, created_at, handled_at, admin_comment
                FROM repair_reports
                ORDER BY created_at DESC
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<RepairReport> reports = new ArrayList<>();
            while (resultSet.next()) {
                Timestamp handledAt = resultSet.getTimestamp("handled_at");
                reports.add(new RepairReport(
                        resultSet.getString("id"),
                        resultSet.getString("student_id"),
                        resultSet.getString("dorm_number"),
                        resultSet.getString("category"),
                        resultSet.getString("description"),
                        RepairStatus.fromName(resultSet.getString("status")),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        handledAt == null ? null : handledAt.toLocalDateTime(),
                        resultSet.getString("admin_comment")));
            }
            return reports;
        } catch (SQLException e) {
            throw new IOException("读取 MySQL 报修反馈失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void save(List<RepairReport> reports) throws IOException {
        String insertSql = """
                INSERT INTO repair_reports (
                    id, student_id, dorm_number, category, description,
                    status, created_at, handled_at, admin_comment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    student_id = VALUES(student_id),
                    dorm_number = VALUES(dorm_number),
                    category = VALUES(category),
                    description = VALUES(description),
                    status = VALUES(status),
                    created_at = VALUES(created_at),
                    handled_at = VALUES(handled_at),
                    admin_comment = VALUES(admin_comment)
                """;
        Connection connection = null;
        try {
            connection = connectionFactory.openConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (RepairReport report : reports) {
                    statement.setString(1, report.getId());
                    statement.setString(2, report.getStudentId());
                    statement.setString(3, report.getDormNumber());
                    statement.setString(4, report.getCategory());
                    statement.setString(5, report.getDescription());
                    statement.setString(6, report.getStatus().name());
                    statement.setTimestamp(7, Timestamp.valueOf(report.getCreatedAt()));
                    statement.setTimestamp(8, report.getHandledAt() == null ? null : Timestamp.valueOf(report.getHandledAt()));
                    statement.setString(9, report.getAdminComment());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IOException("保存 MySQL 报修反馈失败：" + e.getMessage(), e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
