package com.personalbot.database;

import java.io.File;
import java.net.URI;
import java.sql.*;

public class DatabaseManager {
    private String jdbcUrl;
    private String dbUser = "";
    private String dbPassword = "";
    private boolean dbAvailable = false;

    public DatabaseManager() {
        initConnectionInfo();
        if (jdbcUrl != null) {
            initTables();
        }
    }

    public boolean isDbAvailable() {
        return dbAvailable;
    }

    private void initConnectionInfo() {
        String envDbUrl = System.getenv("DATABASE_URL");
        if (envDbUrl == null || envDbUrl.isEmpty()) {
            envDbUrl = System.getenv("JDBC_DATABASE_URL");
        }

        if (envDbUrl != null && !envDbUrl.trim().isEmpty()) {
            System.out.println("[DatabaseManager] Detected cloud DATABASE_URL, initializing PostgreSQL...");
            try {
                if (envDbUrl.startsWith("postgres://") || envDbUrl.startsWith("postgresql://")) {
                    URI uri = new URI(envDbUrl.replace("postgres://", "http://").replace("postgresql://", "http://"));
                    String host = uri.getHost();
                    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String path = uri.getPath();
                    String userInfo = uri.getUserInfo();

                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        dbUser = parts[0];
                        dbPassword = parts[1];
                    }

                    jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?sslmode=require";
                } else if (envDbUrl.startsWith("jdbc:")) {
                    jdbcUrl = envDbUrl;
                }
            } catch (Exception e) {
                System.err.println("[DatabaseManager] Failed to parse DATABASE_URL: " + e.getMessage());
            }
        }

        if (jdbcUrl == null) {
            try {
                // Try loading SQLite driver
                Class.forName("org.sqlite.JDBC");
                File dir = new File("data");
                if (!dir.exists()) dir.mkdirs();
                jdbcUrl = "jdbc:sqlite:data/bot_database.db";
                System.out.println("[DatabaseManager] Using local SQLite database...");
            } catch (ClassNotFoundException e) {
                System.out.println("[DatabaseManager] SQLite JDBC driver not on classpath. Using local JSON storage mode.");
                jdbcUrl = null;
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (jdbcUrl == null) return null;
        if (dbUser.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        } else {
            return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
        }
    }

    private void initTables() {
        boolean isPostgres = jdbcUrl.contains("postgresql");

        String createRemindersTable = isPostgres ?
                "CREATE TABLE IF NOT EXISTS reminders (" +
                        "id VARCHAR(64) PRIMARY KEY, " +
                        "text TEXT NOT NULL, " +
                        "trigger_time_ms BIGINT NOT NULL, " +
                        "created_at_ms BIGINT NOT NULL, " +
                        "triggered BOOLEAN DEFAULT FALSE)" :
                "CREATE TABLE IF NOT EXISTS reminders (" +
                        "id TEXT PRIMARY KEY, " +
                        "text TEXT NOT NULL, " +
                        "trigger_time_ms INTEGER NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL, " +
                        "triggered INTEGER DEFAULT 0)";

        String createHabitsTable = isPostgres ?
                "CREATE TABLE IF NOT EXISTS habits (" +
                        "id VARCHAR(64) PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "streak INT DEFAULT 0, " +
                        "last_completed_date VARCHAR(32))" :
                "CREATE TABLE IF NOT EXISTS habits (" +
                        "id TEXT PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "streak INTEGER DEFAULT 0, " +
                        "last_completed_date TEXT)";

        try (Connection conn = getConnection()) {
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createRemindersTable);
                    stmt.execute(createHabitsTable);
                    dbAvailable = true;
                    System.out.println("[DatabaseManager] Database tables initialized successfully.");
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] Database unavailable: " + e.getMessage() + ". Falling back to local JSON storage.");
            dbAvailable = false;
        }
    }
}
