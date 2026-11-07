package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.PoolLogDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.model.PoolLog;
import com.aquatrack.model.Unit;
import com.aquatrack.model.UnitUsage;
import com.aquatrack.model.User;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Menu for the "Billing Clerk" role. This role is READ-ONLY: it can look at pool logs and guests,
 * work out what each unit would be billed for guests, and open the Daily Report, but it never
 * changes a record. It only talks to the user and calls the DAOs. There is no SQL in here.
 * No payments are handled: the "amount to bill" is for display only (see the proposal's scope).
 */
public class BillingClerkMenu {

    // Pesos charged per guest. Display only: AquaTrack records no payments. Change it here.
    private static final double GUEST_FEE = 50.00;

    // Most rows shown at once. A long history would otherwise scroll off the screen.
    private static final int MAX_ROWS = 50;

    private final User user;
    private final UnitDao unitDao;               // the ONE shared DAOs created in Main
    private final PoolLogDao poolLogDao;
    private final Scanner scanner;               // the ONE shared Scanner created in Main

    // The Daily Report screen is shared with the Building Admin.
    private final ReportMenu reportMenu;

    public BillingClerkMenu(User user, UnitDao unitDao, PoolLogDao poolLogDao,
                            ActivityLogDao activityLogDao, Scanner scanner) {
        this.user = user;
        this.unitDao = unitDao;
        this.poolLogDao = poolLogDao;
        this.scanner = scanner;
        this.reportMenu = new ReportMenu(user, poolLogDao, activityLogDao, scanner, Theme.BLUE);
    }

    // Shows the menu until the user logs out.
    public void start() {
        boolean loggedIn = true;

        while (loggedIn) {
            Theme.clearScreen();
            Theme.menu("BILLING CLERK MENU", Theme.BLUE,
                    List.of("Welcome, " + user.fullName(),
                            "Status : " + Theme.status(user.status()),
                            "Fee    : " + money(GUEST_FEE) + " per guest (display only)"),
                    "# POOL LOGS",
                    "View Logs With Guests",
                    "Search Unit History",
                    "Filter Logs by Date",
                    "# BILLING",
                    "Monthly Guest Total per Unit",
                    "# REPORTS",
                    "Daily Usage & Guest Report",
                    "# SESSION",
                    "Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 6);

            switch (choice) {
                case 1 -> { viewLogsWithGuests(); Theme.pause(scanner); }
                case 2 -> { searchUnitHistory(); Theme.pause(scanner); }
                case 3 -> { filterByDate(); Theme.pause(scanner); }
                case 4 -> { monthlyGuestTotals(); Theme.pause(scanner); }
                case 5 -> reportMenu.start();
                case 6 -> {
                    Theme.success("Logged out. See you, " + user.fullName() + "!");
                    loggedIn = false;
                }
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] The newest groups that brought guests, newest first.
    private void viewLogsWithGuests() {
        Theme.heading("Logs With Guests");

        List<PoolLog> logs = poolLogDao.getLogsWithGuests(MAX_ROWS);

        if (logs.isEmpty()) {
            Theme.info("No group has brought guests yet.");
            return;
        }
        PoolAttendantMenu.printLogTable(logs); // the same table the attendant uses
        printSummary("In this list", logs);

        if (logs.size() == MAX_ROWS) {
            Theme.info("Showing the newest " + MAX_ROWS + " entries. Use a filter to narrow it down.");
        }
    }

    // [2] Ask for a unit, then show its owner card and its whole pool history. Entering 0 cancels.
    private void searchUnitHistory() {
        Theme.heading("Unit Pool History");

        Unit unit = readExistingUnit("Unit number (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled.");
            return;
        }

        Theme.box("UNIT " + unit.unitNumber(), Theme.BLUE, List.of(
                "Owner   : " + unit.ownerName(),
                "Contact : " + unit.contactNumber(),
                "Status  : " + Theme.status(unit.occupancyStatus())));

        List<PoolLog> logs = poolLogDao.getLogsByUnit(unit.unitNumber()); // newest first

        if (logs.isEmpty()) {
            Theme.info("Unit " + unit.unitNumber() + " has never used the pool.");
            return;
        }

        // Only the newest rows are drawn, but the summary counts ALL of the unit's history.
        PoolAttendantMenu.printLogTable(logs.subList(0, Math.min(MAX_ROWS, logs.size())));
        printSummary("All time", logs);

        if (logs.size() > MAX_ROWS) {
            Theme.info("Showing the newest " + MAX_ROWS + " of " + logs.size() + " entries.");
        }
    }

    // [3] Ask for a date, then show every group that checked in that day. Entering 0 cancels.
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
        printSummary(date.toString(), logs);
    }

    // [4] Ask for a month, then list each unit's guests and the amount to bill. Entering 0 cancels.
    private void monthlyGuestTotals() {
        Theme.heading("Monthly Guest Total per Unit");

        YearMonth month = InputHelper.readMonth(scanner,
                "Month (yyyy-MM, e.g. " + YearMonth.now() + "; 0 to cancel): ");
        if (month == null) {
            Theme.info("Cancelled.");
            return;
        }

        List<UnitUsage> usage = poolLogDao.getMonthlyGuestUsage(month.toString());

        if (usage.isEmpty()) {
            Theme.warn("No guests were recorded in " + month + ".");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        int totalGuests = 0;
        for (UnitUsage unit : usage) {
            totalGuests += unit.guests();
            rows.add(new String[]{
                    String.valueOf(unit.unitNumber()),
                    unit.ownerName(),
                    String.valueOf(unit.guests()),
                    money(unit.guests() * GUEST_FEE)
            });
        }
        String[] totals = {
                Theme.bold("TOTAL"),
                "",
                Theme.bold(String.valueOf(totalGuests)),
                Theme.bold(money(totalGuests * GUEST_FEE))
        };
        Theme.table(new String[]{"Unit", "Owner", "Guests", "Amount to Bill"}, rows, totals, "unit");

        Theme.info("Month: " + month + "    Guest fee: " + money(GUEST_FEE) + " per guest.");
        System.out.println(Theme.gray("Display only: AquaTrack does not record payments."));
    }

    // ------------------------------------------------------------- helpers

    // Keeps asking for a unit number until the unit exists. Returns null if the user types 0.
    // A Vacant unit is fine here: it may still have an old pool history.
    private Unit readExistingUnit(String prompt) {
        while (true) {
            int unitNumber = InputHelper.readNonNegativeInt(scanner, prompt);

            if (unitNumber == 0) {
                return null;
            }

            Unit unit = unitDao.findByUnitNumber(unitNumber);
            if (unit != null) {
                return unit;
            }
            Theme.error("Unit " + unitNumber + " does not exist.");
        }
    }

    // One gray line under a log table: how many groups, residents, guests and the guest fee for them.
    private static void printSummary(String label, List<PoolLog> logs) {
        int residents = 0;
        int guests = 0;
        for (PoolLog log : logs) {
            residents += log.residentCount();
            guests += log.guestCount();
        }
        System.out.println(Theme.gray(label + ": " + logs.size() + " group(s), " + residents
                + " resident(s), " + guests + " guest(s)  ->  guest fees " + money(guests * GUEST_FEE)));
    }

    // 1250.0 -> "PHP 1,250.00"
    private static String money(double amount) {
        return String.format("PHP %,.2f", amount);
    }
}
