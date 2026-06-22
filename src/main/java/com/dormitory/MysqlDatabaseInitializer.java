package com.dormitory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class MysqlDatabaseInitializer {
    private final MysqlConnectionFactory connectionFactory;

    public MysqlDatabaseInitializer(MysqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void initialize() {
        createDatabaseIfNeeded();
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS students (
                        student_id VARCHAR(32) PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        department VARCHAR(100) NOT NULL,
                        class_name VARCHAR(100) NOT NULL,
                        dorm_number VARCHAR(32) NOT NULL,
                        dorm_phone VARCHAR(32) NOT NULL,
                        bed_number VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_dorm_bed (dorm_number, bed_number),
                        KEY idx_dorm_number (dorm_number),
                        KEY idx_department_class (department, class_name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS change_requests (
                        id VARCHAR(64) PRIMARY KEY,
                        student_id VARCHAR(32) NOT NULL,
                        current_dorm_number VARCHAR(32) NOT NULL,
                        current_bed_number VARCHAR(32) NOT NULL,
                        target_dorm_number VARCHAR(32) NOT NULL,
                        target_dorm_phone VARCHAR(32) NOT NULL,
                        target_bed_number VARCHAR(32) NOT NULL,
                        reason VARCHAR(500) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at DATETIME NOT NULL,
                        handled_at DATETIME NULL,
                        admin_comment VARCHAR(500) NULL,
                        KEY idx_request_status (status),
                        KEY idx_request_student (student_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            seedStudentsIfEmpty(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 MySQL 表结构失败：" + e.getMessage(), e);
        }
    }

    private void createDatabaseIfNeeded() {
        if (connectionFactory.config().hasExplicitJdbcUrl()) {
            return;
        }
        try (Connection connection = connectionFactory.openServerConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + connectionFactory.config().database() + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("创建 MySQL 数据库失败：" + e.getMessage(), e);
        }
    }

    private void seedStudentsIfEmpty(Connection connection) throws SQLException {
        try (Statement countStatement = connection.createStatement();
             var resultSet = countStatement.executeQuery("SELECT COUNT(*) FROM students")) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
        }
        String sql = """
                INSERT INTO students (student_id, name, department, class_name, dorm_number, dorm_phone, bed_number)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            insertStudent(statement, "20230001", "张明", "计算机系", "软件2301", "3-501", "0571-3501", "1");
            insertStudent(statement, "20230002", "李华", "计算机系", "软件2301", "3-501", "0571-3501", "2");
            insertStudent(statement, "20230003", "王芳", "信息工程系", "物联2302", "3-502", "0571-3502", "1");
            insertStudent(statement, "20230004", "赵强", "机电工程系", "机电2301", "2-401", "0571-2401", "3");
            insertStudent(statement, "20230005", "陈晨", "外语系", "英语2301", "2-402", "0571-2402", "2");
            insertStudent(statement, "20230006", "刘洋", "计算机系", "网络2301", "3-502", "0571-3502", "2");
        }
    }

    private void insertStudent(
            PreparedStatement statement,
            String studentId,
            String name,
            String department,
            String className,
            String dormNumber,
            String dormPhone,
            String bedNumber) throws SQLException {
        statement.setString(1, studentId);
        statement.setString(2, name);
        statement.setString(3, department);
        statement.setString(4, className);
        statement.setString(5, dormNumber);
        statement.setString(6, dormPhone);
        statement.setString(7, bedNumber);
        statement.executeUpdate();
    }
}
