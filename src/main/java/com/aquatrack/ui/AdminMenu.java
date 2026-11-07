package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.PoolLogDao;
import com.aquatrack.dao.ResidentDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.dao.UserDao;
import com.aquatrack.model.ActivityLog;
import com.aquatrack.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Menu for the "Building Admin" role.
 * The admin can manage user accounts (list, approve, update, delete) and open the
 * "Manage Units", "Manage Residents", "Pool Usage", "System Audit Logs" and "Daily Report" screens.
 * Every key action is written to the audit log with activityLogDao.logAction().
 * It only talks to the user and calls the DAOs. There is no SQL in here.
 */
public class AdminMenu {

    // The only values the database accepts (see the CHECK constraints in DatabaseInitializer).
    private static final String BUILDING_ADMIN = "Building Admin";
    private static final String[] VALID_ROLES = {BUILDING_ADMIN, "Pool Attendant", "Billing Clerk"};
    private static final String[] VALID_STATUSES = {"APPROVED", "PENDING"};

    private final User user;
    private final UserDao userDao; // the ONE shared DAOs created in Main
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner; // the ONE shared Scanner created in Main

    // The sub-screens reached from this menu. They get the same shared DAOs and Scanner.
    private final UnitMenu unitMenu;
    private final ResidentMenu residentMenu;
    private final PoolUsageMenu poolUsageMenu;
    private final ReportMenu reportMenu;

    // The status shown in the menu header. The User object is a snapshot taken at login,
    // so this is updated if the admin approves their own account during this session.
    private String currentStatus;

    public AdminMenu(User user, UserDao userDao, UnitDao unitDao, ResidentDao residentDao,
                     PoolLogDao poolLogDao, ActivityLogDao activityLogDao, Scanner scanner) {
        this.user = user;
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
        this.scanner = scanner;
        this.unitMenu = new UnitMenu(user, unitDao, residentDao, activityLogDao, scanner);
        this.residentMenu = new ResidentMenu(user, residentDao, unitDao, activityLogDao, scanner);
        this.poolUsageMenu = new PoolUsageMenu(unitDao, poolLogDao, scanner);
        this.reportMenu = new ReportMenu(user, poolLogDao, activityLogDao, scanner, Theme.MAGENTA);
        this.currentStatus = user.status();
    }

