package com.aquatrack.dao;

import com.aquatrack.model.DailyReport;
import com.aquatrack.model.PoolLog;
import com.aquatrack.model.UnitUsage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Pool Logs. Every SQL statement is here and uses PreparedStatement.
 * It does not create the table; DatabaseInitializer does that before the app starts.
 * The FOREIGN KEY on unit_number means the database itself refuses a log for a unit that does not exist.
 */
public class PoolLogDao {

    // time_in and status are not in the INSERT, so the table defaults apply (the database clock and 'Inside').
    private static final String SQL_INSERT = """
            INSERT INTO pool_logs (unit_number, resident_count, guest_count)
            VALUES (?, ?, ?)
            """;

    // Column order in the SELECTs matches mapRow().
    private static final String SQL_FIND = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            WHERE log_id = ?
            """;

    private static final String SQL_INSIDE = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            WHERE status = 'Inside'
            ORDER BY time_in, log_id
            """;

    private static final String SQL_RECENT = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            ORDER BY log_id DESC
            LIMIT ?
            """;

    // "AND status = 'Inside'" means checking out the same log twice changes nothing the second time.
    private static final String SQL_CHECK_OUT = """
            UPDATE pool_logs
            SET time_out = datetime('now', 'localtime'), status = 'Left'
            WHERE log_id = ? AND status = 'Inside'
            """;

    private static final String SQL_DELETE =
            "DELETE FROM pool_logs WHERE log_id = ?";

    // COALESCE turns "no rows" (SUM gives NULL) into 0, so an empty pool reads as 0 swimmers.
    private static final String SQL_HEADCOUNT = """
            SELECT COALESCE(SUM(resident_count + guest_count), 0)
            FROM pool_logs
            WHERE status = 'Inside'
            """;

    private static final String SQL_RESIDENTS_INSIDE_BY_UNIT = """
            SELECT COALESCE(SUM(resident_count), 0)
            FROM pool_logs
            WHERE unit_number = ? AND status = 'Inside'
            """;

    // ---- read-only queries for the Billing Clerk and the reports (Parts 5 and 6) ----

    private static final String SQL_WITH_GUESTS = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            WHERE guest_count > 0
            ORDER BY log_id DESC
            LIMIT ?
            """;

