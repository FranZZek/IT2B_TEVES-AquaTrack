package com.aquatrack.model;

/**
 * One row of the residents table
 * (columns: resident_id, first_name, last_name, age_category, unit_number).
 * ageCategory is "Adult" or "Minor". unitNumber points to the unit the resident lives in.
 */
public record Resident(int residentId, String firstName, String lastName,
                       String ageCategory, int unitNumber) {

    public static final String ADULT = "Adult";
    public static final String MINOR = "Minor";

    // For a new resident that has no id yet; SQLite assigns it.
    public Resident(String firstName, String lastName, String ageCategory, int unitNumber) {
        this(0, firstName, lastName, ageCategory, unitNumber);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
