package com.dormitory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            seedDemoDataIfNeeded(connection);
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

    private void seedDemoDataIfNeeded(Connection connection) throws SQLException {
        try (Statement countStatement = connection.createStatement();
             var resultSet = countStatement.executeQuery("SELECT COUNT(*) FROM students")) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
        }

        Path seedScript = Path.of("database", "seed_demo_data.sql");
        if (!Files.exists(seedScript)) {
            throw new IllegalStateException("未找到演示数据脚本：" + seedScript.toAbsolutePath());
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : splitSqlStatements(Files.readString(seedScript))) {
                if (!sql.isBlank() && !sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("USE ")) {
                    statement.executeUpdate(sql);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取演示数据脚本失败：" + e.getMessage(), e);
        }
    }

    private List<String> splitSqlStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (ch == '\'') {
                current.append(ch);
                boolean escaped = i > 0 && script.charAt(i - 1) == '\\';
                if (!escaped) {
                    inString = !inString;
                }
            } else if (ch == ';' && !inString) {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
