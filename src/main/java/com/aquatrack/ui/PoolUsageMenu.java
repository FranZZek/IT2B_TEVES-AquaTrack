package com.aquatrack.ui;

import com.aquatrack.dao.PoolLogDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.model.PoolLog;
import com.aquatrack.model.Unit;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * "Pool Usage" screen of the Building Admin ("view all pool usage data" in the proposal).
 * Everything here is read-only: the live roster, the newest logs, one unit's history and one day's logs.
 * It only talks to the user and calls the DAOs. There is no SQL in here.
 */
public class PoolUsageMenu {

    // Most rows shown at once. A long history would otherwise scroll off the screen.
    private static final int MAX_ROWS = 50;

    private final UnitDao unitDao;               // the ONE shared DAOs created in Main
    private final PoolLogDao poolLogDao;
    private final Scanner scanner;               // the ONE shared Scanner created in Main

    public PoolUsageMenu(UnitDao unitDao, PoolLogDao poolLogDao, Scanner scanner) {
        this.unitDao = unitDao;
        this.poolLogDao = poolLogDao;
        this.scanner = scanner;
    }

    // Shows the menu until the admin goes back.
    public void start() {
        boolean open = true;

        while (open) {
            Theme.clearScreen();
            Theme.menu("POOL USAGE", Theme.MAGENTA,
                    List.of("Pool : " + Theme.capacityBar(poolLogDao.getCurrentHeadcount(),
                            PoolAttendantMenu.MAX_CAPACITY)),
                    "Live Roster & Capacity",
                    "Recent Pool Logs",
                    "Search Unit History",
                    "Filter Logs by Date",
                    "Back");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 5);

            switch (choice) {
                case 1 -> { PoolAttendantMenu.printRoster(poolLogDao); Theme.pause(scanner); }
                case 2 -> { recentLogs(); Theme.pause(scanner); }
                case 3 -> { searchUnitHistory(); Theme.pause(scanner); }
                case 4 -> { filterByDate(); Theme.pause(scanner); }
                case 5 -> open = false;
            }
        }
    }

    // The newest logs, Inside or Left.
    private void recentLogs() {
        Theme.heading("Recent Pool Logs");

        List<PoolLog> logs = poolLogDao.getRecentLogs(MAX_ROWS);

        if (logs.isEmpty()) {
            Theme.info("There are no pool entries yet.");
            return;
        }
        PoolAttendantMenu.printLogTable(logs);

        if (logs.size() == MAX_ROWS) {
            Theme.info("Showing the newest " + MAX_ROWS + " entries. Use a filter to narrow it down.");
        }
    }

    // Ask for a unit, then show its whole pool history. Entering 0 cancels.
    private void searchUnitHistory() {
        Theme.heading("Unit Pool History");

        Unit unit = null;
        while (unit == null) {
            int unitNumber = InputHelper.readNonNegativeInt(scanner, "Unit number (0 to cancel): ");

            if (unitNumber == 0) {
                Theme.info("Cancelled.");
                return;
            }
            unit = unitDao.findByUnitNumber(unitNumber);
            if (unit == null) {
                Theme.error("Unit " + unitNumber + " does not exist.");
            }
        }

        Theme.box("UNIT " + unit.unitNumber(), Theme.MAGENTA, List.of(
                "Owner   : " + unit.ownerName(),
                "Contact : " + unit.contactNumber(),
                "Status  : " + Theme.status(unit.occupancyStatus())));

        List<PoolLog> logs = poolLogDao.getLogsByUnit(unit.unitNumber()); // newest first

        if (logs.isEmpty()) {
            Theme.info("Unit " + unit.unitNumber() + " has never used the pool.");
            return;
        }
        PoolAttendantMenu.printLogTable(logs.subList(0, Math.min(MAX_ROWS, logs.size())));

        if (logs.size() > MAX_ROWS) {
            Theme.info("Showing the newest " + MAX_ROWS + " of " + logs.size() + " entries.");
        }
    }

    // Ask for a date, then show every group that checked in that day. Entering 0 cancels.
    private void filterByDate() {
        Theme.heading("Filter Logs by Date");

        LocalDate date = InputHelper.readDate(scanner,
                "Date (yyyy-MM-dd, e.g. " + LocalDate.now() + "; 0 to cancel): ");
        if (date == null) {
            Theme.info("Cancelled.");
            return;
        }

        List<PoolLog> logs = poolLogDao.getLogsByDate(date.toString());

        if (logs.isEmpty()) {
            Theme.warn("No pool entries were found for " + date + ".");
            return;
        }
        PoolAttendantMenu.printLogTable(logs);
    }
}
