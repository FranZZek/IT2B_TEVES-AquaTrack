package com.aquatrack.model;

/**
 * One row of the activity_logs table (the audit trail)
 * (columns: audit_id, user_id, action_type, description, timestamp).
 * userId is the staff member who did the action. It is null if that staff account was deleted later:
 * the entry stays, and its description still names the person.
 */
public record ActivityLog(int auditId, Integer userId, String actionType,
                          String description, String timestamp) {

    // The action types written to the audit log. Always use these constants, never a typed-out string.
    // Each one is at most 18 characters so it fits the %-18s column of the audit table.
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String USER_APPROVED = "USER_APPROVED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String UNIT_ADDED = "UNIT_ADDED";
    public static final String UNIT_UPDATED = "UNIT_UPDATED";
    public static final String RESIDENT_ADDED = "RESIDENT_ADDED";
    public static final String RESIDENT_UPDATED = "RESIDENT_UPDATED";
    public static final String RESIDENT_REMOVED = "RESIDENT_REMOVED";
    public static final String POOL_ENTRY_LOGGED = "POOL_ENTRY_LOGGED";    // a group checked in
    public static final String POOL_EXIT_LOGGED = "POOL_EXIT_LOGGED";      // a group checked out
    public static final String POOL_ENTRY_DELETED = "POOL_ENTRY_DELETED";  // a mistaken entry was removed
    public static final String REPORT_EXPORTED = "REPORT_EXPORTED";        // a report was saved as CSV or PDF
    public static final String LOGS_CLEARED = "LOGS_CLEARED";              // the audit log was cleaned up
}
