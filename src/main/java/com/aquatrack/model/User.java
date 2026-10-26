package com.aquatrack.model;

/**
 * One row of the users table (columns: id, username, password_hash, full_name, role, status).
 * status is PENDING for a new account and becomes APPROVED when a Building Admin approves it.
 */
public record User(int userId, String username, String passwordHash,
                   String fullName, String role, String status) {

    // For a new account that has no id yet; SQLite assigns it and status defaults to PENDING.
    public User(String username, String passwordHash, String fullName, String role) {
        this(0, username, passwordHash, fullName, role, "PENDING");
    }
}