    // Shows the menu until the user logs out.
    public void start() {
        boolean loggedIn = true;

        while (loggedIn) {
            Theme.clearScreen();
            Theme.menu("BUILDING ADMIN MENU", Theme.MAGENTA,
                    List.of("Welcome, " + user.fullName(), "Status : " + Theme.status(currentStatus)),
                    "# USER ACCOUNTS",
                    "View All System Users",
                    "View Pending Registrations",
                    "Approve User Account",
                    "Update User Role & Status",
                    "Delete Staff Account",
                    "# CONDO RECORDS",
                    "Manage Units",
                    "Manage Residents",
                    "# POOL",
                    "Pool Usage & Live Roster",
                    "# MONITORING",
                    "View System Audit Logs",
                    "# REPORTS",
                    "Daily Usage & Guest Report",
                    "# SESSION",
                    "Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 11);

            switch (choice) {
                case 1 -> { viewAllUsers(); Theme.pause(scanner); }
                case 2 -> { viewPendingUsers(); Theme.pause(scanner); }
                case 3 -> { approveUser(); Theme.pause(scanner); }
                case 4 -> { updateUser(); Theme.pause(scanner); }
                case 5 -> { deleteUser(); Theme.pause(scanner); }
                case 6 -> unitMenu.start();
                case 7 -> residentMenu.start();
                case 8 -> poolUsageMenu.start();
                case 9 -> auditLogScreen();
                case 10 -> reportMenu.start();
                case 11 -> {
                    Theme.success("Logged out. See you, " + user.fullName() + "!");
                    loggedIn = false;
                }
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] Every account, whatever its role or status.
    private void viewAllUsers() {
        Theme.heading("All System Users");

        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            Theme.info("There are no registered accounts.");
            return;
        }
        printUserTable(users);
    }

    // [2] Only the accounts still waiting for approval, across all roles.
    private void viewPendingUsers() {
        Theme.heading("Pending Registrations");

        List<User> pending = userDao.getPendingUsers();

        if (pending.isEmpty()) {
            Theme.info("There are no pending registrations.");
            return;
        }
        printUserTable(pending);
    }

    // [3] Show the pending accounts, ask for a User ID, check it is really in that list,
    // then approve it. Entering 0 cancels without changing anything.
    private void approveUser() {
        Theme.heading("Approve User Account");

        List<User> pending = userDao.getPendingUsers();

        if (pending.isEmpty()) {
            Theme.info("There are no pending accounts to approve.");
            return;
        }
        printUserTable(pending);

        while (true) {
            int targetId = InputHelper.readNonNegativeInt(scanner,
                    "Enter the User ID to approve (0 to cancel): ");

            if (targetId == 0) {
                Theme.info("Approval cancelled. No changes were made.");
                return;
            }

            // Only the very first Building Admin may approve their own account. After that,
            // another admin must do it, or a self-registered "admin" could approve themselves.
            if (targetId == user.userId() && Authentication.hasApprovedAdmin(userDao)) {
                Theme.warn("Another Building Admin must approve your account.");
                continue;
            }

            // Only IDs that are in the pending list can be approved.
            Optional<User> target = pending.stream()
                    .filter(u -> u.userId() == targetId)
                    .findFirst();

            if (target.isEmpty()) {
                Theme.error("User ID " + targetId + " is not in the pending list. Please try again.");
                continue;
            }

            if (userDao.approveUser(targetId)) {
                User approved = target.get();
                Theme.success("Success! " + approved.username() + " (User ID " + targetId
                        + ", " + approved.role() + ") is now APPROVED.");
                activityLogDao.logAction(user.userId(), ActivityLog.USER_APPROVED,
                        "Admin [" + user.userId() + "] approved account for User ID " + targetId
                                + " (" + approved.username() + ")");

                if (targetId == user.userId()) {
                    currentStatus = "APPROVED"; // the admin just approved their own account
                }
            } else {
                Theme.error("The account was NOT approved. No changes were made.");
            }
            return;
        }
    }

    // [4] Show every user, ask for a User ID, then ask for a new role and status.
    // Entering 0 at any step cancels without changing anything.
    // The admin who is logged in cannot change their own account.
    private void updateUser() {
        Theme.heading("Update User Role & Status");

        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            Theme.info("There are no registered accounts.");
            return;
        }
        printUserTable(users);

        User target;
        while (true) {
            int targetId = InputHelper.readNonNegativeInt(scanner,
                    "Enter the User ID to update (0 to cancel): ");

            if (targetId == 0) {
                cancelUpdate();
                return;
            }

            // Self-edit restriction: the active admin account cannot be modified.
            if (targetId == user.userId()) {
                Theme.warn("Modifying the currently active admin account is restricted.");
                continue;
            }

            Optional<User> found = users.stream()
                    .filter(u -> u.userId() == targetId)
                    .findFirst();

            if (found.isEmpty()) {
                Theme.error("User ID " + targetId + " does not exist. Please try again.");
                continue;
            }
            target = found.get();
            break;
        }

        Theme.info("Updating " + target.username() + " (currently " + target.role()
                + ", " + target.status() + ").");

        String newRole = readValidRole();
        if (newRole == null) {
            cancelUpdate();
            return;
        }

        String newStatus = readValidStatus();
        if (newStatus == null) {
            cancelUpdate();
            return;
        }

        if (userDao.updateUserRoleAndStatus(target.userId(), newRole, newStatus)) {
            Theme.success("Success! " + target.username() + " (User ID " + target.userId()
                    + ") is now " + newRole + " / " + newStatus.toUpperCase() + ".");
            activityLogDao.logAction(user.userId(), ActivityLog.USER_UPDATED,
                    "Admin [" + user.userId() + "] changed role of User " + target.userId()
                            + " to " + newRole + " (status " + newStatus.toUpperCase() + ")");
        } else {
            Theme.error("The account was NOT updated. No changes were made.");
        }
    }

    private static void cancelUpdate() {
        Theme.info("Update cancelled. No changes were made.");
    }

    // [5] Show every user, ask for a User ID, check the safety rules, confirm, then delete.
    // Entering 0 cancels. The deleted account's old audit-log entries are kept (see activity_logs).
    private void deleteUser() {
        Theme.heading("Delete Staff Account");

        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            Theme.info("There are no registered accounts.");
            return;
        }
        printUserTable(users);

        User target;
        while (true) {
            int targetId = InputHelper.readNonNegativeInt(scanner,
                    "Enter the User ID to delete (0 to cancel): ");

            if (targetId == 0) {
                Theme.info("Deletion cancelled. No changes were made.");
                return;
            }

            // Safety rule 1: you cannot delete the account you are logged in with.
            if (targetId == user.userId()) {
                Theme.warn("You cannot delete the account you are logged in with.");
                continue;
            }

            Optional<User> found = users.stream()
                    .filter(u -> u.userId() == targetId)
                    .findFirst();

            if (found.isEmpty()) {
                Theme.error("User ID " + targetId + " does not exist. Please try again.");
                continue;
            }
            target = found.get();
            break;
        }

        // Safety rule 2: the system must always keep at least one Building Admin.
        // This reads the real count from the database, so it still holds if roles were changed.
        if (target.role().equals(BUILDING_ADMIN) && userDao.countByRole(BUILDING_ADMIN) <= 1) {
            Theme.warn("The last Building Admin account cannot be deleted.");
            return;
        }

        Theme.warn("This permanently deletes " + target.username() + " (" + target.role()
                + "). Their audit-log history is kept.");
        if (!InputHelper.readYesNo(scanner, "Are you sure?")) {
            Theme.info("Deletion cancelled. No changes were made.");
            return;
        }

        if (userDao.deleteUser(target.userId())) {
            Theme.success(target.username() + " (User ID " + target.userId() + ") was deleted.");
            activityLogDao.logAction(user.userId(), ActivityLog.USER_DELETED,
                    "Admin [" + user.userId() + "] deleted staff account of User ID " + target.userId()
                            + " (" + target.username() + ")");
        } else {
            Theme.error("The account was NOT deleted. No changes were made.");
        }
    }

