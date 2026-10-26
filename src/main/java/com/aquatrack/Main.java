package com.aquatrack;

import com.aquatrack.dao.DatabaseInitializer;
import com.aquatrack.dao.UserDao;
import com.aquatrack.ui.Authentication;

import java.util.Locale;
import java.util.Scanner;

/**
 * Application entry point. Nothing else lives here.
 * Flow: initialize database -> create the shared DAO + Scanner -> hand control to Authentication.
 */
public class Main {

    public static void main(String[] args) {
        // 1. Database first: every table must exist before any menu is shown.
        System.out.println("Initializing database...");
        if (!DatabaseInitializer.initialize()) {
            System.out.println("Could not set up the database. Exiting system.");
            return;
        }
        System.out.println("Database initialized successfully.");

        // 2. Create the shared objects ONCE. Every menu receives these same instances.
        Locale.setDefault(Locale.US); // must come BEFORE the Scanner is created, or the Scanner ignores it
        UserDao userDao = new UserDao();

        try (Scanner scanner = new Scanner(System.in)) {
            // 3. Hand over to the login / registration screen.
            new Authentication(userDao, scanner).start();
        }
    }
}
