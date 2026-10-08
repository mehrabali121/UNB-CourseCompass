
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Main console application for UNB CourseCompass.
 *
 * This is an independent student project.
 * It is not affiliated with or endorsed by UNB.
 */
public final class Main {

    private static final Scanner SCANNER = new Scanner(System.in);

    private static final ProfileRepository PROFILE_REPOSITORY =
            new ProfileRepository();

    private Main() {
        // Prevent creating Main objects.
    }

    /**
     * Starts the application.
     */
    public static void main(String[] args) {

        try {
            DatabaseManager.initializeDatabase();
            PROFILE_REPOSITORY.initializeCampuses();
        } catch (SQLException | IOException exception) {
            System.err.println(
                    "Unable to initialize the application database."
            );
            System.err.println(exception.getMessage());
            return;
        }

        showWelcomeMessage();
        runMainMenu();
    }

    /**
     * Displays the application introduction and disclaimer.
     */
    private static void showWelcomeMessage() {

        System.out.println();
        System.out.println("====================================");
        System.out.println("           UNB COURSECOMPASS");
        System.out.println("     Unofficial Academic Planner");
        System.out.println("====================================");
        System.out.println();

        System.out.println(
                "This is an independent student project and is not "
                + "affiliated with or endorsed by the University "
                + "of New Brunswick."
        );

        System.out.println(
                "Academic requirements and course information "
                + "must be verified against official UNB sources."
        );
    }

    /**
     * Keeps displaying the main menu until the user exits.
     */
    private static void runMainMenu() {

        boolean running = true;

        while (running) {

            printMainMenu();

            String choice = readLine("Enter your choice (0-9): ");

            switch (choice) {

                case "1" -> runProfilesMenu();

                case "2" -> showComingSoon("Search Courses");

                case "3" -> showComingSoon(
                        "My Completed Courses"
                );

                case "4" -> showComingSoon(
                        "Check Prerequisites"
                );

                case "5" -> showComingSoon(
                        "Explore Possible Courses"
                );

                case "6" -> showComingSoon("My Term Plans");

                case "7" -> showComingSoon("What-If Planner");

                case "8" -> showComingSoon("Degree Progress");

                case "9" -> showComingSoon(
                        "Data Coverage and Sources"
                );

                case "0" -> {
                    System.out.println();
                    System.out.println(
                            "Thank you for using UNB CourseCompass!"
                    );
                    running = false;
                }

                default -> System.out.println(
                        "Invalid choice. Enter a number from 0 to 9."
                );
            }
        }
    }

    /**
     * Displays the main application menu.
     */
    private static void printMainMenu() {

        System.out.println();
        System.out.println("========== MAIN MENU ==========");
        System.out.println("1. Student Profiles");
        System.out.println("2. Search Courses");
        System.out.println("3. My Completed Courses");
        System.out.println("4. Check Prerequisites");
        System.out.println("5. Explore Possible Courses");
        System.out.println("6. My Term Plans");
        System.out.println("7. What-If Planner");
        System.out.println("8. Degree Progress");
        System.out.println("9. Data Coverage and Sources");
        System.out.println("0. Exit");
        System.out.println("===============================");
    }

