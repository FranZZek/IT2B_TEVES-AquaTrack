package com.aquatrack.ui;

import com.aquatrack.dao.ActivityLogDao;
import com.aquatrack.dao.ResidentDao;
import com.aquatrack.dao.UnitDao;
import com.aquatrack.model.ActivityLog;
import com.aquatrack.model.Resident;
import com.aquatrack.model.Unit;
import com.aquatrack.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * "Manage Residents" screen of the Building Admin: view, register, update and remove residents.
 * It keeps the unit's occupancy honest: registering the first resident makes a Vacant unit Occupied,
 * and removing the last resident makes the unit Vacant again.
 * It only talks to the user and calls the DAOs. There is no SQL in here.
 */
public class ResidentMenu {

    private static final int MAX_NAME_LENGTH = 30;

    private final User user;
    private final ResidentDao residentDao;       // the ONE shared DAOs created in Main
    private final UnitDao unitDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;               // the ONE shared Scanner created in Main

    public ResidentMenu(User user, ResidentDao residentDao, UnitDao unitDao,
                        ActivityLogDao activityLogDao, Scanner scanner) {
        this.user = user;
        this.residentDao = residentDao;
        this.unitDao = unitDao;
        this.activityLogDao = activityLogDao;
        this.scanner = scanner;
    }

    // Shows the menu until the admin goes back.
    public void start() {
        boolean open = true;

        while (open) {
            Theme.clearScreen();
            Theme.menu("MANAGE RESIDENTS", Theme.MAGENTA, List.of(),
                    "View All Residents",
                    "View Residents of a Unit",
                    "Register New Resident",
                    "Update Resident Profile",
                    "Remove Resident (Moved Out)",
                    "Back");

            int choice = InputHelper.readInt(scanner, "Choose an option: ", 1, 6);

            switch (choice) {
                case 1 -> { viewAllResidents(); Theme.pause(scanner); }
                case 2 -> { viewResidentsOfUnit(); Theme.pause(scanner); }
                case 3 -> { registerResident(); Theme.pause(scanner); }
                case 4 -> { updateResident(); Theme.pause(scanner); }
                case 5 -> { removeResident(); Theme.pause(scanner); }
                case 6 -> open = false;
            }
        }
    }

    // ------------------------------------------------------------- options

    // [1] Every resident of the building.
    private void viewAllResidents() {
        Theme.heading("All Residents");

        List<Resident> residents = residentDao.getAllResidents();

        if (residents.isEmpty()) {
            Theme.info("There are no registered residents yet.");
            return;
        }
        printResidentTable(residents);
    }

    // [2] The residents of ONE unit. Entering 0 cancels.
    private void viewResidentsOfUnit() {
        Theme.heading("Residents of a Unit");

        Unit unit = readExistingUnit("Unit number (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled.");
            return;
        }

        Theme.info("Unit " + unit.unitNumber() + " - owner " + unit.ownerName()
                + " - " + Theme.status(unit.occupancyStatus()));

