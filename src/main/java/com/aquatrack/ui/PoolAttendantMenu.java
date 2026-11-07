package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.PoolLogDao;
import com.aquatrack.dao.ResidentDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.model.ActivityLog;
import com.aquatrack.model.PoolLog;
import com.aquatrack.model.Resident;
import com.aquatrack.model.Unit;
import com.aquatrack.model.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Menu for the "Pool Attendant" role: check groups in and out, watch the live roster and capacity,
 * verify a unit's residents, and delete an entry that was typed by mistake.
 * A "group" is residents of ONE unit plus the guests they brought (guests are billed, residents swim free).
 * It only talks to the user and calls the DAOs. There is no SQL in here.
 */
public class PoolAttendantMenu {

    // The pool's limit (Part of the proposal: show a warning when it is exceeded).
    // Change it here and every capacity bar and warning follows.
    public static final int MAX_CAPACITY = 30;

    // Most guests one group may bring. A typo such as 500 is rejected by the input check.
    private static final int MAX_GUESTS_PER_GROUP = 10;

    // Most rows shown when choosing an entry to delete.
    private static final int RECENT_ROWS = 10;

    private final User user;
    private final UnitDao unitDao;               // the ONE shared DAOs created in Main
    private final ResidentDao residentDao;
    private final PoolLogDao poolLogDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;               // the ONE shared Scanner created in Main

    public PoolAttendantMenu(User user, UnitDao unitDao, ResidentDao residentDao, PoolLogDao poolLogDao,
                             ActivityLogDao activityLogDao, Scanner scanner) {
        this.user = user;
        this.unitDao = unitDao;
        this.residentDao = residentDao;
        this.poolLogDao = poolLogDao;
        this.activityLogDao = activityLogDao;
        this.scanner = scanner;
    }

