package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.ResidentDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.model.ActivityLog;
import com.aquatrack.model.Unit;
import com.aquatrack.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * "Manage Units" screen of the Building Admin: view, search, add and modify condo units.
 * The proposal has no "delete unit" (pool logs point to units), so there is none here.
 * It only talks to the user and calls the DAOs. There is no SQL in here.
 */
public class UnitMenu {

    private static final int MAX_UNIT_NUMBER = 9999;
    private static final int MAX_NAME_LENGTH = 50;

    // Digits plus the usual phone symbols, 7 to 20 characters, e.g. 0917-123-4567 or +63 917 123 4567.
    private static final String CONTACT_PATTERN = "[0-9+\\-() ]{7,20}";

    private final User user;
    private final UnitDao unitDao;               // the ONE shared DAOs created in Main
    private final ResidentDao residentDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;               // the ONE shared Scanner created in Main

    public UnitMenu(User user, UnitDao unitDao, ResidentDao residentDao,
                    ActivityLogDao activityLogDao, Scanner scanner) {
        this.user = user;
        this.unitDao = unitDao;
        this.residentDao = residentDao;
        this.activityLogDao = activityLogDao;
        this.scanner = scanner;
    }

    // Shows the menu until the admin goes back.
    public void start() {
        boolean open = true;

        while (open) {
            Theme.clearScreen();
            Theme.menu("MANAGE UNITS", Theme.MAGENTA, List.of(),
                    "View All Units",
                    "Search a Unit",
                    "Add New Unit",
                    "Modify Unit Details",
                    "Back");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 5);

            switch (choice) {
                case 1 -> { viewAllUnits(); Theme.pause(scanner); }
                case 2 -> { searchUnit(); Theme.pause(scanner); }
                case 3 -> { addUnit(); Theme.pause(scanner); }
                case 4 -> { modifyUnit(); Theme.pause(scanner); }
                case 5 -> open = false;
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] Every unit with how many residents are registered to it.
    private void viewAllUnits() {
        Theme.heading("All Condo Units");

        List<Unit> units = unitDao.getAllUnits();

        if (units.isEmpty()) {
            Theme.info("There are no units yet. Choose \"Add New Unit\" to create one.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        for (Unit unit : units) {
            rows.add(new String[]{
                    String.valueOf(unit.unitNumber()),
                    unit.ownerName(),
                    unit.contactNumber(),
                    String.valueOf(residentDao.countByUnit(unit.unitNumber())),
                    Theme.status(unit.occupancyStatus())
            });
        }
        Theme.table(new String[]{"Unit", "Owner", "Contact", "Residents", "Status"}, rows, "unit");
    }

    // [2] One unit as a card. Entering 0 cancels.
    private void searchUnit() {
        Theme.heading("Search a Unit");

        Unit unit = readExistingUnit("Unit number to search (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled.");
            return;
        }
        showUnitCard(unit);
    }

    // [3] Ask for the details, check them, then save. A new unit starts Vacant:
    // it becomes Occupied by itself when the first resident is registered to it.
    private void addUnit() {
        Theme.heading("Add New Unit");

        int unitNumber;
        while (true) {
            unitNumber = InputHelper.readNonNegativeInt(scanner, "New unit number (0 to cancel): ");

            if (unitNumber == 0) {
                Theme.info("Cancelled. No unit was added.");
                return;
            }
            if (unitNumber > MAX_UNIT_NUMBER) {
                Theme.error("Unit numbers go from 1 to " + MAX_UNIT_NUMBER + ".");
            } else if (unitDao.findByUnitNumber(unitNumber) != null) {
                Theme.error("Unit " + unitNumber + " already exists.");
            } else {
                break;
            }
        }

        String owner = InputHelper.readNonEmpty(scanner, "Owner name: ", MAX_NAME_LENGTH);
        String contact = readContact("Contact number: ");

        Unit unit = new Unit(unitNumber, owner, contact, Unit.VACANT);

        if (unitDao.insert(unit)) {
            Theme.success("Unit " + unitNumber + " was added as Vacant.");
            activityLogDao.logAction(user.userId(), ActivityLog.UNIT_ADDED,
                    "Admin [" + user.userId() + "] added Unit " + unitNumber + " (owner " + owner + ")");
        } else {
            Theme.error("The unit was NOT added.");
        }
    }

    // [4] Show the unit, then ask for each new value. Pressing Enter keeps the current value.
    // Entering 0 at the unit number cancels.
    private void modifyUnit() {
        Theme.heading("Modify Unit Details");

        Unit unit = readExistingUnit("Unit number to modify (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled.");
            return;
        }
        showUnitCard(unit);
        Theme.info("Press Enter on a question to keep the current value.");

        String owner = InputHelper.readOptional(scanner,
                "Owner name [" + unit.ownerName() + "]: ", MAX_NAME_LENGTH);
        if (owner.isEmpty()) {
            owner = unit.ownerName();
        }

        String contact = readOptionalContact("Contact number [" + unit.contactNumber() + "]: ");
        if (contact.isEmpty()) {
            contact = unit.contactNumber();
        }

        String status = readStatus(unit);
        if (status == null) {
            return; // the reason was already shown
        }

        Unit updated = new Unit(unit.unitNumber(), owner, contact, status);
        if (updated.equals(unit)) {
            Theme.info("Nothing was changed.");
            return;
        }

        if (unitDao.update(updated)) {
            Theme.success("Unit " + unit.unitNumber() + " was updated.");
            activityLogDao.logAction(user.userId(), ActivityLog.UNIT_UPDATED,
                    "Admin [" + user.userId() + "] updated Unit " + unit.unitNumber()
                            + " (owner " + owner + ", " + status + ")");
        } else {
            Theme.error("The unit was NOT updated. No changes were made.");
        }
    }

