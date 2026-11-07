package com.aquatrack.model;

/**
 * One line of a usage report: everything ONE unit did in the chosen period
 * (a day for the Daily Report, a month for the Billing Clerk's guest totals).
 * It is not a table. The pool_logs rows are added up per unit by PoolLogDao.
 * checkIns is how many groups of that unit entered the pool.
 */
public record UnitUsage(int unitNumber, String ownerName, int checkIns, int residents, int guests) {

    // Residents plus guests of this unit.
    public int total() {
        return residents + guests;
    }
}