    // Shows the menu until the user logs out. The capacity bar in the header is redrawn every time.
    public void start() {
        boolean loggedIn = true;

        while (loggedIn) {
            Theme.clearScreen();
            Theme.menu("POOL ATTENDANT MENU", Theme.CYAN,
                    List.of("Welcome, " + user.fullName(),
                            "Status : " + Theme.status(user.status()),
                            "Pool   : " + Theme.capacityBar(poolLogDao.getCurrentHeadcount(), MAX_CAPACITY)),
                    "# POOL ACCESS",
                    "Check In a Group",
                    "Check Out a Group",
                    "# LIVE VIEW",
                    "Live Roster & Capacity",
                    "Verify Unit & Residents",
                    "# CORRECTIONS",
                    "Delete Mistaken Entry",
                    "# SESSION",
                    "Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 6);

            switch (choice) {
                case 1 -> { checkIn(); Theme.pause(scanner); }
                case 2 -> { checkOut(); Theme.pause(scanner); }
                case 3 -> { printRoster(poolLogDao); Theme.pause(scanner); }
                case 4 -> { verifyUnit(); Theme.pause(scanner); }
                case 5 -> { deleteEntry(); Theme.pause(scanner); }
                case 6 -> {
                    Theme.success("Logged out. See you, " + user.fullName() + "!");
                    loggedIn = false;
                }
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] Ask for the unit, check who may swim, ask the numbers, warn about capacity, then save.
    // Entering 0 at any number cancels without saving anything.
    private void checkIn() {
        Theme.heading("Check In a Group");

        Unit unit = readExistingUnit("Unit number (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled. Nobody was checked in.");
            return;
        }

        List<Resident> residents = residentDao.getResidentsByUnit(unit.unitNumber());
        showUnitCard(unit, residents);

        // Only registered residents swim free, and nobody can be inside twice.
        int inside = poolLogDao.countResidentsInside(unit.unitNumber());
        int available = residents.size() - inside;

        if (residents.isEmpty()) {
            Theme.error("Unit " + unit.unitNumber() + " has no registered residents, so it cannot check in.");
            return;
        }
        if (available <= 0) {
            Theme.warn("All " + residents.size() + " registered resident(s) of Unit "
                    + unit.unitNumber() + " are already inside.");
            return;
        }

        int residentCount = InputHelper.readInt(scanner,
                "Residents swimming (1-" + available + ", 0 to cancel): ", 0, available);
        if (residentCount == 0) {
            Theme.info("Cancelled. Nobody was checked in.");
            return;
        }

        int guestCount = InputHelper.readInt(scanner,
                "Outside guests (0-" + MAX_GUESTS_PER_GROUP + "): ", 0, MAX_GUESTS_PER_GROUP);

        // The proposal asks for a WARNING, not a lock: the attendant decides.
        int headcount = poolLogDao.getCurrentHeadcount();
        int after = headcount + residentCount + guestCount;
        if (after > MAX_CAPACITY) {
            Theme.warn("CAPACITY WARNING: " + headcount + " inside now, this group would make it "
                    + after + " (limit " + MAX_CAPACITY + ").");
            if (!InputHelper.readYesNo(scanner, "Let this group in anyway?")) {
                Theme.info("Cancelled. Nobody was checked in.");
                return;
            }
        }

        if (poolLogDao.insert(new PoolLog(unit.unitNumber(), residentCount, guestCount))) {
            Theme.success("Unit " + unit.unitNumber() + " checked in: " + residentCount
                    + " resident(s) + " + guestCount + " guest(s).");
            if (guestCount > 0) {
                Theme.info(guestCount + " guest(s) will show up on the Billing Clerk's guest list.");
            }
            System.out.println("Pool now: " + Theme.capacityBar(poolLogDao.getCurrentHeadcount(), MAX_CAPACITY));

            activityLogDao.logAction(user.userId(), ActivityLog.POOL_ENTRY_LOGGED,
                    "Attendant [" + user.userId() + "] checked in Unit " + unit.unitNumber()
                            + " (" + residentCount + " res, " + guestCount + " guest)");
        } else {
            Theme.error("The check-in was NOT saved.");
        }
    }

    // [2] Show the groups that are inside, ask which log left, then stamp the check-out time.
    private void checkOut() {
        Theme.heading("Check Out a Group");

        List<PoolLog> inside = poolLogDao.getInsideLogs();

        if (inside.isEmpty()) {
            Theme.info("Nobody is inside the pool right now.");
            return;
        }
        printInsideTable(inside);

        while (true) {
            int logId = InputHelper.readNonNegativeInt(scanner, "Log ID that is leaving (0 to cancel): ");

            if (logId == 0) {
                Theme.info("Cancelled. Nobody was checked out.");
                return;
            }

            PoolLog found = null;
            for (PoolLog log : inside) {
                if (log.logId() == logId) {
                    found = log;
                    break;
                }
            }
            if (found == null) {
                Theme.error("Log " + logId + " is not in the list above. Please try again.");
                continue;
            }

            if (poolLogDao.checkOut(logId)) {
                Theme.success("Unit " + found.unitNumber() + " checked out after "
                        + insideFor(found.timeIn()) + ".");
                System.out.println("Pool now: " + Theme.capacityBar(poolLogDao.getCurrentHeadcount(), MAX_CAPACITY));

                activityLogDao.logAction(user.userId(), ActivityLog.POOL_EXIT_LOGGED,
                        "Attendant [" + user.userId() + "] checked out Unit " + found.unitNumber()
                                + " (log #" + logId + ")");
            } else {
                Theme.error("The check-out was NOT saved.");
            }
            return;
        }
    }

    // [4] The unit card plus the list of its registered residents: the quick "is this person legit?" check.
    private void verifyUnit() {
        Theme.heading("Verify Unit & Residents");

        Unit unit = readExistingUnit("Unit number (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled.");
            return;
        }

        List<Resident> residents = residentDao.getResidentsByUnit(unit.unitNumber());
        showUnitCard(unit, residents);

        if (residents.isEmpty()) {
            Theme.warn("No registered residents. Anyone from this unit is NOT a verified resident.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        for (Resident resident : residents) {
            rows.add(new String[]{
                    String.valueOf(resident.residentId()),
                    resident.fullName(),
                    resident.ageCategory()
            });
        }
        Theme.table(new String[]{"ID", "Name", "Category"}, rows, "verified resident");
    }

    // [5] Show the newest entries, ask for a log ID, confirm, then delete it. Entering 0 cancels.
    private void deleteEntry() {
        Theme.heading("Delete Mistaken Entry");

        List<PoolLog> recent = poolLogDao.getRecentLogs(RECENT_ROWS);

        if (recent.isEmpty()) {
            Theme.info("There are no pool entries yet.");
            return;
        }
        printLogTable(recent);
        Theme.info("Only use this for an entry typed by mistake. A normal exit is a check-out.");

        PoolLog log;
        while (true) {
            int logId = InputHelper.readNonNegativeInt(scanner, "Log ID to delete (0 to cancel): ");

            if (logId == 0) {
                Theme.info("Cancelled. No changes were made.");
                return;
            }
            log = poolLogDao.findById(logId);
            if (log != null) {
                break;
            }
            Theme.error("Log " + logId + " does not exist.");
        }

        Theme.warn("This permanently deletes log " + log.logId() + " (Unit " + log.unitNumber()
                + ", " + log.residentCount() + " resident(s), " + log.guestCount() + " guest(s)).");
        if (!InputHelper.readYesNo(scanner, "Are you sure?")) {
            Theme.info("Deletion cancelled. No changes were made.");
            return;
        }

        if (poolLogDao.deleteLog(log.logId())) {
            Theme.success("Log " + log.logId() + " was deleted.");
            activityLogDao.logAction(user.userId(), ActivityLog.POOL_ENTRY_DELETED,
                    "Attendant [" + user.userId() + "] deleted pool entry #" + log.logId()
                            + " of Unit " + log.unitNumber());
        } else {
            Theme.error("The entry was NOT deleted. No changes were made.");
        }
    }

    // ------------------------------------------- shared with the other roles

    // The Live Roster dashboard: the capacity bar, everyone inside, and an over-capacity warning.
    // Static, so the Building Admin's pool screen shows exactly the same dashboard.
    public static void printRoster(PoolLogDao poolLogDao) {
        Theme.heading("Live Roster & Capacity");

        int headcount = poolLogDao.getCurrentHeadcount();
        System.out.println("Pool : " + Theme.capacityBar(headcount, MAX_CAPACITY));

        if (headcount > MAX_CAPACITY) {
            Theme.warn("OVER CAPACITY by " + (headcount - MAX_CAPACITY) + " swimmer(s)!");
        }

        List<PoolLog> inside = poolLogDao.getInsideLogs();
        if (inside.isEmpty()) {
            Theme.info("Nobody is inside the pool right now.");
            return;
        }
        printInsideTable(inside);

        int residents = 0;
        int guests = 0;
        for (PoolLog log : inside) {
            residents += log.residentCount();
            guests += log.guestCount();
        }
        System.out.println(Theme.gray("Inside now: " + residents + " resident(s) + " + guests + " guest(s)."));
    }

    // A table of pool logs (Inside or Left). The Billing Clerk and the Admin use the same one.
    public static void printLogTable(List<PoolLog> logs) {
        List<String[]> rows = new ArrayList<>();
        for (PoolLog log : logs) {
            rows.add(new String[]{
                    String.valueOf(log.logId()),
                    String.valueOf(log.unitNumber()),
                    String.valueOf(log.residentCount()),
                    String.valueOf(log.guestCount()),
                    log.timeIn(),
                    log.timeOut() == null ? "-" : log.timeOut(),
                    Theme.status(log.status())
            });
        }
        Theme.table(new String[]{"Log", "Unit", "Residents", "Guests", "Time In", "Time Out", "Status"},
                rows, "log");
    }

    // ------------------------------------------------------------- helpers

    // The groups that are inside, with how long each has been in the water.
    private static void printInsideTable(List<PoolLog> inside) {
        List<String[]> rows = new ArrayList<>();
        for (PoolLog log : inside) {
            rows.add(new String[]{
                    String.valueOf(log.logId()),
                    String.valueOf(log.unitNumber()),
                    String.valueOf(log.residentCount()),
                    String.valueOf(log.guestCount()),
                    log.timeIn(),
                    insideFor(log.timeIn())
            });
        }
        Theme.table(new String[]{"Log", "Unit", "Residents", "Guests", "Time In", "Inside For"},
                rows, "group");
    }

    // "2026-10-31 14:05:09" -> "25 min" or "1 h 5 min". Shows "-" if the time cannot be read.
    private static String insideFor(String timeIn) {
        try {
            LocalDateTime start = LocalDateTime.parse(timeIn.replace(' ', 'T'));
            long minutes = Math.max(0, Duration.between(start, LocalDateTime.now()).toMinutes());

            if (minutes < 60) {
                return minutes + " min";
            }
            return (minutes / 60) + " h " + (minutes % 60) + " min";
        } catch (DateTimeParseException e) {
            return "-";
        }
    }

    // A unit as a framed card: owner, status, who is registered and how many are inside right now.
    private void showUnitCard(Unit unit, List<Resident> residents) {
        List<String> lines = new ArrayList<>();
        lines.add("Owner   : " + unit.ownerName());
        lines.add("Status  : " + Theme.status(unit.occupancyStatus()));
        lines.add("Inside  : " + poolLogDao.countResidentsInside(unit.unitNumber())
                + " of " + residents.size() + " registered resident(s)");
        for (Resident resident : residents) {
            lines.add("  - " + resident.fullName() + " (" + resident.ageCategory() + ")");
        }
        Theme.box("UNIT " + unit.unitNumber(), Theme.CYAN, lines);
    }

    // Keeps asking for a unit number until the unit exists. Returns null if the user types 0.
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
}
