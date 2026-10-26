package com.aquatrack.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes sure every table exists before the first menu appears.
 * Safe to run on every start-up: CREATE TABLE IF NOT EXISTS never touches existing data.
 */
public class DatabaseInitializer {

    // The Role list here must match the roles offered in ui/Authentication.
    // Status: a new account is PENDING until a Building Admin approves it (see UserDao.approveUser).
    private static final String CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT NOT NULL,
                full_name     TEXT NOT NULL,
                role          TEXT NOT NULL
                              CHECK (role IN ('Building Admin', 'Pool Attendant', 'Billing Clerk')),
                status        TEXT NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'APPROVED'))
            )
            """;

    // The columns UserDao relies on in the users table.
    private static final List<String> USERS_COLUMNS =
            List.of("id", "username", "password_hash", "full_name", "role", "status");

    // Add future tables here. Parents go first: a table with a FOREIGN KEY must come
    // after the table it points to.
    private static final String[] TABLES = {
            CREATE_USERS_TABLE
    };

    // Returns true when every table is ready, false if setup failed.
    public static boolean initialize() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String sql : TABLES) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.execute();
                }
            }

            // CREATE TABLE IF NOT EXISTS leaves an existing table exactly as it is, so a
            // database file from an older version of the app would still have the old columns.
            // Catch that here with a clear message instead of failing later inside a menu.
            if (!usersTableHasCurrentColumns(conn)) {
                System.out.println("Your aquatrack.db was created by an older version of AquaTrack,");
                System.out.println("so its users table has different columns.");
                System.out.println("Delete the aquatrack.db file (in the project folder) and start the app again.");
                return false;
            }
            return true;

        } catch (SQLException e) {
            System.out.println("Database setup failed: " + e.getMessage());
            return false;
        }
    }

    // True when the users table has every column listed in USERS_COLUMNS.
    private static boolean usersTableHasCurrentColumns(Connection conn) throws SQLException {
        Set<String> found = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(users)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString("name").toLowerCase());
            }
        }
        return found.containsAll(USERS_COLUMNS);
    }
}
