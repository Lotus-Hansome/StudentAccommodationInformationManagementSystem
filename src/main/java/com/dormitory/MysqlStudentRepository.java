package com.dormitory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public PageResult<StudentDormRecord> search(StudentSearchCriteria criteria) throws IOException {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(criteria, params);
        String countSql = "SELECT COUNT(*) FROM students s " + where;
        String dataSql = """
                SELECT s.student_id, s.name, s.department, s.class_name, s.dorm_number, s.dorm_phone, s.bed_number
                FROM students s
                """ + where + " " + orderBy(criteria.getSort()) + " LIMIT ? OFFSET ?";

        int page = criteria.getPage();
        int pageSize = criteria.getPageSize();
        try (Connection connection = connectionFactory.openConnection()) {
            int total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                fill(statement, params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    total = resultSet.getInt(1);
                }
            }

            List<StudentDormRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(dataSql)) {
                fill(statement, params);
                statement.setInt(params.size() + 1, pageSize);
                statement.setInt(params.size() + 2, (page - 1) * pageSize);
                try (ResultSet resultSet = statement.executeQuery()) {
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
                }
            }
            return new PageResult<>(records, total, page, pageSize);
        } catch (SQLException e) {
            throw new IOException("分页查询 MySQL 学生住宿数据失败：" + e.getMessage(), e);
        }
    }

    private String buildWhere(StudentSearchCriteria criteria, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (!criteria.getStudentId().isBlank()) {
            conditions.add("s.student_id = ?");
            params.add(criteria.getStudentId());
        }
        if (!criteria.getDormNumber().isBlank()) {
            conditions.add("s.dorm_number = ?");
            params.add(criteria.getDormNumber());
        }
        if (!criteria.getBuildingNumber().isBlank()) {
            conditions.add("s.dorm_number LIKE ?");
            params.add(normalizeBuildingNumber(criteria.getBuildingNumber()) + "-%");
        }
        if (!criteria.getDepartment().isBlank()) {
            conditions.add("s.department LIKE ?");
            params.add("%" + criteria.getDepartment() + "%");
        }
        if (!criteria.getClassName().isBlank()) {
            conditions.add("s.class_name LIKE ?");
            params.add("%" + criteria.getClassName() + "%");
        }
        if (!criteria.getKeyword().isBlank()) {
            conditions.add("(s.student_id LIKE ? OR s.name LIKE ? OR s.department LIKE ? OR s.class_name LIKE ? OR s.dorm_number LIKE ?)");
            String keyword = "%" + criteria.getKeyword() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(keyword);
            }
        }
        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private String orderBy(String sort) {
        String normalized = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        if ("department".equals(normalized) || "departmentclass".equals(normalized)) {
            return "ORDER BY s.department, s.class_name, s.student_id";
        }
        if ("student".equals(normalized)) {
            return "ORDER BY s.student_id";
        }
        return "ORDER BY s.dorm_number, CAST(s.bed_number AS UNSIGNED), s.bed_number";
    }

    private void fill(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    private String normalizeBuildingNumber(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.endsWith("号楼")) {
            return normalized.substring(0, normalized.length() - 2).trim();
        }
        if (normalized.endsWith("楼")) {
            return normalized.substring(0, normalized.length() - 1).trim();
        }
        int digitEnd = 0;
        while (digitEnd < normalized.length() && Character.isDigit(normalized.charAt(digitEnd))) {
            digitEnd++;
        }
        if (digitEnd > 0) {
            return normalized.substring(0, digitEnd);
        }
        return normalized;
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