    /**
     * Handles the Student Profiles feature.
     */
    private static void runProfilesMenu() {

        boolean insideProfiles = true;

        while (insideProfiles) {

            System.out.println();
            System.out.println("====== STUDENT PROFILES ======");
            System.out.println("1. Create a Profile");
            System.out.println("2. View All Profiles");
            System.out.println("3. Edit a Profile");
            System.out.println("4. Delete a Profile");
            System.out.println("0. Back to Main Menu");
            System.out.println("==============================");

            String choice = readLine("Enter your choice (0-4): ");

            try {

                switch (choice) {

                    case "1" -> createProfile();

                    case "2" -> viewProfiles();

                    case "3" -> editProfile();

                    case "4" -> deleteProfile();

                    case "0" -> insideProfiles = false;

                    default -> System.out.println(
                            "Invalid choice. Enter a number from 0 to 4."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println();
                System.out.println(
                        "Database operation failed: "
                        + exception.getMessage()
                );

            } catch (IllegalArgumentException exception) {

                System.out.println();
                System.out.println(
                        "Invalid input: " + exception.getMessage()
                );
            }
        }
    }

    /**
     * Creates and saves a new student profile.
     */
    private static void createProfile()
            throws SQLException, IOException {

        System.out.println();
        System.out.println("----- CREATE PROFILE -----");

        String name = readRequiredText("Profile name: ");

        if (name == null) {
            return;
        }

        Integer campusId = chooseCampus();

        if (campusId == null) {
            return;
        }

        String academicYear = readAcademicYear();

        Integer programId = chooseProgram(
                campusId,
                academicYear
        );

        int newProfileId = PROFILE_REPOSITORY.createProfile(
                name,
                campusId,
                programId,
                academicYear
        );

        System.out.println();
        System.out.println("Profile created successfully!");
        System.out.println("Profile ID: " + newProfileId);
    }

    /**
     * Displays all stored profiles.
     */
    private static void viewProfiles()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                PROFILE_REPOSITORY.findAll();

        System.out.println();
        System.out.println("----- SAVED PROFILES -----");

        if (profiles.isEmpty()) {
            System.out.println("No profiles have been created yet.");
            return;
        }

        for (ProfileRepository.Profile profile : profiles) {

            System.out.println();
            System.out.println("Profile ID: " + profile.id());
            System.out.println("Name: " + profile.name());
            System.out.println("Campus: " + profile.campusName());

            System.out.println(
                    "Academic year: "
                    + displayOptional(profile.academicYear())
            );

            System.out.println(
                    "Program: "
                    + displayOptional(profile.programName())
            );
        }

        System.out.println();
        System.out.println(
                "Total saved profiles: " + profiles.size()
        );
    }

    /**
     * Allows users to update an existing profile.
     */
    private static void editProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                PROFILE_REPOSITORY.findAll();

        if (profiles.isEmpty()) {
            System.out.println("No profiles are available to edit.");
            return;
        }

        viewProfiles();

        Integer profileId = readPositiveInteger(
                "Enter the profile ID to edit (0 to cancel): "
        );

        if (profileId == null || profileId == 0) {
            return;
        }

        ProfileRepository.Profile selectedProfile = null;

        for (ProfileRepository.Profile profile : profiles) {
            if (profile.id() == profileId) {
                selectedProfile = profile;
                break;
            }
        }

        if (selectedProfile == null) {
            System.out.println("Profile not found.");
            return;
        }

        System.out.println();
        System.out.println(
                "Editing profile: " + selectedProfile.name()
        );

        System.out.println(
                "Enter the updated information below."
        );

        String name = readRequiredText("New profile name: ");

        if (name == null) {
            return;
        }

        Integer campusId = chooseCampus();

        if (campusId == null) {
            return;
        }

        String academicYear = readAcademicYear();

        Integer programId = chooseProgram(
                campusId,
                academicYear
        );

        boolean updated = PROFILE_REPOSITORY.updateProfile(
                profileId,
                name,
                campusId,
                programId,
                academicYear
        );

        System.out.println(
                updated
                        ? "Profile updated successfully!"
                        : "Profile not found."
        );
    }

    /**
     * Deletes a profile only after user confirmation.
     */
    private static void deleteProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                PROFILE_REPOSITORY.findAll();

        if (profiles.isEmpty()) {
            System.out.println("No profiles are available to delete.");
            return;
        }

        viewProfiles();

        Integer profileId = readPositiveInteger(
                "Enter the profile ID to delete (0 to cancel): "
        );

        if (profileId == null || profileId == 0) {
            return;
        }

        ProfileRepository.Profile selectedProfile = null;

        for (ProfileRepository.Profile profile : profiles) {
            if (profile.id() == profileId) {
                selectedProfile = profile;
                break;
            }
        }

        if (selectedProfile == null) {
            System.out.println("Profile not found.");
            return;
        }

        System.out.println();
        System.out.println(
                "You are about to delete: "
                + selectedProfile.name()
        );

        System.out.println(
                "Deleting this profile also deletes its "
                + "saved course completions and term plans."
        );

        String confirmation = readLine(
                "Type DELETE to confirm, or anything else to cancel: "
        );

        if (!"DELETE".equals(confirmation)) {
            System.out.println("Deletion cancelled.");
            return;
        }

        boolean deleted =
                PROFILE_REPOSITORY.deleteProfile(profileId);

