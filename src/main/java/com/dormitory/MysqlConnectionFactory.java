package com.dormitory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MysqlConnectionFactory {
    private final DatabaseConfig config;

    public MysqlConnectionFactory(DatabaseConfig config) {
        this.config = config;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 MySQL JDBC 驱动，请先运行 run.ps1 自动下载依赖。", e);
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
    }

    public Connection openServerConnection() throws SQLException {
        return DriverManager.getConnection(config.serverJdbcUrl(), config.username(), config.password());
    }

    public DatabaseConfig config() {
        return config;
    }
}
