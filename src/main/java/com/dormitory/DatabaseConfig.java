package com.dormitory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class DatabaseConfig {
    private static final String DEFAULT_PARAMS = "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";

    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private final String params;
    private final String explicitJdbcUrl;

    private DatabaseConfig(String host, String port, String database, String username, String password, String params, String explicitJdbcUrl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.params = params;
        this.explicitJdbcUrl = explicitJdbcUrl;
    }

    public static DatabaseConfig load() {
        Properties properties = new Properties();
        Path configPath = Path.of("config", "database.properties");
        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException e) {
                throw new IllegalStateException("读取数据库配置失败：" + e.getMessage(), e);
            }
        }

        String jdbcUrl = firstNonBlank(env("DB_URL"), properties.getProperty("db.url"));
        String host = firstNonBlank(env("DB_HOST"), properties.getProperty("db.host", "localhost"));
        String port = firstNonBlank(env("DB_PORT"), properties.getProperty("db.port", "3306"));
        String database = firstNonBlank(env("DB_NAME"), properties.getProperty("db.name", "student_dormitory"));
        String username = firstNonBlank(env("DB_USER"), properties.getProperty("db.user", "root"));
        String password = firstNonBlank(env("DB_PASSWORD"), properties.getProperty("db.password", ""));
        String params = firstNonBlank(env("DB_PARAMS"), properties.getProperty("db.params", DEFAULT_PARAMS));
        return new DatabaseConfig(host, port, database, username, password, params, jdbcUrl);
    }

    public String jdbcUrl() {
        if (!isBlank(explicitJdbcUrl)) {
            return explicitJdbcUrl;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
    }

    public String serverJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?" + params;
    }

    public boolean hasExplicitJdbcUrl() {
        return !isBlank(explicitJdbcUrl);
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
