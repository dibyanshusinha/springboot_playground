package com.dibyanshusinha.apiserv.e2e.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public final class E2EDatabase {

    private static final String DEFAULT_DB_HOST = "localhost";
    private static final String DEFAULT_DB_PORT = "5432";
    private static final String DEFAULT_DB_NAME = "productdb";
    private static final String LOCAL_DB_USERNAME = "product_user";
    private static final String LOCAL_DB_PASSWORD = "product_password";

    private final boolean available;
    private final String environment;
    private final String url;
    private final String username;
    private final String password;

    private E2EDatabase(boolean available, String environment, String url, String username, String password) {
        this.available = available;
        this.environment = environment;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static E2EDatabase fromConfiguration() {
        String environment = config("e2e.environment", "E2E_ENVIRONMENT")
                .orElseGet(() -> config("e2e.target-env", "E2E_TARGET_ENV").orElse("local"));
        String enabledMode = config("e2e.db.enabled", "E2E_DB_ENABLED")
                .orElseGet(() -> config("e2e.db.cleanup", "E2E_DB_CLEANUP").orElse("auto"))
                .toLowerCase(Locale.ROOT);
        boolean skipDocker = Boolean.parseBoolean(config("e2e.skipDocker", "E2E_SKIP_DOCKER").orElse("true"));

        Optional<String> configuredUrl = config("e2e.db.url", "E2E_DB_URL");
        String dbHost = config("e2e.db.host", "E2E_DB_HOST").orElse(DEFAULT_DB_HOST);
        String dbPort = config("e2e.db.port", "E2E_DB_PORT").orElse(DEFAULT_DB_PORT);
        String dbName = config("e2e.db.name", "E2E_DB_NAME").orElse(DEFAULT_DB_NAME);
        Optional<String> configuredUsername = config("e2e.db.username", "E2E_DB_USERNAME");
        Optional<String> configuredPassword = config("e2e.db.password", "E2E_DB_PASSWORD");

        boolean localComposeRun = !skipDocker || "local".equalsIgnoreCase(environment);
        boolean hasExplicitDbConfig = configuredUrl.isPresent()
                || config("e2e.db.host", "E2E_DB_HOST").isPresent()
                || config("e2e.db.port", "E2E_DB_PORT").isPresent()
                || config("e2e.db.name", "E2E_DB_NAME").isPresent();
        boolean available = switch (enabledMode) {
            case "true", "enabled", "on" -> true;
            case "false", "disabled", "off" -> false;
            default -> localComposeRun || hasExplicitDbConfig;
        };

        String url = configuredUrl.orElse(available ? jdbcUrl(dbHost, dbPort, dbName) : "");
        String username = configuredUsername.orElse(localComposeRun ? LOCAL_DB_USERNAME : "");
        String password = configuredPassword.orElse(localComposeRun ? LOCAL_DB_PASSWORD : "");

        return new E2EDatabase(available && !url.isBlank(), environment, url, username, password);
    }

    private static String jdbcUrl(String host, String port, String databaseName) {
        return "jdbc:postgresql://%s:%s/%s".formatted(host, port, databaseName);
    }

    public boolean isAvailable() {
        return available;
    }

    public Connection getConnection() throws SQLException {
        if (!available) {
            throw new SQLException("E2E database is not configured for environment '%s'. Configure e2e.db.url or e2e.db.host/e2e.db.port/e2e.db.name plus credentials.".formatted(environment));
        }
        return DriverManager.getConnection(url, username, password);
    }

    public void execute(SqlConsumer<Connection> operation) throws SQLException {
        try (Connection connection = getConnection()) {
            operation.accept(connection);
        }
    }

    public <T> T query(SqlFunction<Connection, T> operation) throws SQLException {
        try (Connection connection = getConnection()) {
            return operation.apply(connection);
        }
    }

    public int deleteProductsBySkuPrefix(String skuPrefix) {
        if (!available) {
            return 0;
        }

        try {
            return query(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE sku LIKE ?")) {
                    statement.setString(1, skuPrefix + "%");
                    return statement.executeUpdate();
                }
            });
        } catch (SQLException ex) {
            System.err.printf(
                    "E2E DB cleanup skipped for environment '%s'. Configure e2e.db.url or e2e.db.host/e2e.db.port/e2e.db.name plus credentials if DB access is required. Cause: %s%n",
                    environment,
                    ex.getMessage()
            );
            return 0;
        }
    }

    public long countProductsBySku(String sku) throws SQLException {
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM products WHERE sku = ?")) {
                statement.setString(1, sku);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    private static Optional<String> config(String propertyName, String envName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue);
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue);
        }

        return Optional.empty();
    }

    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }
}
