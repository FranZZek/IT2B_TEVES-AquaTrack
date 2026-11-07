package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.PoolLogDao;
import com.aquatrack.dao.ResidentDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.dao.UserDao;
import com.aquatrack.model.ActivityLog;
import com.aquatrack.model.User;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * AquaTrack: Condominium Pool Access & Guest Tracker (console UI).
 * The front door of the app: Register / Login / Exit, then routing to the user's role menu.
 * An account that is still PENDING cannot enter (see mayEnter), so approval really means something.
 * It only talks to the user and calls the DAO. There is no SQL in here.
 */
public class Authentication {

    // The roles from the System Proposal. They must match the CHECK on users.role in DatabaseInitializer.
    private static final String BUILDING_ADMIN = "Building Admin";
    private static final String POOL_ATTENDANT = "Pool Attendant";
    private static final String BILLING_CLERK = "Billing Clerk";
    private static final String[] ROLES = {BUILDING_ADMIN, POOL_ATTENDANT, BILLING_CLERK};

    // The status that lets an account use the system (the other one is PENDING).
    private static final String APPROVED = "APPROVED";

    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_PASSWORD_LENGTH = 6;

    // The ONE DAOs and Scanner (created in Main). Every menu opened from here gets these same instances.
    private final UserDao userDao;
    private final UnitDao unitDao;
    private final ResidentDao residentDao;
    private final PoolLogDao poolLogDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;

    public Authentication(UserDao userDao, UnitDao unitDao, ResidentDao residentDao,
                          PoolLogDao poolLogDao, ActivityLogDao activityLogDao, Scanner scanner) {
        this.userDao = userDao;
        this.unitDao = unitDao;
        this.residentDao = residentDao;
        this.poolLogDao = poolLogDao;
        this.activityLogDao = activityLogDao;
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
            Theme.info("Input closed. Exiting system.");
        }
    }

    // ---------------------------------------------------------------- menus

    // Returns false when the user chooses Exit.
    private boolean showMainMenu() {
        Theme.clearScreen();
        Theme.banner();
        Theme.menu("MAIN MENU", Theme.CYAN, List.of(),
                "Register Account",
                "Login",
                "Exit System");

        int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 3);

        switch (choice) {
            case 1 -> register();
            case 2 -> login();
            case 3 -> {
                Theme.success("Exiting system. Goodbye!");
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ register

    private void register() {
        Theme.heading("Register Account");

        String username = readUsername();
        String password = readPassword();
        String fullName = InputHelper.readNonEmpty(scanner, "Full name: ", 50);
        String role = readRole();

        User user = new User(username, UserDao.hashPassword(password), fullName, role);

        if (userDao.insert(user)) {
            Theme.success("Account created! Status: PENDING.");
        } else {
            Theme.error("Account was NOT created. Please try again.");
        }
        Theme.pause(scanner);
    }

    // --------------------------------------------------------------- login

    private void login() {
        Theme.heading("Login");

        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        User user = userDao.login(username, password);

        if (user == null) {
            Theme.error("Invalid username or password.");
            Theme.pause(scanner);
        } else if (!mayEnter(user)) {
            Theme.warn("Your account is still waiting for approval by a Building Admin.");
            Theme.pause(scanner);
        } else {
            Theme.success("Login successful!");
            activityLogDao.logAction(user.userId(), ActivityLog.LOGIN,
                    user.username() + " logged in as " + user.role());

            routeToRoleMenu(user); // runs until the user chooses Logout

            activityLogDao.logAction(user.userId(), ActivityLog.LOGOUT, user.username() + " logged out");
        }
    }

    // A PENDING account may only enter if it is the very first Building Admin. Without that
    // exception nobody could ever approve anything on a brand-new database.
    private boolean mayEnter(User user) {
        if (user.status().equals(APPROVED)) {
            return true;
        }
        return user.role().equals(BUILDING_ADMIN) && !hasApprovedAdmin(userDao);
    }

    // True when at least one Building Admin account is APPROVED.
    // Package-private because AdminMenu uses the same rule to block self-approval.
    static boolean hasApprovedAdmin(UserDao userDao) {
        for (User account : userDao.getAllUsers()) {
            if (account.role().equals(BUILDING_ADMIN) && account.status().equals(APPROVED)) {
                return true;
            }
        }
        return false;
    }

    // Sends the logged-in user straight to the menu for their role.
    // Each menu runs until the user logs out, then we return to the main menu.
    private void routeToRoleMenu(User user) {
        switch (user.role()) {
            case BUILDING_ADMIN -> new AdminMenu(user, userDao, unitDao, residentDao, poolLogDao, activityLogDao, scanner).start();
            case POOL_ATTENDANT -> new PoolAttendantMenu(user, unitDao, residentDao, poolLogDao, activityLogDao, scanner).start();
            case BILLING_CLERK -> new BillingClerkMenu(user, unitDao, poolLogDao, activityLogDao, scanner).start();
            default -> {
                Theme.error("No menu is set up for the role \"" + user.role() + "\". Logging out.");
                Theme.pause(scanner);
            }
        }
    }

    // ------------------------------------------------------ input helpers

    // Username: no spaces or tabs, unique in the database (not case-sensitive).
    private String readUsername() {
        while (true) {
            String username = InputHelper.readNonEmpty(scanner,
                    "Username (max " + MAX_USERNAME_LENGTH + " characters): ", MAX_USERNAME_LENGTH);

            if (username.matches(".*\\s.*")) {
                Theme.error("Username cannot contain spaces.");
            } else if (userDao.usernameExists(username)) {
                Theme.error("That username is already taken.");
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
            Theme.error("Password is too short.");
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
