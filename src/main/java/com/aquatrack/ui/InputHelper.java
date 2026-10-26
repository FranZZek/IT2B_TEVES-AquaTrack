package com.aquatrack.ui;

import java.util.Scanner;

/**
 * Console input helpers shared by every menu.
 * They all use the ONE Scanner created in Main, so System.in is never opened twice.
 */
public class InputHelper {

    // Keeps asking until the user types a whole number from min to max.
    public static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);

            if (scanner.hasNextInt()) {
                int value = scanner.nextInt();
                scanner.nextLine(); // flush the leftover newline

                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Please enter a number from " + min + " to " + max + ".");
            } else {
                scanner.nextLine(); // throw away the invalid text
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    // Keeps asking until the user types a whole number that is 0 or higher.
    // Menus use 0 to mean "cancel", so there is no upper limit like in readInt.
    public static int readNonNegativeInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);

            if (scanner.hasNextInt()) {
                int value = scanner.nextInt();
                scanner.nextLine(); // flush the leftover newline

                if (value >= 0) {
                    return value;
                }
                System.out.println("Please enter 0 or a positive number.");
            } else {
                scanner.nextLine(); // throw away the invalid text
                System.out.println("Invalid input. Please enter a whole number.");
            }
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
            System.out.println("Must be 1 to " + maxLength + " characters.");
        }
    }
}
