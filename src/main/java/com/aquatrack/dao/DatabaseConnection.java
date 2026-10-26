package com.aquatrack.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQLite connection setup. All JDBC connection logic lives here.
 * (Creating the tables is DatabaseInitializer's job.)
 */
public class DatabaseConnection {

    // Created in the project root (working directory) on first run.
    private static final String URL = "jdbc:sqlite:aquatrack.db";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);

        // SQLite ignores FOREIGN KEY rules unless this is switched on for EACH new connection.
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA foreign_keys = ON;")) {
            ps.execute();
        }
        return conn;
    }
}