        System.out.println(
                deleted
                        ? "Profile deleted successfully."
                        : "Profile not found."
        );
    }

    /**
     * Displays available campuses and reads a selection.
     */
    private static Integer chooseCampus()
            throws SQLException, IOException {

        List<ProfileRepository.Campus> campuses =
                PROFILE_REPOSITORY.findCampuses();

        System.out.println();
        System.out.println("Available campuses:");

        for (ProfileRepository.Campus campus : campuses) {
            System.out.println(
                    campus.id() + ". " + campus.name()
            );
        }

        while (true) {

            Integer campusId = readPositiveInteger(
                    "Campus ID (0 to cancel): "
            );

            if (campusId == null || campusId == 0) {
                return null;
            }

            for (ProfileRepository.Campus campus : campuses) {
                if (campus.id() == campusId) {
                    return campusId;
                }
            }

            System.out.println(
                    "Invalid campus. Choose one from the list."
            );
        }
    }

    /**
     * Reads an optional academic catalogue year.
     */
    private static String readAcademicYear() {

        System.out.println();
        System.out.println(
                "Enter the catalogue year you want to plan against."
        );

        System.out.println(
                "Example: 2026-2027"
        );

        System.out.println(
                "Leave blank if you do not know your catalogue year."
        );

        while (true) {

            String academicYear = readLine(
                    "Academic year (optional): "
            );

            if (academicYear.isBlank()) {
                return null;
            }

            if (academicYear.matches("\\d{4}-\\d{4}")) {

                int startingYear = Integer.parseInt(
                        academicYear.substring(0, 4)
                );

                int endingYear = Integer.parseInt(
                        academicYear.substring(5)
                );

                if (endingYear == startingYear + 1) {
                    return academicYear;
                }
            }

            System.out.println(
                    "Use the format YYYY-YYYY with consecutive years."
            );
        }
    }

    /**
     * Allows selection only from programs with source records.
     *
     * When none are available, the profile can be saved
     * without a program.
     */
    private static Integer chooseProgram(
            int campusId,
            String academicYear
    ) throws SQLException, IOException {

        if (academicYear == null) {

            System.out.println();
            System.out.println(
                    "Program selection skipped because no "
                    + "catalogue year was provided."
            );

            return null;
        }

        List<ProfileRepository.Program> programs =
                PROFILE_REPOSITORY.findPrograms(
                        campusId,
                        academicYear
                );

        if (programs.isEmpty()) {

            System.out.println();
            System.out.println(
                    "No sourced programs are available for "
                    + "this campus and catalogue year yet."
            );

            System.out.println(
                    "Your profile will be saved without "
                    + "a selected program."
            );

            return null;
        }

        System.out.println();
        System.out.println("Available programs:");

        for (ProfileRepository.Program program : programs) {

            System.out.println(
                    program.id() + ". " + program.name()
                    + " [Degree audit: "
                    + program.auditStatus() + "]"
            );
        }

        while (true) {

            Integer programId = readPositiveInteger(
                    "Program ID (0 to skip): "
            );

            if (programId == null || programId == 0) {
                return null;
            }

            for (ProfileRepository.Program program : programs) {
                if (program.id() == programId) {
                    return programId;
                }
            }

            System.out.println(
                    "Invalid program. Choose one from the list."
            );
        }
    }

    /**
     * Reads a required nonempty string.
     */
    private static String readRequiredText(String prompt) {

        while (true) {

            String value = readLine(prompt);

            if ("0".equals(value)) {
                System.out.println("Operation cancelled.");
                return null;
            }

            if (!value.isBlank()) {
                return value;
            }

            System.out.println(
                    "This field cannot be empty. "
                    + "Enter 0 to cancel."
            );
        }
    }

    /**
     * Reads a nonnegative integer.
     */
    private static Integer readPositiveInteger(String prompt) {

        while (true) {

            String input = readLine(prompt);

            try {

                int value = Integer.parseInt(input);

                if (value >= 0) {
                    return value;
                }

            } catch (NumberFormatException exception) {
                // Show the normal validation message below.
            }

            System.out.println(
                    "Please enter a valid nonnegative whole number."
            );
        }
    }

    /**
     * Reads a trimmed line from the console.
     */
    private static String readLine(String prompt) {

        System.out.print(prompt);

        return SCANNER.nextLine().trim();
    }

    /**
     * Displays a friendly value for missing optional data.
     */
    private static String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }

    /**
     * Placeholder for features not implemented yet.
     */
    private static void showComingSoon(String featureName) {

        System.out.println();
        System.out.println("----- " + featureName + " -----");
        System.out.println(
                "This feature is not available yet."
        );
    }
}
