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
            System.out.println("[4] Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 4);

            switch (choice) {
                case 1 -> viewAllUsers();
                case 2 -> viewPendingUsers();
                case 3 -> approveUser();
                case 4 -> {
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
