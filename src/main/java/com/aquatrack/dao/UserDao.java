package com.aquatrack.dao;

import com.aquatrack.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Data Access Object for Users. Every SQL statement is here and uses PreparedStatement.
 * It does not create tables; DatabaseInitializer does that before the app starts.
 */
public class UserDao {

    // Every statement is ONE complete string with ? placeholders: no SQL is ever built by
    // joining strings together. Column order in the SELECTs matches mapRow().
    private static final String SQL_INSERT =
            "INSERT INTO users (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)";

    private static final String SQL_USERNAME_EXISTS =
            "SELECT 1 FROM users WHERE username = ?";

    private static final String SQL_LOGIN = """
            SELECT id, username, password_hash, full_name, role, status
            FROM users
            WHERE username = ? AND password_hash = ?
            """;

    private static final String SQL_ALL_USERS = """
            SELECT id, username, password_hash, full_name, role, status
            FROM users
            ORDER BY id
            """;

    private static final String SQL_PENDING_USERS = """
            SELECT id, username, password_hash, full_name, role, status
            FROM users
            WHERE status = 'PENDING'
            ORDER BY id
            """;

    private static final String SQL_APPROVE_USER =
            "UPDATE users SET status = 'APPROVED' WHERE id = ?";

    private static final String SQL_UPDATE_ROLE_AND_STATUS =
            "UPDATE users SET role = ?, status = ? WHERE id = ?";

    // Turns a plain password into a SHA-256 hex string, so the database never stores the real password.
    public static String hashPassword(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // status is not in the INSERT, so the table default (PENDING) is applied.
    public boolean insert(User user) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setString(1, user.username());
            ps.setString(2, user.passwordHash());
            ps.setString(3, user.fullName());
            ps.setString(4, user.role());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                System.out.println("That username is already taken.");
            } else {
                System.out.println("Registration failed. Please try again.");
            }
            return false;
        }
    }

    public boolean usernameExists(String username) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_USERNAME_EXISTS)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.out.println("Could not check username: " + e.getMessage());
            return false;
        }
    }

    // Returns the matching user, or null if the username/password pair is wrong.
    public User login(String username, String plainPassword) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOGIN)) {

            ps.setString(1, username);
            ps.setString(2, hashPassword(plainPassword));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            System.out.println("Login failed: " + e.getMessage());
        }

        return null;
    }

    // ------------------------------------------------------- admin queries

    // Every registered account, whatever its role or status. Ordered by id.
    public List<User> getAllUsers() {
        return queryUsers(SQL_ALL_USERS);
    }

    // Every account still waiting for approval (status = 'PENDING'), across ALL roles.
    public List<User> getPendingUsers() {
        return queryUsers(SQL_PENDING_USERS);
    }

    // Changes one account from PENDING to APPROVED.
    // Returns true if a row was updated, false if no account has that id (or the update failed).
    public boolean approveUser(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_APPROVE_USER)) {

            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not approve user: " + e.getMessage());
            return false;
        }
    }

    // Changes the role and status of one account (used by the Admin "Update User" option).
    // The status is converted to upper case first so it always matches the CHECK (PENDING / APPROVED).
    // The role is NOT upper-cased: the stored roles are mixed case ("Building Admin", ...),
    // so upper-casing them would break the CHECK on users.role. AdminMenu sends the exact stored form.
    // Returns true if a row was updated, false if no account has that id (or the update failed).
    public boolean updateUserRoleAndStatus(int userId, String newRole, String newStatus) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_ROLE_AND_STATUS)) {

            ps.setString(1, newRole);
            ps.setString(2, newStatus.toUpperCase());
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println("Could not update user: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    // Runs one of the fixed SELECT constants above (no parameters needed)
    // and turns every row into a User. Returns an empty list if the query fails.
    private List<User> queryUsers(String sql) {
        List<User> users = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }

        } catch (SQLException e) {
            System.out.println("Could not load users: " + e.getMessage());
        }

        return users;
    }

    // Builds a User from the current row of a SELECT that lists the six columns in this order.
    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getString("status"));
    }
}