    private static final String SQL_BY_UNIT = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            WHERE unit_number = ?
            ORDER BY log_id DESC
            """;

    // date() cuts "2026-10-31 14:05:09" down to "2026-10-31", so a whole day can be compared.
    // A group is counted on the day it CHECKED IN, even if it leaves after midnight.
    private static final String SQL_BY_DATE = """
            SELECT log_id, unit_number, resident_count, guest_count, time_in, time_out, status
            FROM pool_logs
            WHERE date(time_in) = ?
            ORDER BY log_id
            """;

    // One result row per unit. JOIN units brings in the owner name. SUM adds the groups of that unit together.
    // The two usage queries are identical except for the WHERE line.
    private static final String SQL_USAGE_BY_DATE = """
            SELECT p.unit_number, u.owner_name, COUNT(*) AS check_ins,
                   SUM(p.resident_count) AS residents, SUM(p.guest_count) AS guests
            FROM pool_logs p
            JOIN units u ON u.unit_number = p.unit_number
            WHERE date(p.time_in) = ?
            GROUP BY p.unit_number, u.owner_name
            ORDER BY p.unit_number
            """;

    // strftime('%Y-%m', ...) cuts the time down to "2026-10". HAVING hides units with no guests that month,
    // because only guests are billed.
    private static final String SQL_USAGE_BY_MONTH = """
            SELECT p.unit_number, u.owner_name, COUNT(*) AS check_ins,
                   SUM(p.resident_count) AS residents, SUM(p.guest_count) AS guests
            FROM pool_logs p
            JOIN units u ON u.unit_number = p.unit_number
            WHERE strftime('%Y-%m', p.time_in) = ?
            GROUP BY p.unit_number, u.owner_name
            HAVING SUM(p.guest_count) > 0
            ORDER BY p.unit_number
            """;

    // Returns true if the check-in was saved.
    public boolean insert(PoolLog log) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, log.unitNumber());
            ps.setInt(2, log.residentCount());
            ps.setInt(3, log.guestCount());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not save the check-in: " + e.getMessage());
            return false;
        }
    }

    // Returns the log with that id, or null if there is none.
    public PoolLog findById(int logId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND)) {

            ps.setInt(1, logId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not look up the log: " + e.getMessage());
        }

        return null;
    }

    // Every group that is Inside right now (the live roster), oldest check-in first.
    public List<PoolLog> getInsideLogs() {
        List<PoolLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSIDE);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                logs.add(mapRow(rs));
            }

        } catch (SQLException e) {
            System.out.println("Could not load the roster: " + e.getMessage());
        }

        return logs;
    }

    // The newest logs (Inside or Left), newest first, at most "limit" of them.
    public List<PoolLog> getRecentLogs(int limit) {
        List<PoolLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RECENT)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the logs: " + e.getMessage());
        }

        return logs;
    }

    // The newest groups that brought at least one guest, newest first, at most "limit" of them.
    public List<PoolLog> getLogsWithGuests(int limit) {
        List<PoolLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_WITH_GUESTS)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the guest logs: " + e.getMessage());
        }

        return logs;
    }

    // The whole pool history of ONE unit (Inside and Left), newest first.
    public List<PoolLog> getLogsByUnit(int unitNumber) {
        return queryLogs(SQL_BY_UNIT, unitNumber);
    }

    // Every group that checked in on one date ("yyyy-MM-dd"), oldest first.
    public List<PoolLog> getLogsByDate(String date) {
        return queryLogs(SQL_BY_DATE, date);
    }

    // The Daily Pool Usage & Guest Report data for one date ("yyyy-MM-dd").
    // The screen, the CSV and (Part 7) the PDF all call this ONE method.
    public DailyReport getDailyReport(String date) {
        return new DailyReport(date, queryUsage(SQL_USAGE_BY_DATE, date));
    }

    // Guests per unit for one month ("yyyy-MM"). Only units that brought guests are listed.
    public List<UnitUsage> getMonthlyGuestUsage(String yearMonth) {
        return queryUsage(SQL_USAGE_BY_MONTH, yearMonth);
    }

    // Sets time_out to now and status to Left.
    // Returns true if a row was updated, false if that log does not exist or is already Left.
    public boolean checkOut(int logId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CHECK_OUT)) {

            ps.setInt(1, logId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not check out: " + e.getMessage());
            return false;
        }
    }

    // Returns true if a row was deleted, false if no log has that id (or the delete failed).
    public boolean deleteLog(int logId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, logId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not delete the log: " + e.getMessage());
            return false;
        }
    }

    // Residents + guests who are Inside right now. This is what the capacity bar shows.
    public int getCurrentHeadcount() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_HEADCOUNT);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.out.println("Could not count swimmers: " + e.getMessage());
        }

        return 0;
    }

    // How many residents of one unit are Inside right now (guests are not counted).
    public int countResidentsInside(int unitNumber) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESIDENTS_INSIDE_BY_UNIT)) {

            ps.setInt(1, unitNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not count residents inside: " + e.getMessage());
        }

        return 0;
    }

    // Runs a log SELECT that has exactly one "?" (an int or a text) and maps every row.
    private List<PoolLog> queryLogs(String sql, Object parameter) {
        List<PoolLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, parameter);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the logs: " + e.getMessage());
        }

        return logs;
    }

    // Runs one of the two usage queries (one text parameter) and builds a UnitUsage per row.
    private List<UnitUsage> queryUsage(String sql, String period) {
        List<UnitUsage> usage = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, period);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usage.add(new UnitUsage(
                            rs.getInt("unit_number"),
                            rs.getString("owner_name"),
                            rs.getInt("check_ins"),
                            rs.getInt("residents"),
                            rs.getInt("guests")));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the usage report: " + e.getMessage());
        }

        return usage;
    }

    // Builds a PoolLog from the current row of a SELECT that lists the seven columns in this order.
    // time_out is NULL for a group that is still Inside, and getString gives null for that.
    private PoolLog mapRow(ResultSet rs) throws SQLException {
        return new PoolLog(
                rs.getInt("log_id"),
                rs.getInt("unit_number"),
                rs.getInt("resident_count"),
                rs.getInt("guest_count"),
                rs.getString("time_in"),
                rs.getString("time_out"),
                rs.getString("status"));
    }
}
