package com.aquatrack.ui;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Console input helpers shared by every menu.
 * They all use the ONE Scanner created in Main, so System.in is never opened twice.
 * Error messages go through Theme so they look the same everywhere.
 */
public class InputHelper {

    // Keeps asking until the user types a whole number from min to max.
    public static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            Integer value = readWholeNumber(scanner, prompt);

            if (value == null) {
                continue; // readWholeNumber already showed the error
            }
            if (value >= min && value <= max) {
                return value;
            }
            Theme.error("Please enter a number from " + min + " to " + max + ".");
        }
    }

    // Keeps asking until the user types a whole number that is 0 or higher.
    // Menus use 0 to mean "cancel", so there is no upper limit like in readInt.
    public static int readNonNegativeInt(Scanner scanner, String prompt) {
        while (true) {
            Integer value = readWholeNumber(scanner, prompt);

            if (value == null) {
                continue;
            }
            if (value >= 0) {
                return value;
            }
            Theme.error("Please enter 0 or a positive number.");
        }
    }

    // Reads ONE whole line, so a blank Enter is caught here too (Scanner.nextInt() would silently
    // wait for more input). Returns the number, or null after showing an error for blank or invalid text.
    private static Integer readWholeNumber(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String text = scanner.nextLine().trim();

        if (text.isEmpty()) {
            Theme.error("Please type a number.");
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            Theme.error("Invalid input. Please enter a whole number.");
            return null;
        }
    }

    // Keeps asking until the text is 1 to maxLength characters (spaces at the ends are trimmed).
    public static String readNonEmpty(Scanner scanner, String prompt, int maxLength) {
        while (true) {
            System.out.print(prompt);
            String text = scanner.nextLine().trim();

            if (!text.isEmpty() && text.length() <= maxLength) {
                return text;
            }
            Theme.error("Must be 1 to " + maxLength + " characters.");
        }
    }

    // Like readNonEmpty, but pressing Enter on an empty line is allowed and returns "".
    // Used by the update screens, where an empty answer means "keep the current value".
    public static String readOptional(Scanner scanner, String prompt, int maxLength) {
        while (true) {
            System.out.print(prompt);
            String text = scanner.nextLine().trim();

            if (text.length() <= maxLength) {
                return text;
            }
            Theme.error("Must be at most " + maxLength + " characters.");
        }
    }

    // Keeps asking until the user types a real calendar date as yyyy-MM-dd, e.g. 2026-10-31.
    // Typing 0 cancels and returns null. LocalDate.parse also rejects dates that do not exist (2026-02-30).
    public static LocalDate readDate(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String text = scanner.nextLine().trim();

            if (text.equals("0")) {
                return null;
            }
            try {
                return LocalDate.parse(text); // the default format is already yyyy-MM-dd
            } catch (DateTimeParseException e) {
                Theme.error("Invalid date. Use the format yyyy-MM-dd, for example 2026-10-31.");
            }
        }
    }

    // Keeps asking until the user types a real month as yyyy-MM, e.g. 2026-10.
    // Typing 0 cancels and returns null. YearMonth.parse rejects months that do not exist (2026-13).
    public static YearMonth readMonth(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String text = scanner.nextLine().trim();

            if (text.equals("0")) {
                return null;
            }
            try {
                return YearMonth.parse(text); // the default format is already yyyy-MM
            } catch (DateTimeParseException e) {
                Theme.error("Invalid month. Use the format yyyy-MM, for example 2026-10.");
            }
        }
    }

    // Keeps asking until the user types y / yes / n / no (any letter case). Used before every delete.
    public static boolean readYesNo(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String text = scanner.nextLine().trim().toLowerCase();

            if (text.equals("y") || text.equals("yes")) {
                return true;
            }
            if (text.equals("n") || text.equals("no")) {
                return false;
            }
            Theme.error("Please type y or n.");
        }
    }
}
