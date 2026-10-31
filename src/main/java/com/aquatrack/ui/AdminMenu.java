package com.aquatrack.ui;

import com.aquatrack.dao.UserDao;
import com.aquatrack.model.User;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Menu for the "Building Admin" role.
 * The admin can list every account, list the accounts waiting for approval,
 * and approve a pending account. It only talks to the user and calls the DAO.
 * There is no SQL in here.
 */
public class AdminMenu {

    // Table layout used by printUserTable(): ID | Username | Role | Status,
    // formatted with %-5s, %-15s, %-12s and %-10s.
    // A username, role or status longer than its column is cut and ends in "..." so the table stays
    // lined up (for example a 20-character username, or the role "Building Admin").
    // The User ID is never cut, because the admin types it to approve an account.
    private static final String ROW_FORMAT = "| %-5s | %-15s | %-12s | %-10s |%n";
    private static final String BORDER = "+-------+-----------------+--------------+------------+";
    private static final int USERNAME_WIDTH = 15;
    private static final int ROLE_WIDTH = 12;
    private static final int STATUS_WIDTH = 10;

    // The only values the database accepts (see the CHECK constraints in DatabaseInitializer).
    private static final String[] VALID_ROLES = {"Building Admin", "Pool Attendant", "Billing Clerk"};
    private static final String[] VALID_STATUSES = {"APPROVED", "PENDING"};

    private final User user;
    private final UserDao userDao; // the ONE shared DAO created in Main
    private final Scanner scanner; // the ONE shared Scanner created in Main

    // The status shown in the menu header. The User object is a snapshot taken at login,
    // so this is updated if the admin approves their own account during this session.
    private String currentStatus;

    public AdminMenu(User user, UserDao userDao, Scanner scanner) {
        this.user = user;
        this.userDao = userDao;
        this.scanner = scanner;
        this.currentStatus = user.status();
    }

    // Shows the menu until the user logs out.
    public void start() {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println();
            System.out.println("===== BUILDING ADMIN MENU =====");
            System.out.println("Welcome, " + user.fullName());
            System.out.println("Status : " + currentStatus);
            System.out.println("[1] View All System Users");
            System.out.println("[2] View Pending Registrations");
            System.out.println("[3] Approve User Account");
            System.out.println("[4] Update User Role & Status");
            System.out.println("[5] Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 5);

            switch (choice) {
                case 1 -> viewAllUsers();
                case 2 -> viewPendingUsers();
                case 3 -> approveUser();
                case 4 -> updateUser();
                case 5 -> {
                    System.out.println("Logged out. See you, " + user.fullName() + "!");
                    loggedIn = false;
                }
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] Every account, whatever its role or status.
    private void viewAllUsers() {
        System.out.println();
        System.out.println("--- All System Users ---");

        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            System.out.println("There are no registered accounts.");
            return;
        }
        printUserTable(users);
    }

    // [2] Only the accounts still waiting for approval, across all roles.
    private void viewPendingUsers() {
        System.out.println();
        System.out.println("--- Pending Registrations ---");

        List<User> pending = userDao.getPendingUsers();

        if (pending.isEmpty()) {
            System.out.println("There are no pending registrations.");
            return;
        }
        printUserTable(pending);
    }

    // [3] Show the pending accounts, ask for a User ID, check it is really in that list,
    // then approve it. Entering 0 cancels without changing anything.
    private void approveUser() {
        System.out.println();
        System.out.println("--- Approve User Account ---");

        List<User> pending = userDao.getPendingUsers();

        if (pending.isEmpty()) {
            System.out.println("There are no pending accounts to approve.");
            return;
        }
        printUserTable(pending);

        while (true) {
            int targetId = InputHelper.readNonNegativeInt(scanner,
                    "Enter the User ID to approve (0 to cancel): ");

            if (targetId == 0) {
                System.out.println("Approval cancelled. No changes were made.");
                return;
            }

            // Only IDs that are in the pending list can be approved.
            Optional<User> target = pending.stream()
                    .filter(u -> u.userId() == targetId)
                    .findFirst();

            if (target.isEmpty()) {
                System.out.println("User ID " + targetId + " is not in the pending list. Please try again.");
                continue;
            }

            if (userDao.approveUser(targetId)) {
                User approved = target.get();
                System.out.println("Success! " + approved.username() + " (User ID " + targetId
                        + ", " + approved.role() + ") is now APPROVED.");

                if (targetId == user.userId()) {
                    currentStatus = "APPROVED"; // the admin just approved their own account
                }
            } else {
                System.out.println("The account was NOT approved. No changes were made.");
            }
            return;
        }
    }

    // [4] Show every user, ask for a User ID, then ask for a new role and status.
    // Entering 0 at any step cancels without changing anything.
    // The admin who is logged in cannot change their own account.
    private void updateUser() {
        System.out.println();
        System.out.println("--- Update User Role & Status ---");

        List<User> users = userDao.getAllUsers();

        if (users.isEmpty()) {
            System.out.println("There are no registered accounts.");
            return;
        }
        printUserTable(users);

        User target;
        while (true) {
            int targetId = readIntInput("Enter the User ID to update (0 to cancel): ");

            if (targetId == 0) {
                cancelUpdate();
                return;
            }

            // Self-edit restriction: the active admin account cannot be modified.
            if (targetId == user.userId()) {
                System.out.println("WARNING: Modifying the currently active admin account is restricted.");
                continue;
            }

            Optional<User> found = users.stream()
                    .filter(u -> u.userId() == targetId)
                    .findFirst();

            if (found.isEmpty()) {
                System.out.println("User ID " + targetId + " does not exist. Please try again.");
                continue;
            }
            target = found.get();
            break;
        }

        System.out.println("Updating " + target.username() + " (currently " + target.role()
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
            System.out.println("Success! " + target.username() + " (User ID " + target.userId()
                    + ") is now " + newRole + " / " + newStatus.toUpperCase() + ".");
        } else {
            System.out.println("The account was NOT updated. No changes were made.");
        }
    }

    private static void cancelUpdate() {
        System.out.println("Update cancelled. No changes were made.");
    }

    // ------------------------------------------------- validation wrappers

    // Keeps asking until the user types a whole number (0 or higher).
    // Letters, symbols, blanks and decimals are rejected with a message instead of crashing.
    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String text = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(text);
                if (value >= 0) {
                    return value;
                }
                System.out.println("Please enter 0 or a positive number.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

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
            System.out.println("Invalid role. Please choose one of: "
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
            System.out.println("Invalid status. Please enter APPROVED or PENDING.");
        }
    }

    // ------------------------------------------------------- table output

    // Prints any list of users as a bordered table with aligned columns.
    // Used for "all users", "pending users", and the list shown before an approval.
    private static void printUserTable(List<User> users) {
        System.out.println(BORDER);
        System.out.printf(ROW_FORMAT, "ID", "Username", "Role", "Status");
        System.out.println(BORDER);

        for (User u : users) {
            System.out.printf(ROW_FORMAT,
                    u.userId(),
                    fit(u.username(), USERNAME_WIDTH),
                    fit(u.role(), ROLE_WIDTH),
                    fit(u.status(), STATUS_WIDTH));
        }

        System.out.println(BORDER);
        System.out.println("Total: " + users.size() + " account(s)");
    }

    // Returns the text unchanged if it fits in the column, otherwise cuts it so that
    // the result (including the "...") is exactly the column width.
    private static String fit(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, width - 3) + "...";
    }
}
