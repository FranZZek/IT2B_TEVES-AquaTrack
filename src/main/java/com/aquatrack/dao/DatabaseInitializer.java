package com.aquatrack.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes sure every table exists before the first menu appears.
 * Safe to run on every start-up: CREATE TABLE IF NOT EXISTS never touches existing data,
 * so an older aquatrack.db that only has the users table simply gets the new tables added.
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

    // The unit number (101, 102, ...) is typed by the admin, so it is the primary key itself.
    private static final String CREATE_UNITS_TABLE = """
            CREATE TABLE IF NOT EXISTS units (
                unit_number      INTEGER PRIMARY KEY CHECK (unit_number > 0),
                owner_name       TEXT NOT NULL,
                contact_number   TEXT NOT NULL,
                occupancy_status TEXT NOT NULL DEFAULT 'Occupied'
                                 CHECK (occupancy_status IN ('Occupied', 'Vacant'))
            )
            """;

    // A resident belongs to exactly one unit. The FOREIGN KEY stops a resident of a unit that does not exist.
    private static final String CREATE_RESIDENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS residents (
                resident_id  INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name   TEXT NOT NULL,
                last_name    TEXT NOT NULL,
                age_category TEXT NOT NULL CHECK (age_category IN ('Adult', 'Minor')),
                unit_number  INTEGER NOT NULL,
                FOREIGN KEY (unit_number) REFERENCES units (unit_number)
            )
            """;

    // One row = one GROUP of a unit in the pool. time_in and status get their values from the defaults.
    // The CHECKs mean the database itself refuses a negative count or an empty group.
    private static final String CREATE_POOL_LOGS_TABLE = """
            CREATE TABLE IF NOT EXISTS pool_logs (
                log_id         INTEGER PRIMARY KEY AUTOINCREMENT,
                unit_number    INTEGER NOT NULL,
                resident_count INTEGER NOT NULL CHECK (resident_count >= 0),
                guest_count    INTEGER NOT NULL CHECK (guest_count >= 0),
                time_in        TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                time_out       TEXT,
                status         TEXT NOT NULL DEFAULT 'Inside' CHECK (status IN ('Inside', 'Left')),
                CHECK (resident_count + guest_count > 0),
                FOREIGN KEY (unit_number) REFERENCES units (unit_number)
            )
            """;

    // The audit trail (Deliverable 8). user_id is the staff member who did the action.
    // ON DELETE SET NULL: deleting a staff account keeps their old entries (user_id becomes NULL)
    // instead of failing or erasing the history. The description still names the person.
    private static final String CREATE_ACTIVITY_LOGS_TABLE = """
            CREATE TABLE IF NOT EXISTS activity_logs (
                audit_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     INTEGER,
                action_type TEXT NOT NULL,
                description TEXT NOT NULL,
                timestamp   TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
            )
            """;

    // The columns the DAOs rely on, per table. Used to spot a database file from an older version.
    private static final Map<String, List<String>> EXPECTED_COLUMNS = Map.of(
            "users", List.of("id", "username", "password_hash", "full_name", "role", "status"),
            "units", List.of("unit_number", "owner_name", "contact_number", "occupancy_status"),
            "residents", List.of("resident_id", "first_name", "last_name", "age_category", "unit_number"),
            "pool_logs", List.of("log_id", "unit_number", "resident_count", "guest_count",
                    "time_in", "time_out", "status"),
            "activity_logs", List.of("audit_id", "user_id", "action_type", "description", "timestamp"));

    // Parents go first: a table with a FOREIGN KEY must come after the table it points to.
    private static final String[] TABLES = {
            CREATE_USERS_TABLE,
            CREATE_UNITS_TABLE,
            CREATE_RESIDENTS_TABLE,
            CREATE_POOL_LOGS_TABLE,
            CREATE_ACTIVITY_LOGS_TABLE
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
            for (Map.Entry<String, List<String>> expected : EXPECTED_COLUMNS.entrySet()) {
                if (!tableHasColumns(conn, expected.getKey(), expected.getValue())) {
                    System.out.println("Your aquatrack.db was created by an older version of AquaTrack,");
                    System.out.println("so its \"" + expected.getKey() + "\" table has different columns.");
                    System.out.println("Delete the aquatrack.db file (in the project folder) and start the app again.");
                    return false;
                }
            }
            return true;

        } catch (SQLException e) {
            System.out.println("Database setup failed: " + e.getMessage());
            return false;
        }
    }

    // True when the table has every column in "columns". The table name comes from the fixed map
    // above (never from the user), because PRAGMA cannot take a ? placeholder.
    private static boolean tableHasColumns(Connection conn, String table, List<String> columns)
            throws SQLException {
        Set<String> found = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString("name").toLowerCase());
            }
        }
        return found.containsAll(columns);
    }
}
