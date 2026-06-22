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
            insertStudent(statement, "20230007", "孙悦", "计算机系", "软件2302", "4-301", "0571-4301", "1");
            insertStudent(statement, "20230008", "周杰", "计算机系", "软件2302", "4-301", "0571-4301", "2");
            insertStudent(statement, "20230009", "吴桐", "计算机系", "网络2302", "4-301", "0571-4301", "3");
            insertStudent(statement, "20230010", "郑琳", "信息工程系", "物联2301", "4-302", "0571-4302", "1");
            insertStudent(statement, "20230011", "冯凯", "信息工程系", "大数据2301", "4-302", "0571-4302", "2");
            insertStudent(statement, "20230012", "陈雨", "信息工程系", "大数据2301", "4-302", "0571-4302", "3");
            insertStudent(statement, "20230013", "蒋欣", "经济管理系", "会计2301", "4-303", "0571-4303", "1");
            insertStudent(statement, "20230014", "何晨", "经济管理系", "电商2301", "4-303", "0571-4303", "2");
            insertStudent(statement, "20230015", "马宁", "外语系", "英语2302", "4-303", "0571-4303", "3");
            insertStudent(statement, "20230016", "朱磊", "机电工程系", "机电2302", "5-201", "0571-5201", "1");
            insertStudent(statement, "20230017", "高洁", "机电工程系", "智能制造2301", "5-201", "0571-5201", "2");
            insertStudent(statement, "20230018", "林浩", "艺术设计系", "视觉2301", "5-202", "0571-5202", "1");
            insertStudent(statement, "20230019", "郭敏", "艺术设计系", "环艺2301", "5-202", "0571-5202", "2");
            insertStudent(statement, "20230020", "宋佳", "经济管理系", "电商2302", "2-403", "0571-2403", "1");
            insertStudent(statement, "20230021", "唐宇", "计算机系", "软件2303", "3-503", "0571-3503", "1");
            insertStudent(statement, "20230022", "许诺", "计算机系", "软件2303", "3-503", "0571-3503", "2");
            insertStudent(statement, "20230023", "沈琪", "信息工程系", "物联2303", "3-504", "0571-3504", "1");
            insertStudent(statement, "20230024", "邓超", "外语系", "商务英语2301", "2-404", "0571-2404", "1");
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