        List<Resident> residents = residentDao.getResidentsByUnit(unit.unitNumber());
        if (residents.isEmpty()) {
            Theme.warn("No residents are registered to this unit.");
            return;
        }
        printResidentTable(residents);
    }

    // [3] Ask for the unit and the details, then save. Entering 0 at the unit number cancels.
    private void registerResident() {
        Theme.heading("Register New Resident");

        Unit unit = readExistingUnit("Assign to unit number (0 to cancel): ");
        if (unit == null) {
            Theme.info("Cancelled. No resident was added.");
            return;
        }

        String firstName = InputHelper.readNonEmpty(scanner, "First name: ", MAX_NAME_LENGTH);
        String lastName = InputHelper.readNonEmpty(scanner, "Last name: ", MAX_NAME_LENGTH);
        String ageCategory = readAgeCategory("Age category", null);

        Resident resident = new Resident(firstName, lastName, ageCategory, unit.unitNumber());

        if (!residentDao.insert(resident)) {
            Theme.error("The resident was NOT registered.");
            return;
        }

        Theme.success(resident.fullName() + " (" + ageCategory + ") was registered to Unit "
                + unit.unitNumber() + ".");
        activityLogDao.logAction(user.userId(), ActivityLog.RESIDENT_ADDED,
                "Admin [" + user.userId() + "] registered " + resident.fullName()
                        + " to Unit " + unit.unitNumber());

        // The first resident makes a Vacant unit Occupied.
        if (unit.occupancyStatus().equals(Unit.VACANT) && unitDao.update(unit.withOccupancy(Unit.OCCUPIED))) {
            Theme.info("Unit " + unit.unitNumber() + " is now Occupied.");
        }
    }

    // [4] Show every resident, ask for an ID, then ask for the new values.
    // Pressing Enter keeps the current value. Entering 0 at the ID cancels.
    private void updateResident() {
        Theme.heading("Update Resident Profile");

        Resident resident = readExistingResident("Resident ID to update (0 to cancel): ");
        if (resident == null) {
            Theme.info("Cancelled.");
            return;
        }
        showResidentCard(resident);
        Theme.info("Press Enter on a question to keep the current value.");

        String firstName = InputHelper.readOptional(scanner,
                "First name [" + resident.firstName() + "]: ", MAX_NAME_LENGTH);
        String lastName = InputHelper.readOptional(scanner,
                "Last name [" + resident.lastName() + "]: ", MAX_NAME_LENGTH);
        String ageCategory = readAgeCategory("Age category", resident.ageCategory());

        Resident updated = new Resident(
                resident.residentId(),
                firstName.isEmpty() ? resident.firstName() : firstName,
                lastName.isEmpty() ? resident.lastName() : lastName,
                ageCategory,
                resident.unitNumber());

        if (updated.equals(resident)) {
            Theme.info("Nothing was changed.");
            return;
        }

        if (residentDao.update(updated)) {
            Theme.success("Resident " + resident.residentId() + " was updated.");
            activityLogDao.logAction(user.userId(), ActivityLog.RESIDENT_UPDATED,
                    "Admin [" + user.userId() + "] updated Resident " + resident.residentId()
                            + " (" + updated.fullName() + ", " + updated.ageCategory() + ")");
        } else {
            Theme.error("The resident was NOT updated. No changes were made.");
        }
    }

    // [5] Ask for the ID, show the resident, confirm, then delete. Entering 0 cancels.
    private void removeResident() {
        Theme.heading("Remove Resident");

        Resident resident = readExistingResident("Resident ID to remove (0 to cancel): ");
        if (resident == null) {
            Theme.info("Cancelled.");
            return;
        }
        showResidentCard(resident);

        Theme.warn("This permanently removes " + resident.fullName() + " from the database.");
        if (!InputHelper.readYesNo(scanner, "Are you sure?")) {
            Theme.info("Removal cancelled. No changes were made.");
            return;
        }

        if (!residentDao.delete(resident.residentId())) {
            Theme.error("The resident was NOT removed. No changes were made.");
            return;
        }

        Theme.success(resident.fullName() + " was removed.");
        activityLogDao.logAction(user.userId(), ActivityLog.RESIDENT_REMOVED,
                "Admin [" + user.userId() + "] removed " + resident.fullName()
                        + " of Unit " + resident.unitNumber());

        // The last resident leaving makes the unit Vacant.
        Unit unit = unitDao.findByUnitNumber(resident.unitNumber());
        if (unit != null && residentDao.countByUnit(unit.unitNumber()) == 0
                && unit.occupancyStatus().equals(Unit.OCCUPIED)
                && unitDao.update(unit.withOccupancy(Unit.VACANT))) {
            Theme.info("Unit " + unit.unitNumber() + " has no residents left, so it is now Vacant.");
        }
    }

    // ------------------------------------------------------------- helpers

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

    // Keeps asking for a resident ID until the resident exists. Returns null if the user types 0.
    private Resident readExistingResident(String prompt) {
        List<Resident> residents = residentDao.getAllResidents();

        if (residents.isEmpty()) {
            Theme.info("There are no registered residents yet.");
            return null;
        }
        printResidentTable(residents);

        while (true) {
            int residentId = InputHelper.readNonNegativeInt(scanner, prompt);

            if (residentId == 0) {
                return null;
            }
            for (Resident resident : residents) {
                if (resident.residentId() == residentId) {
                    return resident;
                }
            }
            Theme.error("Resident ID " + residentId + " does not exist.");
        }
    }

    // Asks Adult or Minor. If "current" is given, pressing Enter keeps it; otherwise an answer is required.
    private String readAgeCategory(String label, String current) {
        String hint = current == null ? "" : "  (Enter keeps " + current + ")";

        while (true) {
            System.out.print(label + " [1] Adult  [2] Minor" + hint + ": ");
            String text = scanner.nextLine().trim();

            if (text.isEmpty() && current != null) {
                return current;
            }
            if (text.equals("1") || text.equalsIgnoreCase(Resident.ADULT)) {
                return Resident.ADULT;
            }
            if (text.equals("2") || text.equalsIgnoreCase(Resident.MINOR)) {
                return Resident.MINOR;
            }
            Theme.error("Please type 1 for Adult or 2 for Minor.");
        }
    }

    private void showResidentCard(Resident resident) {
        Theme.box("RESIDENT " + resident.residentId(), Theme.MAGENTA, List.of(
                "Name     : " + resident.fullName(),
                "Category : " + resident.ageCategory(),
                "Unit     : " + resident.unitNumber()));
    }

    private static void printResidentTable(List<Resident> residents) {
        List<String[]> rows = new ArrayList<>();
        for (Resident resident : residents) {
            rows.add(new String[]{
                    String.valueOf(resident.residentId()),
                    resident.fullName(),
                    resident.ageCategory(),
                    String.valueOf(resident.unitNumber())
            });
        }
        Theme.table(new String[]{"ID", "Name", "Category", "Unit"}, rows, "resident");
    }
}