    // ------------------------------------------------- system audit logs

    // [9] The "View System Audit Logs" screen: view all, filter by user, or clean up old entries.
    private void auditLogScreen() {
        boolean open = true;

        while (open) {
            Theme.clearScreen();
            Theme.menu("SYSTEM AUDIT LOGS", Theme.MAGENTA,
                    List.of("Entries recorded : " + activityLogDao.countLogs()),
                    "View All System Logs",
                    "Filter Logs by User ID",
                    "Clear Historical Logs",
                    "Back");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 4);

            switch (choice) {
                case 1 -> { viewAllLogs(); Theme.pause(scanner); }
                case 2 -> { filterLogsByUser(); Theme.pause(scanner); }
                case 3 -> { clearLogs(); Theme.pause(scanner); }
                case 4 -> open = false;
            }
        }
    }

    // Every recorded action, newest first.
    private void viewAllLogs() {
        Theme.heading("All System Logs");

        List<ActivityLog> logs = activityLogDao.getAllLogs();

        if (logs.isEmpty()) {
            Theme.info("The audit log is empty.");
            return;
        }
        printLogTable(logs);
    }

    // Show the users, ask for a User ID, then list only that user's actions. Entering 0 cancels.
    private void filterLogsByUser() {
        Theme.heading("Filter Logs by User ID");

        List<User> users = userDao.getAllUsers();
        printUserTable(users);

        Optional<User> target;
        while (true) {
            int targetId = InputHelper.readNonNegativeInt(scanner, "Enter the User ID to filter by (0 to cancel): ");

            if (targetId == 0) {
                Theme.info("Cancelled.");
                return;
            }
            target = users.stream().filter(u -> u.userId() == targetId).findFirst();

            if (target.isPresent()) {
                break;
            }
            Theme.error("User ID " + targetId + " does not exist. Entries of deleted accounts cannot be filtered.");
        }

        User chosen = target.get();
        List<ActivityLog> logs = activityLogDao.getLogsByUserId(chosen.userId());

        Theme.info("Actions of " + chosen.username() + " (User ID " + chosen.userId() + ", " + chosen.role() + ")");
        if (logs.isEmpty()) {
            Theme.warn("This user has no recorded actions yet.");
            return;
        }
        printLogTable(logs);
    }

    // Two safe ways to clean up: only the OLD entries (one yes/no), or ALL of them (type the word CLEAR).
    // The clean-up itself is written to the log afterwards, so it can never happen silently.
    private void clearLogs() {
        Theme.heading("Clear Historical Logs");

        int total = activityLogDao.countLogs();
        if (total == 0) {
            Theme.info("The audit log is already empty.");
            return;
        }

        Theme.warn("Cleared logs cannot be brought back. The audit log holds " + total + " entries.");
        Theme.menu("CLEAR OPTIONS", Theme.RED, List.of(),
                "Delete Logs Older Than N Days",
                "Delete ALL Logs",
                "Cancel");

        int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 3);

        switch (choice) {
            case 1 -> {
                int days = InputHelper.readInt(scanner,
                        "Delete logs older than how many days? (1-3650, 0 to cancel): ", 0, 3650);
                if (days == 0) {
                    Theme.info("Cancelled. No logs were deleted.");
                    return;
                }
                if (!InputHelper.readYesNo(scanner, "Delete every log older than " + days + " day(s)?")) {
                    Theme.info("Cancelled. No logs were deleted.");
                    return;
                }
                int deleted = activityLogDao.clearLogsOlderThan(days);
                finishClear(deleted, "older than " + days + " day(s)");
            }
            case 2 -> {
                Theme.warn("This deletes ALL " + total + " entries.");
                System.out.print("Type CLEAR to confirm (anything else cancels): ");
                if (!scanner.nextLine().trim().equals("CLEAR")) {
                    Theme.info("Cancelled. No logs were deleted.");
                    return;
                }
                int deleted = activityLogDao.clearAllLogs();
                finishClear(deleted, "ALL");
            }
            case 3 -> Theme.info("Cancelled. No logs were deleted.");
        }
    }

    // Tells the admin how many entries went, and records the clean-up as a new audit entry.
    private void finishClear(int deleted, String what) {
        if (deleted == 0) {
            Theme.info("No logs were deleted.");
            return;
        }
        Theme.success(deleted + " log entr" + (deleted == 1 ? "y was" : "ies were") + " deleted.");
        activityLogDao.logAction(user.userId(), ActivityLog.LOGS_CLEARED,
                "Admin [" + user.userId() + "] cleared " + deleted + " log(s): " + what);
    }

    // ---------------------------------------------------- audit log table

    // The audit table columns, exactly as the deliverable asks: ID | User ID | Action Type | Description | Timestamp.
    private static final String LOG_ROW_FORMAT = "%-5s | %-8s | %-18s | %-35s | %-20s";
    private static final int LOG_DESCRIPTION_WIDTH = 35;
    private static final int LOG_ACTION_START = 5 + 3 + 8 + 3;   // where the Action Type column begins in a row
    private static final int LOG_ACTION_WIDTH = 18;
    private static final int LOG_PAGE_SIZE = 15;

    // Prints the logs 15 at a time. Enter shows the next page, q stops. Nothing is skipped silently.
    private void printLogTable(List<ActivityLog> logs) {
        int shown = 0;

        while (shown < logs.size()) {
            printLogHeader();

            int end = Math.min(logs.size(), shown + LOG_PAGE_SIZE);
            for (ActivityLog log : logs.subList(shown, end)) {
                printLogRow(log);
            }
            System.out.println(logRule());
            shown = end;

            if (shown < logs.size()) {
                System.out.print(Theme.gray(shown + " of " + logs.size()
                        + " shown. Press Enter for the next page, or q to stop: "));
                if (scanner.nextLine().trim().equalsIgnoreCase("q")) {
                    break;
                }
            }
        }
        System.out.println(Theme.gray("Showing " + shown + " of " + logs.size() + " log entr"
                + (logs.size() == 1 ? "y" : "ies") + ", newest first."));
    }

    private static void printLogHeader() {
        System.out.println(logRule());
        System.out.println(Theme.paint("1;" + Theme.CYAN,
                String.format(LOG_ROW_FORMAT, "ID", "User ID", "Action Type", "Description", "Timestamp")));
        System.out.println(logRule());
    }

    // One audit entry. A description longer than its column continues on the next line(s) so no text is lost.
    // The action type is colored AFTER the row is padded, so the colors never break the alignment.
    private static void printLogRow(ActivityLog log) {
        List<String> description = wrap(log.description(), LOG_DESCRIPTION_WIDTH);
        String userId = log.userId() == null ? "-" : String.valueOf(log.userId()); // "-" = account was deleted

        String first = String.format(LOG_ROW_FORMAT, log.auditId(), userId, log.actionType(),
                description.get(0), log.timestamp());
        int actionEnd = LOG_ACTION_START + LOG_ACTION_WIDTH;
        System.out.println(first.substring(0, LOG_ACTION_START)
                + Theme.paint(actionColor(log.actionType()), first.substring(LOG_ACTION_START, actionEnd))
                + first.substring(actionEnd));

        for (int i = 1; i < description.size(); i++) {
            System.out.println(String.format(LOG_ROW_FORMAT, "", "", "", description.get(i), ""));
        }
    }

    // Green = something was created, yellow = changed, red = removed, gray = sessions, cyan = the rest.
    private static String actionColor(String actionType) {
        return switch (actionType) {
            case ActivityLog.LOGIN, ActivityLog.LOGOUT -> Theme.GRAY;
            case ActivityLog.USER_APPROVED, ActivityLog.UNIT_ADDED, ActivityLog.RESIDENT_ADDED,
                 ActivityLog.POOL_ENTRY_LOGGED -> Theme.GREEN;
            case ActivityLog.USER_UPDATED, ActivityLog.UNIT_UPDATED, ActivityLog.RESIDENT_UPDATED,
                 ActivityLog.POOL_EXIT_LOGGED -> Theme.YELLOW;
            case ActivityLog.USER_DELETED, ActivityLog.RESIDENT_REMOVED, ActivityLog.POOL_ENTRY_DELETED,
                 ActivityLog.LOGS_CLEARED -> Theme.RED;
            default -> Theme.CYAN;
        };
    }

    // A horizontal line as wide as one table row.
    private static String logRule() {
        int width = String.format(LOG_ROW_FORMAT, "", "", "", "", "").length();
        return Theme.gray((Theme.USE_UNICODE ? "─" : "-").repeat(width));
    }

    // Cuts text into lines of at most "width" characters, breaking at spaces (a word longer than the width is split).
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            while (word.length() > width) {              // a very long word: split it
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ------------------------------------------------- validation helpers

    // Keeps asking until the user types a valid role (name or number). Returns null if they type 0.
    // The role is returned in its exact stored form, e.g. "Pool Attendant".
    private String readValidRole() {
        while (true) {
            System.out.println("Select new role:");
            for (int i = 0; i < VALID_ROLES.length; i++) {
                System.out.println("[" + (i + 1) + "] " + VALID_ROLES[i]);
            }
            System.out.print("Enter the role name or number (0 to cancel): ");
            String text = scanner.nextLine().trim();

            if (text.equals("0")) {
                return null;
            }
            for (int i = 0; i < VALID_ROLES.length; i++) {
                if (text.equalsIgnoreCase(VALID_ROLES[i]) || text.equals(String.valueOf(i + 1))) {
                    return VALID_ROLES[i];
                }
            }
            Theme.error("Invalid role. Please choose one of: "
                    + String.join(", ", VALID_ROLES) + ".");
        }
    }

    // Keeps asking until the user types APPROVED or PENDING (any letter case). Returns null if they type 0.
    private String readValidStatus() {
        while (true) {
            System.out.print("Enter new status (APPROVED / PENDING, 0 to cancel): ");
            String text = scanner.nextLine().trim().toUpperCase();

            if (text.equals("0")) {
                return null;
            }
            for (String status : VALID_STATUSES) {
                if (text.equals(status)) {
                    return status;
                }
            }
            Theme.error("Invalid status. Please enter APPROVED or PENDING.");
        }
    }

    // ------------------------------------------------------- table output

    // Prints any list of users as a framed table with auto-sized columns.
    // Used for "all users", "pending users", and the list shown before an approval or update.
    private static void printUserTable(List<User> users) {
        List<String[]> rows = new ArrayList<>();
        for (User u : users) {
            rows.add(new String[]{
                    String.valueOf(u.userId()),
                    u.username(),
                    u.role(),
                    Theme.status(u.status())
            });
        }
        Theme.table(new String[]{"ID", "Username", "Role", "Status"}, rows, "account");
    }
}
