package com.gestor.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private static final HikariDataSource DATA_SOURCE = createDataSource();
    private Database() {}

    private static HikariDataSource createDataSource() {
        Properties p = new Properties();
        try (InputStream in = Database.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IllegalStateException("No se encontró application.properties");
            p.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOr("DB_URL", p.getProperty("db.url")));
        config.setDriverClassName(envOr("DB_DRIVER", p.getProperty("db.driver", "com.mysql.cj.jdbc.Driver")));
        config.setUsername(envOr("DB_USER", p.getProperty("db.user")));
        config.setPassword(envOr("DB_PASSWORD", p.getProperty("db.password")));
        config.setMaximumPoolSize(Integer.parseInt(envOr("DB_POOL_SIZE", p.getProperty("db.pool.size", "10"))));
        config.setPoolName("GestorPool");
        config.setConnectionTimeout(10_000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static Connection getConnection() throws SQLException { return DATA_SOURCE.getConnection(); }
    public static void close() { DATA_SOURCE.close(); }
}