    // ------------------------------------------------------------- helpers

    // Draws one unit as a framed card, with the names of its registered residents.
    private void showUnitCard(Unit unit) {
        int residents = residentDao.countByUnit(unit.unitNumber());

        Theme.box("UNIT " + unit.unitNumber(), Theme.MAGENTA, List.of(
                "Owner     : " + unit.ownerName(),
                "Contact   : " + unit.contactNumber(),
                "Status    : " + Theme.status(unit.occupancyStatus()),
                "Residents : " + residents + " registered"));
    }

    // Keeps asking for a unit number until the unit exists. Returns null if the user types 0.
    private Unit readExistingUnit(String prompt) {
        while (true) {
            int unitNumber = InputHelper.readNonNegativeInt(scanner, prompt);

            if (unitNumber == 0) {
                return null;
            }

            Unit unit = unitDao.findByUnitNumber(unitNumber);
            if (unit != null) {
                return unit;
            }
            Theme.error("Unit " + unitNumber + " does not exist.");
        }
    }

    // A required contact number that looks like a phone number.
    private String readContact(String prompt) {
        while (true) {
            String text = InputHelper.readNonEmpty(scanner, prompt, 20);

            if (text.matches(CONTACT_PATTERN)) {
                return text;
            }
            Theme.error("Use 7 to 20 digits (you may also use + - ( ) and spaces).");
        }
    }

    // Like readContact, but Enter on an empty line is allowed and returns "" (keep the current number).
    private String readOptionalContact(String prompt) {
        while (true) {
            String text = InputHelper.readOptional(scanner, prompt, 20);

            if (text.isEmpty() || text.matches(CONTACT_PATTERN)) {
                return text;
            }
            Theme.error("Use 7 to 20 digits (you may also use + - ( ) and spaces).");
        }
    }

    // Asks for the occupancy status. Enter keeps the current one. Returns null if the change is not allowed:
    // a unit that still has registered residents cannot be marked Vacant.
    private String readStatus(Unit unit) {
        while (true) {
            System.out.print("Occupancy [1] Occupied  [2] Vacant  (Enter keeps " + unit.occupancyStatus() + "): ");
            String text = scanner.nextLine().trim();

            String status;
            if (text.isEmpty()) {
                return unit.occupancyStatus();
            } else if (text.equals("1") || text.equalsIgnoreCase(Unit.OCCUPIED)) {
                status = Unit.OCCUPIED;
            } else if (text.equals("2") || text.equalsIgnoreCase(Unit.VACANT)) {
                status = Unit.VACANT;
            } else {
                Theme.error("Please type 1, 2 or press Enter.");
                continue;
            }

            int residents = residentDao.countByUnit(unit.unitNumber());
            if (status.equals(Unit.VACANT) && residents > 0) {
                Theme.error("Unit " + unit.unitNumber() + " still has " + residents
                        + " registered resident(s). Remove them first. Nothing was changed.");
                return null;
            }
            return status;
        }
    }
}
