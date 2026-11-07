package com.aquatrack.model;

import java.util.List;

/**
 * The Daily Pool Usage & Guest Report for one date: one UnitUsage line per unit, plus the totals row.
 * Part 7 (PDF) reuses this same object, so the screen, the CSV and the PDF always show the same numbers.
 * date is text like "2026-10-31".
 */
public record DailyReport(String date, List<UnitUsage> rows) {

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int totalCheckIns() {
        return rows.stream().mapToInt(UnitUsage::checkIns).sum();
    }

    public int totalResidents() {
        return rows.stream().mapToInt(UnitUsage::residents).sum();
    }

    public int totalGuests() {
        return rows.stream().mapToInt(UnitUsage::guests).sum();
    }

    // Every swimmer of the day: residents plus guests.
    public int totalSwimmers() {
        return totalResidents() + totalGuests();
    }
}
