package com.aquatrack.model;

/**
 * One row of the units table (columns: unit_number, owner_name, contact_number, occupancy_status).
 * occupancyStatus is "Occupied" or "Vacant".
 */
public record Unit(int unitNumber, String ownerName, String contactNumber, String occupancyStatus) {

    public static final String OCCUPIED = "Occupied";
    public static final String VACANT = "Vacant";

    // The same unit with a different occupancy status (records cannot be changed, so we build a new one).
    public Unit withOccupancy(String newStatus) {
        return new Unit(unitNumber, ownerName, contactNumber, newStatus);
    }
}
