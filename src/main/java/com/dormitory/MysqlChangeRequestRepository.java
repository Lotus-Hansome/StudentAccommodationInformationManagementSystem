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

public class MysqlChangeRequestRepository implements ChangeRequestRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlChangeRequestRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<DormChangeRequest> load() throws IOException {
        String sql = """
                SELECT id, student_id, current_dorm_number, current_bed_number,
                       target_dorm_number, target_dorm_phone, target_bed_number,
                       reason, status, created_at, handled_at, admin_comment
                FROM change_requests
                ORDER BY created_at DESC
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<DormChangeRequest> requests = new ArrayList<>();
            while (resultSet.next()) {
                Timestamp handledAt = resultSet.getTimestamp("handled_at");
                requests.add(new DormChangeRequest(
                        resultSet.getString("id"),
                        resultSet.getString("student_id"),
                        resultSet.getString("current_dorm_number"),
                        resultSet.getString("current_bed_number"),
                        resultSet.getString("target_dorm_number"),
                        resultSet.getString("target_dorm_phone"),
                        resultSet.getString("target_bed_number"),
                        resultSet.getString("reason"),
                        ChangeRequestStatus.fromName(resultSet.getString("status")),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        handledAt == null ? null : handledAt.toLocalDateTime(),
                        resultSet.getString("admin_comment")));
            }
            return requests;
        } catch (SQLException e) {
            throw new IOException("读取 MySQL 调换申请失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void save(List<DormChangeRequest> requests) throws IOException {
        String insertSql = """
                INSERT INTO change_requests (
                    id, student_id, current_dorm_number, current_bed_number,
                    target_dorm_number, target_dorm_phone, target_bed_number,
                    reason, status, created_at, handled_at, admin_comment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Connection connection = null;
        try {
            connection = connectionFactory.openConnection();
            connection.setAutoCommit(false);
            try (Statement deleteStatement = connection.createStatement()) {
                deleteStatement.executeUpdate("DELETE FROM change_requests");
            }
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (DormChangeRequest request : requests) {
                    statement.setString(1, request.getId());
                    statement.setString(2, request.getStudentId());
                    statement.setString(3, request.getCurrentDormNumber());
                    statement.setString(4, request.getCurrentBedNumber());
                    statement.setString(5, request.getTargetDormNumber());
                    statement.setString(6, request.getTargetDormPhone());
                    statement.setString(7, request.getTargetBedNumber());
                    statement.setString(8, request.getReason());
                    statement.setString(9, request.getStatus().name());
                    statement.setTimestamp(10, Timestamp.valueOf(request.getCreatedAt()));
                    statement.setTimestamp(11, request.getHandledAt() == null ? null : Timestamp.valueOf(request.getHandledAt()));
                    statement.setString(12, request.getAdminComment());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IOException("保存 MySQL 调换申请失败：" + e.getMessage(), e);
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
