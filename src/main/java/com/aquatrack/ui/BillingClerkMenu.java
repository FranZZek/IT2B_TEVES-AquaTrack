package com.aquatrack.ui;

import com.aquatrack.dao.UserDao;
import com.aquatrack.model.User;

import java.util.Scanner;

/**
 * Menu for the "Billing Clerk" role.
 * Placeholder for now: the real CRUD options are added in the next deliverable.
 */
public class BillingClerkMenu {

    private final User user;
    private final UserDao userDao; // not used yet; kept so the next features reuse the shared DAO
    private final Scanner scanner;

    public BillingClerkMenu(User user, UserDao userDao, Scanner scanner) {
        this.user = user;
        this.userDao = userDao;
        this.scanner = scanner;
    }

    // Shows the menu until the user logs out.
    public void start() {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println();
            System.out.println("===== BILLING CLERK MENU =====");
            System.out.println("Welcome, " + user.fullName());
            System.out.println("Status : " + user.status());
            // Next deliverable: add the Billing Clerk options here, above Logout.
            System.out.println("[1] Logout");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 1);

            switch (choice) {
                case 1 -> {
                    System.out.println("Logged out. See you, " + user.fullName() + "!");
                    loggedIn = false;
                }
            }
        }
    }
}
