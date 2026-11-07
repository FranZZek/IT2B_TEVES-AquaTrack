package com.aquatrack.model;

/**
 * One row of the pool_logs table: one GROUP of a unit entering the pool
 * (columns: log_id, unit_number, resident_count, guest_count, time_in, time_out, status).
 * status is "Inside" or "Left". timeOut is null while the group is still Inside.
 * Times are text like "2026-10-31 14:05:09".
 */
public record PoolLog(int logId, int unitNumber, int residentCount, int guestCount,
                      String timeIn, String timeOut, String status) {

    public static final String INSIDE = "Inside";
    public static final String LEFT = "Left";

    // For a new check-in that has no id or times yet; the table defaults fill them in.
    public PoolLog(int unitNumber, int residentCount, int guestCount) {
        this(0, unitNumber, residentCount, guestCount, null, null, INSIDE);
    }

    // Everyone in this group: residents plus guests.
    public int headcount() {
        return residentCount + guestCount;
    }
}
