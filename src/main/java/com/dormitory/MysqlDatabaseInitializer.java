package com.dormitory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                    CREATE TABLE IF NOT EXISTS buildings (
                        building_number VARCHAR(32) PRIMARY KEY,
                        building_name VARCHAR(100) NOT NULL,
                        gender_type VARCHAR(32) NOT NULL DEFAULT 'MIXED',
                        total_floors INT NOT NULL DEFAULT 6,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS dorm_rooms (
                        dorm_number VARCHAR(32) PRIMARY KEY,
                        building_number VARCHAR(32) NOT NULL,
                        floor_number INT NOT NULL,
                        room_type VARCHAR(64) NOT NULL DEFAULT '标准四人间',
                        gender_type VARCHAR(32) NOT NULL DEFAULT 'MIXED',
                        capacity INT NOT NULL DEFAULT 4,
                        phone VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        KEY idx_room_building (building_number),
                        KEY idx_room_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS beds (
                        dorm_number VARCHAR(32) NOT NULL,
                        bed_number VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (dorm_number, bed_number),
                        KEY idx_bed_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        username VARCHAR(64) PRIMARY KEY,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(32) NOT NULL,
                        student_id VARCHAR(32) NULL,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        last_login_at DATETIME NULL,
                        KEY idx_user_role (role),
                        KEY idx_user_student (student_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS operation_logs (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        operator VARCHAR(64) NOT NULL,
                        action VARCHAR(64) NOT NULL,
                        target_type VARCHAR(64) NOT NULL,
                        target_id VARCHAR(128) NOT NULL,
                        detail VARCHAR(1000) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        KEY idx_log_created (created_at),
                        KEY idx_log_operator (operator)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            seedDemoDataIfNeeded(connection);
            seedInfrastructureIfNeeded(connection);
            migrateDefaultRoomsToFourIfNeeded(connection);
            seedUsersIfNeeded(connection);
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

    private void seedInfrastructureIfNeeded(Connection connection) throws SQLException {
        if (tableCount(connection, "buildings") == 0) {
            String sql = """
                    INSERT INTO buildings (building_number, building_name, gender_type, total_floors, status)
                    VALUES (?, ?, ?, ?, 'ACTIVE')
                    ON DUPLICATE KEY UPDATE
                      building_name = VALUES(building_name),
                      gender_type = VALUES(gender_type),
                      total_floors = VALUES(total_floors),
                      status = VALUES(status)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int building = 1; building <= 12; building++) {
                    statement.setString(1, String.valueOf(building));
                    statement.setString(2, building + "号楼");
                    statement.setString(3, building % 2 == 0 ? "FEMALE" : "MALE");
                    statement.setInt(4, 6);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        if (tableCount(connection, "dorm_rooms") == 0) {
            seedRooms(connection);
        }
        if (tableCount(connection, "beds") == 0) {
            seedBedsFromRooms(connection);
        }
    }

    private void seedRooms(Connection connection) throws SQLException {
        String roomSql = """
                INSERT INTO dorm_rooms (dorm_number, building_number, floor_number, room_type, gender_type, capacity, phone, status)
                VALUES (?, ?, ?, '标准四人间', ?, 4, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE
                  building_number = VALUES(building_number),
                  floor_number = VALUES(floor_number),
                  room_type = VALUES(room_type),
                  gender_type = VALUES(gender_type),
                  capacity = VALUES(capacity),
                  phone = VALUES(phone),
                  status = VALUES(status)
                """;
        try (PreparedStatement statement = connection.prepareStatement(roomSql)) {
            for (int building = 1; building <= 12; building++) {
                int floor = defaultFloor(building);
                int startRoom = building == 10 ? 101 : floor * 100 + 1;
                int endRoom = building == 10 ? 110 : floor * 100 + 6;
                for (int room = startRoom; room <= endRoom; room++) {
                    statement.setString(1, building + "-" + room);
                    statement.setString(2, String.valueOf(building));
                    statement.setInt(3, room / 100);
                    statement.setString(4, building % 2 == 0 ? "FEMALE" : "MALE");
                    statement.setString(5, "0571-" + building + room);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void seedBedsFromRooms(Connection connection) throws SQLException {
        String querySql = "SELECT dorm_number, capacity FROM dorm_rooms";
        String insertSql = """
                INSERT INTO beds (dorm_number, bed_number, status)
                VALUES (?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = VALUES(status)
                """;
        try (Statement queryStatement = connection.createStatement();
             ResultSet resultSet = queryStatement.executeQuery(querySql);
             PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                String dormNumber = resultSet.getString("dorm_number");
                int capacity = resultSet.getInt("capacity");
                for (int i = 1; i <= capacity; i++) {
                    insertStatement.setString(1, dormNumber);
                    insertStatement.setString(2, String.valueOf(i));
                    insertStatement.addBatch();
                }
            }
            insertStatement.executeBatch();
        }
    }

    private void migrateDefaultRoomsToFourIfNeeded(Connection connection) throws SQLException {
        String migrationAction = "MIGRATE_DEFAULT_ROOMS_TO_FOUR";
        try (PreparedStatement checkStatement = connection.prepareStatement("SELECT COUNT(*) FROM operation_logs WHERE action = ?")) {
            checkStatement.setString(1, migrationAction);
            try (ResultSet resultSet = checkStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) {
                    return;
                }
            }
        }

        String updateSql = """
                UPDATE dorm_rooms r
                SET r.room_type = '标准四人间', r.capacity = 4
                WHERE r.room_type = '标准六人间'
                  AND r.capacity = 6
                  AND (
                    SELECT COUNT(*)
                    FROM students s
                    WHERE s.dorm_number = r.dorm_number
                  ) <= 4
                """;
        int updatedRows;
        try (Statement statement = connection.createStatement()) {
            updatedRows = statement.executeUpdate(updateSql);
            if (updatedRows > 0) {
                statement.executeUpdate("""
                        DELETE b
                        FROM beds b
                        JOIN dorm_rooms r ON r.dorm_number = b.dorm_number
                        WHERE r.room_type = '标准四人间'
                          AND r.capacity = 4
                          AND CAST(b.bed_number AS UNSIGNED) > 4
                        """);
            }
        }
        if (updatedRows > 0) {
            try (PreparedStatement logStatement = connection.prepareStatement("""
                    INSERT INTO operation_logs (operator, action, target_type, target_id, detail)
                    VALUES ('system', ?, 'dorm_rooms', 'default-capacity', ?)
                    """)) {
                logStatement.setString(1, migrationAction);
                logStatement.setString(2, "已将默认标准六人间迁移为标准四人间，涉及宿舍数：" + updatedRows);
                logStatement.executeUpdate();
            }
        }
    }

    private void seedUsersIfNeeded(Connection connection) throws SQLException {
        String userSql = """
                INSERT IGNORE INTO users (username, password_hash, role, student_id, enabled)
                VALUES (?, ?, ?, ?, TRUE)
                """;
        try (PreparedStatement statement = connection.prepareStatement(userSql)) {
            statement.setString(1, "admin");
            statement.setString(2, PasswordHasher.hash("admin123"));
            statement.setString(3, "ADMIN");
            statement.setString(4, null);
            statement.addBatch();

            String studentPasswordHash = PasswordHasher.hash("student123");
            try (Statement queryStatement = connection.createStatement();
                 ResultSet resultSet = queryStatement.executeQuery("SELECT student_id FROM students ORDER BY student_id")) {
                while (resultSet.next()) {
                    String studentId = resultSet.getString("student_id");
                    statement.setString(1, studentId);
                    statement.setString(2, studentPasswordHash);
                    statement.setString(3, "USER");
                    statement.setString(4, studentId);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private int tableCount(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private int defaultFloor(int building) {
        return switch (building) {
            case 1, 5, 7, 11 -> 2;
            case 2, 9 -> 4;
            case 3 -> 5;
            case 4, 8, 12 -> 3;
            default -> 1;
        };
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
