package com.aquatrack.ui;

import com.aquatrack.dao.UserDao;
import com.aquatrack.model.User;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * AquaTrack: Condominium Pool Access & Guest Tracker (console UI).
 * The front door of the app: Register / Login / Exit, then routing to the user's role menu.
 * It only talks to the user and calls the DAO. There is no SQL in here.
 */
public class Authentication {

    // The roles from the System Proposal. They must match the CHECK on users.role in DatabaseInitializer.
    private static final String BUILDING_ADMIN = "Building Admin";
    private static final String POOL_ATTENDANT = "Pool Attendant";
    private static final String BILLING_CLERK = "Billing Clerk";
    private static final String[] ROLES = {BUILDING_ADMIN, POOL_ATTENDANT, BILLING_CLERK};

    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_PASSWORD_LENGTH = 6;

    // The ONE UserDao and Scanner (created in Main). Every menu opened from here gets these same two.
    private final UserDao userDao;
    private final Scanner scanner;

    public Authentication(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.scanner = scanner;
    }

    public void start() {
        boolean running = true;
        try {
            while (running) {
                running = showMainMenu();
            }
        } catch (NoSuchElementException e) {
            // The input stream was closed (e.g. Ctrl+D), so there is nothing left to read.
            System.out.println();
            System.out.println("Input closed. Exiting system.");
        }
    }

    // ---------------------------------------------------------------- menus

    // Returns false when the user chooses Exit.
    private boolean showMainMenu() {
        System.out.println();
        System.out.println("===== AQUATRACK: POOL ACCESS & GUEST TRACKER =====");
        System.out.println("[1] Register Account");
        System.out.println("[2] Login");
        System.out.println("[3] Exit System");

        int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 3);

        switch (choice) {
            case 1 -> register();
            case 2 -> login();
            case 3 -> {
                System.out.println("Exiting system. Goodbye!");
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ register

    private void register() {
        System.out.println();
        System.out.println("--- Register Account ---");

        String username = readUsername();
        String password = readPassword();
        String fullName = InputHelper.readNonEmpty(scanner, "Full name: ", 50);
        String role = readRole();

        User user = new User(username, UserDao.hashPassword(password), fullName, role);

        if (userDao.insert(user)) {
            System.out.println("Account created! Status: PENDING.");
        } else {
            System.out.println("Account was NOT created. Please try again.");
        }
    }

    // --------------------------------------------------------------- login

    private void login() {
        System.out.println();
        System.out.println("--- Login ---");

        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        User user = userDao.login(username, password);

        if (user == null) {
            System.out.println("Invalid username or password.");
        } else {
            System.out.println("Login successful!");
            routeToRoleMenu(user);
        }
    }

    // Sends the logged-in user straight to the menu for their role.
    // Each menu runs until the user logs out, then we return to the main menu.
    private void routeToRoleMenu(User user) {
        switch (user.role()) {
            case BUILDING_ADMIN -> new AdminMenu(user, userDao, scanner).start();
            case POOL_ATTENDANT -> new PoolAttendantMenu(user, userDao, scanner).start();
            case BILLING_CLERK -> new BillingClerkMenu(user, userDao, scanner).start();
            default -> System.out.println("No menu is set up for the role \"" + user.role() + "\". Logging out.");
        }
    }

    // ------------------------------------------------------ input helpers

    // Username: no spaces or tabs, unique in the database (not case-sensitive).
    private String readUsername() {
        while (true) {
            String username = InputHelper.readNonEmpty(scanner,
                    "Username (max " + MAX_USERNAME_LENGTH + " characters): ", MAX_USERNAME_LENGTH);

            if (username.matches(".*\\s.*")) {
                System.out.println("Username cannot contain spaces.");
            } else if (userDao.usernameExists(username)) {
                System.out.println("That username is already taken.");
            } else {
                return username;
            }
        }
    }

    private String readPassword() {
        while (true) {
            System.out.print("Password (min " + MIN_PASSWORD_LENGTH + " characters): ");
            String password = scanner.nextLine();

            if (password.length() >= MIN_PASSWORD_LENGTH) {
                return password;
            }
            System.out.println("Password is too short.");
        }
    }

    private String readRole() {
        System.out.println("Select role:");
        for (int i = 0; i < ROLES.length; i++) {
            System.out.println("[" + (i + 1) + "] " + ROLES[i]);
        }
        return ROLES[InputHelper.readInt(scanner, "Choose a role: ", 1, ROLES.length) - 1];
    }
}
