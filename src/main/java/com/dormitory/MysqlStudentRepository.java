package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MysqlStudentRepository implements StudentRepository {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlStudentRepository(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public List<StudentDormRecord> load() throws IOException {
        String sql = """
                SELECT student_id, name, department, class_name, dorm_number, dorm_phone, bed_number
                FROM students
                ORDER BY dorm_number, bed_number
                """;
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<StudentDormRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(new StudentDormRecord(
                        resultSet.getString("student_id"),
                        resultSet.getString("name"),
                        resultSet.getString("department"),
                        resultSet.getString("class_name"),
                        resultSet.getString("dorm_number"),
                        resultSet.getString("dorm_phone"),
                        resultSet.getString("bed_number")));
            }
            return records;
        } catch (SQLException e) {
            throw new IOException("读取 MySQL 学生宿舍数据失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void save(List<StudentDormRecord> records) throws IOException {
        String insertSql = """
                INSERT INTO students (student_id, name, department, class_name, dorm_number, dorm_phone, bed_number)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        Connection connection = null;
        try {
            connection = connectionFactory.openConnection();
            connection.setAutoCommit(false);
            try (Statement deleteStatement = connection.createStatement()) {
                deleteStatement.executeUpdate("DELETE FROM students");
            }
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                for (StudentDormRecord record : records) {
                    statement.setString(1, record.getStudentId());
                    statement.setString(2, record.getName());
                    statement.setString(3, record.getDepartment());
                    statement.setString(4, record.getClassName());
                    statement.setString(5, record.getDormNumber());
                    statement.setString(6, record.getDormPhone());
                    statement.setString(7, record.getBedNumber());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IOException("保存 MySQL 学生宿舍数据失败：" + e.getMessage(), e);
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
