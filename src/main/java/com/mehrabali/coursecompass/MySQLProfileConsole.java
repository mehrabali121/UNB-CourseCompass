package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Separate MySQL student profile console.
 *
 * Default mode is read-only. To create a profile, launch with --allow-writes.
 * Edits to the migrated profile (ID 2) and all deletions are disabled here.
 * Existing SQLite application and tests are unchanged.
 */
public final class MySQLProfileConsole {
    private static final int PROTECTED_MIGRATED_PROFILE_ID = 2;

    private MySQLProfileConsole() { }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !"--allow-writes".equals(args[0]))) {
            System.out.println("Usage: MySQLProfileConsole [--allow-writes]");
            return;
        }
        boolean allowWrites = args.length == 1;
        MySQLProfileRepository reader = new MySQLProfileRepository();
        MySQLProfileWriter writer = new MySQLProfileWriter();

        try (Scanner input = new Scanner(System.in)) {
            // Validate credentials before entering the menu.
            List<MySQLProfileRepository.Campus> campuses = reader.findCampuses();
            System.out.println("UNB CourseCompass - MySQL Student Profiles");
            System.out.println("Independent project; not affiliated with UNB.");
            System.out.println(allowWrites
                    ? "WRITE MODE: creations and edits require confirmation."
                    : "READ-ONLY MODE: no records can be changed.");
            System.out.println("Profile deletion and editing migrated profile ID 2 are disabled.");

            while (true) {
                System.out.println("\n1. List profiles (IDs and academic details)");
                System.out.println("2. List campuses");
                System.out.println("3. Create profile" + (allowWrites ? "" : " [disabled]"));
                System.out.println("4. Edit newly-created profile" + (allowWrites ? "" : " [disabled]"));
                System.out.println("0. Exit");
                String option = ask(input, "Choice: ");
                if (option == null || "0".equals(option)) {
                    System.out.println("Goodbye!");
                    return;
                }
                try {
                    switch (option) {
                        case "1" -> showProfiles(reader);
                        case "2" -> showCampuses(campuses);
                        case "3" -> {
                            if (allowWrites) create(input, reader, writer);
                            else System.out.println("Read-only. Relaunch with --allow-writes to enable changes.");
                        }
                        case "4" -> {
                            if (allowWrites) edit(input, reader, writer);
                            else System.out.println("Read-only. Relaunch with --allow-writes to enable changes.");
                        }
                        default -> System.out.println("Choose 0, 1, 2, 3, or 4.");
                    }
                } catch (SQLException | IllegalArgumentException ex) {
                    System.out.println("Action failed: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            System.err.println("Cannot connect to the MySQL profile database: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void showProfiles(MySQLProfileRepository reader) throws SQLException {
        List<MySQLProfileRepository.Profile> profiles = reader.findAll();
        System.out.println("Profiles: " + profiles.size());
        for (MySQLProfileRepository.Profile profile : profiles) {
            System.out.println("ID " + profile.id() + " | Campus: " + profile.campusName()
                    + " | Academic year: " + optional(profile.academicYear())
                    + " | Program: " + optional(profile.programName())
                    + " | Completed courses: " + reader.countCompletedCourses(profile.id()));
        }
        System.out.println("Profile names are intentionally hidden from this diagnostic menu.");
    }

    private static void showCampuses(List<MySQLProfileRepository.Campus> campuses) {
        for (MySQLProfileRepository.Campus campus : campuses) {
            System.out.println(campus.id() + ". " + campus.name());
        }
    }

    private static void create(Scanner input, MySQLProfileRepository reader,
                               MySQLProfileWriter writer) throws SQLException {
        String name = ask(input, "Profile name (0 to cancel): ");
        if (name == null || "0".equals(name)) return;
        Integer campusId = chooseCampus(input, reader);
        if (campusId == null) return;
        String year = ask(input, "Academic year, e.g. 2026-2027 (blank for none): ");
        if (year == null) return;
        year = year.isBlank() ? null : year;
        if (!validYear(year)) {
            System.out.println("Academic year must be YYYY-YYYY with consecutive years.");
            return;
        }
        System.out.println("Program selection is unavailable until sourced programs are loaded.");
        if (!confirm(input, "Create this profile permanently? Type CREATE: ", "CREATE")) {
            System.out.println("Cancelled; no changes made.");
            return;
        }
        try (Connection connection = MySQLConnectionManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int newId = writer.createProfile(connection, name, campusId, null, year);
                connection.commit();
                System.out.println("Profile created. ID: " + newId);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private static void edit(Scanner input, MySQLProfileRepository reader,
                             MySQLProfileWriter writer) throws SQLException {
        showProfiles(reader);
        String rawId = ask(input, "Profile ID to edit (0 to cancel): ");
        if (rawId == null || "0".equals(rawId)) return;
        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException ex) {
            System.out.println("Enter a whole-number profile ID.");
            return;
        }
        if (id <= 0 || id == PROTECTED_MIGRATED_PROFILE_ID) {
            System.out.println("This profile ID is protected or invalid. No changes made.");
            return;
        }
        boolean exists = reader.findAll().stream().anyMatch(profile -> profile.id() == id);
        if (!exists) {
            System.out.println("Profile not found.");
            return;
        }
        String name = ask(input, "New profile name (0 to cancel): ");
        if (name == null || "0".equals(name)) return;
        Integer campusId = chooseCampus(input, reader);
        if (campusId == null) return;
        String year = ask(input, "New academic year (blank for none): ");
        if (year == null) return;
        year = year.isBlank() ? null : year;
        if (!validYear(year)) {
            System.out.println("Academic year must be YYYY-YYYY with consecutive years.");
            return;
        }
        System.out.println("Program will be cleared; select no program for now.");
        if (!confirm(input, "Permanently update profile " + id + "? Type UPDATE: ", "UPDATE")) {
            System.out.println("Cancelled; no changes made.");
            return;
        }
        try (Connection connection = MySQLConnectionManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean updated = writer.updateProfile(connection, id, name, campusId, null, year);
                if (!updated) {
                    connection.rollback();
                    System.out.println("No profile updated.");
                    return;
                }
                connection.commit();
                System.out.println("Profile updated.");
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private static Integer chooseCampus(Scanner input, MySQLProfileRepository reader)
            throws SQLException {
        List<MySQLProfileRepository.Campus> campuses = reader.findCampuses();
        showCampuses(campuses);
        String value = ask(input, "Campus ID (0 to cancel): ");
        if (value == null || "0".equals(value)) return null;
        try {
            int id = Integer.parseInt(value);
            if (campuses.stream().anyMatch(campus -> campus.id() == id)) return id;
        } catch (NumberFormatException ignored) {
            // Display the same validation message below.
        }
        System.out.println("Invalid campus ID.");
        return null;
    }

    private static boolean validYear(String value) {
        if (value == null) return true;
        if (!value.matches("\\d{4}-\\d{4}")) return false;
        int start = Integer.parseInt(value.substring(0, 4));
        int end = Integer.parseInt(value.substring(5));
        return end == start + 1;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "Not selected" : value;
    }

    private static boolean confirm(Scanner input, String prompt, String expected) {
        return expected.equals(ask(input, prompt));
    }

    private static String ask(Scanner input, String prompt) {
        System.out.print(prompt);
        return input.hasNextLine() ? input.nextLine().trim() : null;
    }
}
