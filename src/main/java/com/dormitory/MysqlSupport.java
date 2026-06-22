package com.dormitory;

public final class MysqlSupport {
    private MysqlSupport() {
    }

    public static MysqlConnectionFactory initializedConnectionFactory() {
        DatabaseConfig config = DatabaseConfig.load();
        MysqlConnectionFactory connectionFactory = new MysqlConnectionFactory(config);
        new MysqlDatabaseInitializer(connectionFactory).initialize();
        return connectionFactory;
    }
}
