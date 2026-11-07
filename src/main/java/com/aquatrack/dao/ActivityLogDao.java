package com.aquatrack.dao;

import com.aquatrack.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Activity Log (the audit trail). Every SQL statement is here and uses
 * PreparedStatement. It does not create the table; DatabaseInitializer does that before the app starts.
 * Every menu receives the same ActivityLogDao (created in Main) and calls logAction() after a key action.
 */
public class ActivityLogDao {

    // timestamp is not in the INSERT, so the table default (the database clock, local time) applies.
    private static final String SQL_INSERT = """
            INSERT INTO activity_logs (user_id, action_type, description)
            VALUES (?, ?, ?)
            """;

    // Column order in the SELECTs matches mapRow(). audit_id is the tie-breaker for entries
    // written in the same second, so the newest entry is always first.
    private static final String SQL_ALL = """
            SELECT audit_id, user_id, action_type, description, timestamp
            FROM activity_logs
            ORDER BY timestamp DESC, audit_id DESC
            """;

    private static final String SQL_BY_USER = """
            SELECT audit_id, user_id, action_type, description, timestamp
            FROM activity_logs
            WHERE user_id = ?
            ORDER BY timestamp DESC, audit_id DESC
            """;

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM activity_logs";

    // "?" receives text such as "-30 days", which SQLite's datetime() understands.
    private static final String SQL_DELETE_OLDER = """
            DELETE FROM activity_logs
            WHERE timestamp < datetime('now', 'localtime', ?)
            """;

    private static final String SQL_DELETE_ALL =
            "DELETE FROM activity_logs";

    // Writes ONE audit entry. Returns true if it was saved.
    // A failed log must never stop the real action, so errors are only printed here.
    public boolean logAction(int userId, String actionType, String description) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, userId);
            ps.setString(2, actionType);
            ps.setString(3, description);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not write the audit log: " + e.getMessage());
            return false;
        }
    }

    // Every recorded action, newest first.
    public List<ActivityLog> getAllLogs() {
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                logs.add(mapRow(rs));
            }

        } catch (SQLException e) {
            System.out.println("Could not load the audit logs: " + e.getMessage());
        }

        return logs;
    }

    // The actions done by ONE user, newest first.
    public List<ActivityLog> getLogsByUserId(int userId) {
        List<ActivityLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BY_USER)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the audit logs: " + e.getMessage());
        }

        return logs;
    }

    // How many entries the audit log holds right now.
    public int countLogs() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.out.println("Could not count the audit logs: " + e.getMessage());
        }

        return 0;
    }

    // Deletes the entries that are more than "days" days old. Returns how many were deleted (0 if it failed).
    public int clearLogsOlderThan(int days) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_OLDER)) {

            ps.setString(1, "-" + days + " days");
            return ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Could not clear the audit logs: " + e.getMessage());
            return 0;
        }
    }

    // Deletes EVERY entry. Returns how many were deleted (0 if it failed).
    public int clearAllLogs() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_ALL)) {

            return ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Could not clear the audit logs: " + e.getMessage());
            return 0;
        }
    }

    // Builds an ActivityLog from the current row of a SELECT that lists the five columns in this order.
    // user_id is NULL after the staff account was deleted, and getInt gives 0 for that, so wasNull() decides.
    private ActivityLog mapRow(ResultSet rs) throws SQLException {
        int userId = rs.getInt("user_id");
        Integer userIdOrNull = rs.wasNull() ? null : userId;

        return new ActivityLog(
                rs.getInt("audit_id"),
                userIdOrNull,
                rs.getString("action_type"),
                rs.getString("description"),
                rs.getString("timestamp"));
    }
}
