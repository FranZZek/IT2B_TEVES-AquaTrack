package com.aquatrack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * The look of AquaTrack ("pool-side control room"). Every screen draws itself through this class,
 * so colors, frames, tables and messages stay consistent. There is no SQL and no business logic here.
 *
 * Two switches (just below) fix most console problems:
 *   USE_COLORS  -> false if you see junk like "[36m" around the text.
 *   USE_UNICODE -> false if the lines and bars show up as "?" boxes (falls back to + - | #).
 */
public final class Theme {

    // ------------------------------------------------------------ switches

    // Colors are on unless the NO_COLOR environment variable is set. Change to false to force them off.
    public static final boolean USE_COLORS = System.getenv("NO_COLOR") == null;

    // Box-drawing lines and bars. Change to false for a plain-ASCII look.
    public static final boolean USE_UNICODE = true;

    // true = a horizontal line between every table row, false = only under the header.
    private static final boolean LINES_BETWEEN_ROWS = false;

    // ------------------------------------------------------------- colors
    // ANSI color codes. The role menus use these as their accent color.

    public static final String CYAN = "36";
    public static final String BLUE = "94";
    public static final String GREEN = "32";
    public static final String YELLOW = "33";
    public static final String RED = "31";
    public static final String MAGENTA = "35";
    public static final String GRAY = "90";

    // Capacity bar: green below 70 %, yellow from 70 %, red from 90 %.
    private static final int BAR_WIDTH = 20;
    private static final int BAR_YELLOW_AT = 70;
    private static final int BAR_RED_AT = 90;

    // Smallest width of a menu / box, so short menus still look like panels.
    private static final int MIN_BOX_WIDTH = 46;

    // Lines used by frames and tables (single-line box drawing, or ASCII fallback).
    private static final String H = USE_UNICODE ? "─" : "-";
    private static final String V = USE_UNICODE ? "│" : "|";
    private static final String TL = USE_UNICODE ? "┌" : "+";
    private static final String TR = USE_UNICODE ? "┐" : "+";
    private static final String BL = USE_UNICODE ? "└" : "+";
    private static final String BR = USE_UNICODE ? "┘" : "+";
    private static final String TJ = USE_UNICODE ? "┬" : "+";
    private static final String BJ = USE_UNICODE ? "┴" : "+";
    private static final String LJ = USE_UNICODE ? "├" : "+";
    private static final String RJ = USE_UNICODE ? "┤" : "+";
    private static final String X = USE_UNICODE ? "┼" : "+";

    // Progress bar pieces and the icons in front of messages.
    private static final String BAR_FULL = USE_UNICODE ? "█" : "#";
    private static final String BAR_EMPTY = USE_UNICODE ? "░" : "-";
    private static final String ICON_OK = USE_UNICODE ? "✔" : "[OK]";
    private static final String ICON_WARN = USE_UNICODE ? "▲" : "[!]";
    private static final String ICON_ERROR = USE_UNICODE ? "✖" : "[X]";
    private static final String ICON_INFO = USE_UNICODE ? "●" : "[i]";

    // The wave-style logo, one String per row (the top rows are cyan, the bottom rows blue).
    private static final String[] LOGO = {
            "    _                          _____                     _",
            "   / \\     __ _  _   _   __ _ |_   _| _ __   __ _   ___ | | __",
            "  / _ \\   / _` || | | | / _` |  | |  | '__| / _` | / __|| |/ /",
            " / ___ \\ | (_| || |_| || (_| |  | |  | |   | (_| || (__ |   <",
            "/_/   \\_\\ \\__, | \\__,_| \\__,_|  |_|  |_|    \\__,_| \\___||_|\\_\\",
            "             |_|"
    };

    private Theme() {
        // Only static helpers: nothing to create.
    }

    // ------------------------------------------------------- color helpers

    // Wraps text in an ANSI color code. Returns the text unchanged when colors are off.
    public static String paint(String code, String text) {
        if (!USE_COLORS) {
            return text;
        }
        return "\u001B[" + code + "m" + text + "\u001B[0m";
    }

    public static String bold(String text) {
        return paint("1", text);
    }

    public static String cyan(String text) {
        return paint(CYAN, text);
    }

    public static String green(String text) {
        return paint(GREEN, text);
    }

    public static String yellow(String text) {
        return paint(YELLOW, text);
    }

    public static String red(String text) {
        return paint(RED, text);
    }

    public static String gray(String text) {
        return paint(GRAY, text);
    }

    // Colors an occupancy / account status: good = green, waiting or empty = yellow, anything else unchanged.
    public static String status(String text) {
        return switch (text.toUpperCase()) {
            case "OCCUPIED", "APPROVED", "INSIDE" -> green(text);
            case "VACANT", "PENDING" -> yellow(text);
            default -> text;
        };
    }

    // ---------------------------------------------------- messages (icons)

    public static void success(String message) {
        System.out.println(paint("1;" + GREEN, ICON_OK) + " " + green(message));
    }

    public static void warn(String message) {
        System.out.println(paint("1;" + YELLOW, ICON_WARN) + " " + yellow(message));
    }

    public static void error(String message) {
        System.out.println(paint("1;" + RED, ICON_ERROR) + " " + red(message));
    }

    public static void info(String message) {
        System.out.println(paint("1;" + CYAN, ICON_INFO) + " " + message);
    }

    // ------------------------------------------------------ screen control

    // Wipes the screen so each menu feels like a page. Consoles that ignore the code (like the
    // IntelliJ Run window) simply keep scrolling, which is harmless.
    public static void clearScreen() {
        if (USE_COLORS) {
            System.out.print("\u001B[H\u001B[2J");
            System.out.flush();
        }
    }

    // Holds the screen until the user presses Enter, so a result can be read before the next page.
    public static void pause(Scanner scanner) {
        System.out.println();
        System.out.print(gray("Press Enter to continue..."));
        scanner.nextLine();
    }

    // A short "working on it" touch: the message with three dots that appear one by one.
    public static void loading(String message) {
        System.out.print(cyan(message));
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // keep the interrupt flag and stop waiting
                break;
            }
            System.out.print(cyan("."));
        }
        System.out.println();
    }

    // ------------------------------------------------------------- banner

    // The AquaTrack logo, a wave line and the tagline.
    public static void banner() {
        System.out.println();
        for (int i = 0; i < LOGO.length; i++) {
            System.out.println(paint("1;" + (i < 3 ? CYAN : BLUE), LOGO[i]));
        }
        System.out.println(waveLine(64));
        System.out.println(gray("  Condominium Pool Access & Guest Tracker"));
    }

    // A line of waves that alternates between cyan and blue.
    private static String waveLine(int length) {
        String wave = USE_UNICODE ? "≈" : "~";
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < length; i++) {
            line.append(paint(i % 2 == 0 ? CYAN : BLUE, wave));
        }
        return line.toString();
    }

    // ---------------------------------------------------- frames and menus

    // A framed menu: title bar, optional info lines (e.g. "Welcome, Franz"), then the numbered options.
    // An item that starts with "# " is a section heading and does not get a number.
    // Options are numbered 1, 2, 3... in the order given, so the caller's switch must use the same numbers.
    public static void menu(String title, String accent, List<String> infoLines, String... items) {
        List<String> options = new ArrayList<>();
        int number = 1;
        for (String item : items) {
            if (item.startsWith("# ")) {
                options.add(gray(H + H + " " + item.substring(2)));
            } else {
                options.add(paint("1;" + accent, "[" + number + "]") + " " + item);
                number++;
            }
        }

        List<List<String>> sections = new ArrayList<>();
        if (!infoLines.isEmpty()) {
            sections.add(infoLines);
        }
        sections.add(options);
        frame(title, accent, sections);
    }

    // A framed box with a title and plain lines (used for a single record, such as one unit).
    public static void box(String title, String accent, List<String> lines) {
        frame(title, accent, List.of(lines));
    }

    // Draws the title bar and every section, with a divider line between sections.
    private static void frame(String title, String accent, List<List<String>> sections) {
        int inner = Math.max(MIN_BOX_WIDTH, visibleLength(title) + 2);
        for (List<String> section : sections) {
            for (String line : section) {
                inner = Math.max(inner, visibleLength(line) + 2);
            }
        }

        System.out.println();
        System.out.println(paint(accent, TL + H.repeat(inner) + TR));
        System.out.println(frameRow(paint("1;" + accent, title), inner, accent));

        for (List<String> section : sections) {
            System.out.println(paint(accent, LJ + H.repeat(inner) + RJ));
            for (String line : section) {
                System.out.println(frameRow(line, inner, accent));
            }
        }
        System.out.println(paint(accent, BL + H.repeat(inner) + BR));
    }

    // One line inside a frame: border, one space, the text, padding, one space, border.
    private static String frameRow(String content, int inner, String accent) {
        int padding = inner - 1 - visibleLength(content);
        return paint(accent, V) + " " + content + " ".repeat(Math.max(0, padding)) + paint(accent, V);
    }

    // A screen heading, for example "◆ All System Units" with a line under it.
    public static void heading(String text) {
        System.out.println();
        System.out.println(paint("1;" + CYAN, (USE_UNICODE ? "◆ " : "> ") + text));
        System.out.println(paint(CYAN, H.repeat(text.length() + 2)));
    }

    // -------------------------------------------------------------- table

    // Prints a table with auto-sized, aligned columns, then "Total: N <noun>(s)".
    // Cells may contain color codes (see status()); they are measured without the codes.
    public static void table(String[] headers, List<String[]> rows, String noun) {
        table(headers, rows, null, noun);
    }

    // Same table, plus a totals row under a divider line (reports use this).
    // Pass null for totalsRow to get a table without it. The totals row needs one cell per column.
    public static void table(String[] headers, List<String[]> rows, String[] totalsRow, String noun) {
        int[] widths = new int[headers.length];
        for (int c = 0; c < headers.length; c++) {
            widths[c] = headers[c].length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < headers.length; c++) {
                widths[c] = Math.max(widths[c], visibleLength(row[c]));
            }
        }
        if (totalsRow != null) {
            for (int c = 0; c < headers.length; c++) {
                widths[c] = Math.max(widths[c], visibleLength(totalsRow[c]));
            }
        }

        System.out.println(tableLine(TL, TJ, TR, widths));

        String[] headerCells = new String[headers.length];
        for (int c = 0; c < headers.length; c++) {
            headerCells[c] = paint("1;" + CYAN, headers[c]);
        }
        System.out.println(tableRow(headerCells, widths));
        System.out.println(tableLine(LJ, X, RJ, widths));

        for (int r = 0; r < rows.size(); r++) {
            System.out.println(tableRow(rows.get(r), widths));
            if (LINES_BETWEEN_ROWS && r < rows.size() - 1) {
                System.out.println(tableLine(LJ, X, RJ, widths));
            }
        }

        if (totalsRow != null) {
            System.out.println(tableLine(LJ, X, RJ, widths));
            System.out.println(tableRow(totalsRow, widths));
        }

        System.out.println(tableLine(BL, BJ, BR, widths));
        System.out.println(gray("Total: " + rows.size() + " " + noun + "(s)"));
    }

    // A horizontal table line such as ┌──────┬──────┐ (left, middle and right pieces are chosen by the caller).
    private static String tableLine(String left, String middle, String right, int[] widths) {
        StringBuilder line = new StringBuilder(left);
        for (int c = 0; c < widths.length; c++) {
            line.append(H.repeat(widths[c] + 2));
            line.append(c < widths.length - 1 ? middle : right);
        }
        return paint(CYAN, line.toString());
    }

    // One table row: │ cell │ cell │, each cell padded to its column width.
    private static String tableRow(String[] cells, int[] widths) {
        StringBuilder row = new StringBuilder(paint(CYAN, V));
        for (int c = 0; c < widths.length; c++) {
            int padding = widths[c] - visibleLength(cells[c]);
            row.append(" ").append(cells[c]).append(" ".repeat(padding)).append(" ");
            row.append(paint(CYAN, V));
        }
        return row.toString();
    }

    // --------------------------------------------------------- capacity bar

    // Example: [████████████░░░░░░░░] 18/30 (60%). Green, then yellow, then red as the pool fills.
    // If current is above max, the bar stays full and the percentage goes over 100.
    public static String capacityBar(int current, int max) {
        int percent = max <= 0 ? 0 : (int) Math.round(current * 100.0 / max);
        int filled = Math.min(BAR_WIDTH, Math.max(0, percent * BAR_WIDTH / 100));

        String color = percent >= BAR_RED_AT ? RED : percent >= BAR_YELLOW_AT ? YELLOW : GREEN;
        String bar = BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(BAR_WIDTH - filled);

        return "[" + paint(color, bar) + "] " + paint("1;" + color, current + "/" + max + " (" + percent + "%)");
    }

    // ------------------------------------------------------------ helpers

    // Text length without the invisible color codes, so padding stays correct.
    private static int visibleLength(String text) {
        return text.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }
}
