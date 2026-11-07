package com.aquatrack.dao;

import com.aquatrack.model.Resident;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Residents. Every SQL statement is here and uses PreparedStatement.
 * It does not create the table; DatabaseInitializer does that before the app starts.
 * The FOREIGN KEY on unit_number means the database itself refuses a resident for a unit that does not exist.
 */
public class ResidentDao {

    // Column order in the SELECTs matches mapRow().
    private static final String SQL_INSERT = """
            INSERT INTO residents (first_name, last_name, age_category, unit_number)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SQL_FIND = """
            SELECT resident_id, first_name, last_name, age_category, unit_number
            FROM residents
            WHERE resident_id = ?
            """;

    private static final String SQL_ALL = """
            SELECT resident_id, first_name, last_name, age_category, unit_number
            FROM residents
            ORDER BY unit_number, resident_id
            """;

    private static final String SQL_BY_UNIT = """
            SELECT resident_id, first_name, last_name, age_category, unit_number
            FROM residents
            WHERE unit_number = ?
            ORDER BY resident_id
            """;

    // The unit is not changed here: a resident only moves out (delete), it is not moved to another unit.
    private static final String SQL_UPDATE = """
            UPDATE residents
            SET first_name = ?, last_name = ?, age_category = ?
            WHERE resident_id = ?
            """;

    private static final String SQL_DELETE =
            "DELETE FROM residents WHERE resident_id = ?";

    private static final String SQL_COUNT_BY_UNIT =
            "SELECT COUNT(*) FROM residents WHERE unit_number = ?";

    // Returns true if the resident was saved.
    public boolean insert(Resident resident) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setString(1, resident.firstName());
            ps.setString(2, resident.lastName());
            ps.setString(3, resident.ageCategory());
            ps.setInt(4, resident.unitNumber());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not save the resident: " + e.getMessage());
            return false;
        }
    }

    // Returns the resident with that id, or null if there is none.
    public Resident findById(int residentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND)) {

            ps.setInt(1, residentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not look up the resident: " + e.getMessage());
        }

        return null;
    }

    // Every resident of the building, ordered by unit and then by id.
    public List<Resident> getAllResidents() {
        List<Resident> residents = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                residents.add(mapRow(rs));
            }

        } catch (SQLException e) {
            System.out.println("Could not load the residents: " + e.getMessage());
        }

        return residents;
    }

    // The residents registered to ONE unit.
    public List<Resident> getResidentsByUnit(int unitNumber) {
        List<Resident> residents = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BY_UNIT)) {

            ps.setInt(1, unitNumber);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    residents.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not load the residents: " + e.getMessage());
        }

        return residents;
    }

    // Saves the name and age category of an existing resident.
    // Returns true if a row was updated, false if no resident has that id (or the update failed).
    public boolean update(Resident resident) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            ps.setString(1, resident.firstName());
            ps.setString(2, resident.lastName());
            ps.setString(3, resident.ageCategory());
            ps.setInt(4, resident.residentId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not update the resident: " + e.getMessage());
            return false;
        }
    }

    // Returns true if a row was deleted, false if no resident has that id (or the delete failed).
    public boolean delete(int residentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, residentId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not remove the resident: " + e.getMessage());
            return false;
        }
    }

    // How many residents are registered to one unit. The pool check-in uses this to stop
    // a group from claiming more residents than the unit really has.
    public int countByUnit(int unitNumber) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT_BY_UNIT)) {

            ps.setInt(1, unitNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not count the residents: " + e.getMessage());
        }

        return 0;
    }

    // Builds a Resident from the current row of a SELECT that lists the five columns in this order.
    private Resident mapRow(ResultSet rs) throws SQLException {
        return new Resident(
                rs.getInt("resident_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("age_category"),
                rs.getInt("unit_number"));
    }
}
