package com.aquatrack.dao;

import com.aquatrack.model.Unit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Condo Units. Every SQL statement is here and uses PreparedStatement.
 * It does not create the table; DatabaseInitializer does that before the app starts.
 * There is no delete: the proposal only creates and modifies units, and pool logs point to them.
 */
public class UnitDao {

    // Column order in the SELECTs matches mapRow().
    private static final String SQL_INSERT = """
            INSERT INTO units (unit_number, owner_name, contact_number, occupancy_status)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SQL_FIND = """
            SELECT unit_number, owner_name, contact_number, occupancy_status
            FROM units
            WHERE unit_number = ?
            """;

    private static final String SQL_ALL = """
            SELECT unit_number, owner_name, contact_number, occupancy_status
            FROM units
            ORDER BY unit_number
            """;

    // The unit number is the primary key, so it is never changed here.
    private static final String SQL_UPDATE = """
            UPDATE units
            SET owner_name = ?, contact_number = ?, occupancy_status = ?
            WHERE unit_number = ?
            """;

    // Returns true if the unit was saved (false if that unit number already exists).
    public boolean insert(Unit unit) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, unit.unitNumber());
            ps.setString(2, unit.ownerName());
            ps.setString(3, unit.contactNumber());
            ps.setString(4, unit.occupancyStatus());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not save the unit: " + e.getMessage());
            return false;
        }
    }

    // Returns the unit with that number, or null if there is none.
    public Unit findByUnitNumber(int unitNumber) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND)) {

            ps.setInt(1, unitNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            System.out.println("Could not look up the unit: " + e.getMessage());
        }

        return null;
    }

    // Every unit, lowest number first.
    public List<Unit> getAllUnits() {
        List<Unit> units = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                units.add(mapRow(rs));
            }

        } catch (SQLException e) {
            System.out.println("Could not load the units: " + e.getMessage());
        }

        return units;
    }

    // Saves the owner, contact and occupancy of an existing unit.
    // Returns true if a row was updated, false if no unit has that number (or the update failed).
    public boolean update(Unit unit) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            ps.setString(1, unit.ownerName());
            ps.setString(2, unit.contactNumber());
            ps.setString(3, unit.occupancyStatus());
            ps.setInt(4, unit.unitNumber());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not update the unit: " + e.getMessage());
            return false;
        }
    }

    // Builds a Unit from the current row of a SELECT that lists the four columns in this order.
    private Unit mapRow(ResultSet rs) throws SQLException {
        return new Unit(
                rs.getInt("unit_number"),
                rs.getString("owner_name"),
                rs.getString("contact_number"),
                rs.getString("occupancy_status"));
    }
}
