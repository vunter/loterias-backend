package br.com.loterias.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;

public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final Pattern VALID_DB_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    public static void createDatabaseIfNotExists(String url, String username, String password) {
        if (url == null || !url.contains("postgresql")) {
            return;
        }

        String dbName = extractDatabaseName(url);
        if (!VALID_DB_NAME.matcher(dbName).matches()) {
            log.error("Invalid database name '{}'. Must match [a-zA-Z_][a-zA-Z0-9_]*", dbName);
            return;
        }

        String baseUrl = url.substring(0, url.lastIndexOf("/")) + "/postgres";

        try (Connection conn = DriverManager.getConnection(baseUrl, username, password)) {
            // Use PreparedStatement for the existence check
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    log.info("Creating database '{}'...", dbName);
                    // CREATE DATABASE doesn't support parameter binding, but dbName is validated above
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
                    }
                    log.info("Database '{}' created successfully", dbName);
                } else {
                    log.debug("Database '{}' already exists", dbName);
                }
            }
            }
        } catch (Exception e) {
            log.warn("Could not check/create database: {}", e.getMessage());
        }
    }

    private static String extractDatabaseName(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?")[0];
        return withoutParams.substring(withoutParams.lastIndexOf("/") + 1);
    }
}
