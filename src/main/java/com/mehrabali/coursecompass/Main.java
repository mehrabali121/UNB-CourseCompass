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

    private static final CourseRepository COURSE_REPOSITORY =
            new CourseRepository();

    private static final PrerequisiteRepository PREREQUISITE_REPOSITORY =
            new PrerequisiteRepository();

    private static final CompletedCourseMenu COMPLETED_COURSE_MENU =
            new CompletedCourseMenu(SCANNER);

    private static final PrerequisiteEvaluationMenu EVALUATION_MENU =
            new PrerequisiteEvaluationMenu(SCANNER);

    private static final CourseExplorationMenu EXPLORATION_MENU =
            new CourseExplorationMenu(SCANNER);

    private static final TermPlanMenu TERM_PLAN_MENU =
            new TermPlanMenu(SCANNER);

    private static final WhatIfSimulationMenu WHAT_IF_MENU =
            new WhatIfSimulationMenu(SCANNER);

    private Main() {
        // Prevent creating Main objects.
    }

    /**
     * Starts the application and initializes its data.
     */
    public static void main(String[] args) {

        try {
            DatabaseManager.initializeDatabase();
            PROFILE_REPOSITORY.initializeCampuses();
            CourseSeedImporter.importCourses();
            PrerequisiteSeedImporter.importPrerequisites();

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

                case "2" -> runCourseSearchMenu();

                case "3" -> COMPLETED_COURSE_MENU.run();

                case "4" -> runPrerequisiteMenu();

                case "5" -> EXPLORATION_MENU.run();

                case "6" -> TERM_PLAN_MENU.run();

                case "7" -> WHAT_IF_MENU.run();

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
     * Handles course catalogue searching and browsing.
     */
    private static void runCourseSearchMenu() {

        boolean insideSearch = true;

        while (insideSearch) {

            System.out.println();
            System.out.println("======= SEARCH COURSES =======");
            System.out.println("1. Search by Course Code or Title");
            System.out.println("2. Find Exact Course");
            System.out.println("3. Browse Available Courses");
            System.out.println("4. View Catalogue Years");
            System.out.println("0. Back to Main Menu");
            System.out.println("==============================");

            String choice = readLine("Enter your choice (0-4): ");

            try {

                switch (choice) {

                    case "1" -> searchCourses();

                    case "2" -> findExactCourse();

                    case "3" -> browseCourses();

                    case "4" -> viewCatalogueYears();

                    case "0" -> insideSearch = false;

                    default -> System.out.println(
                            "Invalid choice. Enter a number from 0 to 4."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println();
                System.out.println(
                        "Unable to access the course catalogue: "
                        + exception.getMessage()
                );
            }
        }
    }

    /**
     * Searches the catalogue by partial course code or title.
     */
    private static void searchCourses()
            throws SQLException, IOException {

        System.out.println();
        System.out.println("----- SEARCH COURSES -----");

        System.out.println(
                "Enter part of a code or title, such as "
                + "CS2263 or Security."
        );

        String searchText = readLine("Search text: ");

        String campus = chooseCourseCampus();
        String academicYear = chooseCourseAcademicYear();

        List<CourseRepository.Course> courses =
                COURSE_REPOSITORY.searchCourses(
                        searchText,
                        campus,
                        academicYear
                );

        printCourseResults(courses);
    }

    /**
     * Retrieves one course by exact code, campus and year.
     */
    private static void findExactCourse()
            throws SQLException, IOException {

        System.out.println();
        System.out.println("----- FIND EXACT COURSE -----");

        String code = readLine(
                "Course code (example: CS 2263): "
        );

        if (code.isBlank()) {
            System.out.println("Course code cannot be empty.");
            return;
        }

        String campus = chooseCourseCampus();

        if (campus == null) {
            System.out.println(
                    "Choose a specific campus for exact lookup."
            );
            return;
        }

        String year = chooseCourseAcademicYear();

        if (year == null) {
            System.out.println(
                    "Choose a specific academic year for exact lookup."
            );
            return;
        }

        CourseRepository.Course course =
                COURSE_REPOSITORY.findByCode(
                        code,
                        campus,
                        year
                );

        if (course == null) {

            System.out.println();
            System.out.println(
                    "No matching course record was found."
            );

            System.out.println(
                    "This does not mean the course does not exist "
                    + "at UNB. Our catalogue currently has "
                    + "limited coverage."
            );

            return;
        }

        printCourseDetails(course);
    }

    /**
     * Lists the courses covered by the local catalogue.
     */
    private static void browseCourses()
            throws SQLException, IOException {

        System.out.println();
        System.out.println("----- BROWSE COURSES -----");

        String campus = chooseCourseCampus();
        String academicYear = chooseCourseAcademicYear();

        List<CourseRepository.Course> courses =
                COURSE_REPOSITORY.searchCourses(
                        "",
                        campus,
                        academicYear
                );

        printCourseResults(courses);
    }

    /**
     * Displays academic years represented in SQLite.
     */
    private static void viewCatalogueYears()
            throws SQLException, IOException {

        List<String> years =
                COURSE_REPOSITORY.findAcademicYears();

        System.out.println();
        System.out.println("----- CATALOGUE YEARS -----");

        if (years.isEmpty()) {
            System.out.println(
                    "No academic catalogue years are loaded."
            );
            return;
        }

        for (String year : years) {
            System.out.println("- " + year);
        }

        System.out.println();
        System.out.println(
                "Catalogue coverage is limited. A listed year "
                + "does not imply all UNB courses are included."
        );
    }

    /**
     * Asks for an optional campus filter.
     */
    private static String chooseCourseCampus() {

        while (true) {

            System.out.println();
            System.out.println("Campus filter:");
            System.out.println("1. Fredericton");
            System.out.println("2. Saint John");
            System.out.println("0. All available campuses");

            String choice = readLine("Campus choice (0-2): ");

            switch (choice) {

                case "1" -> {
                    return "Fredericton";
                }

                case "2" -> {
                    return "Saint John";
                }

                case "0" -> {
                    return null;
                }

                default -> System.out.println(
                        "Invalid choice. Enter 0, 1, or 2."
                );
            }
        }
    }

    /**
     * Asks for a catalogue year or permits all loaded years.
     */
    private static String chooseCourseAcademicYear()
            throws SQLException, IOException {

        List<String> years =
                COURSE_REPOSITORY.findAcademicYears();

        if (years.isEmpty()) {
            System.out.println(
                    "No catalogue years are available."
            );
            return null;
        }

        while (true) {

            System.out.println();
            System.out.println("Available academic years:");

            for (int index = 0; index < years.size(); index++) {
                System.out.println(
                        (index + 1) + ". " + years.get(index)
                );
            }

            System.out.println("0. All available years");

            Integer choice = readPositiveInteger(
                    "Academic year choice: "
            );

            if (choice == null || choice == 0) {
                return null;
            }

            if (choice <= years.size()) {
                return years.get(choice - 1);
            }

            System.out.println(
                    "Choose a year from the displayed list."
            );
        }
    }

    /**
     * Displays a course result list with catalogue metadata.
     */
    private static void printCourseResults(
            List<CourseRepository.Course> courses
    ) {

        System.out.println();
        System.out.println("----- COURSE RESULTS -----");

        if (courses.isEmpty()) {

            System.out.println(
                    "No matching courses in the local catalogue."
            );

            System.out.println(
                    "This does not mean the course is unavailable "
                    + "at UNB. Try different filters or consult "
                    + "the official academic calendar."
            );

            return;
        }

        for (CourseRepository.Course course : courses) {

            System.out.println();
            System.out.println(
                    course.code() + " | " + course.title()
            );

            System.out.println(
                    "Credits: " + course.creditHours()
                    + " | Campus: " + course.campusName()
                    + " | Year: " + course.academicYear()
            );
        }

        System.out.println();
        System.out.println(
                "Matching catalogue records: " + courses.size()
        );

        System.out.println(
                "Use Find Exact Course to view source information "
                + "and academic status details."
        );
    }

    /**
     * Displays the complete stored information for one course.
     */
    private static void printCourseDetails(
            CourseRepository.Course course
    ) {

        System.out.println();
        System.out.println("----- COURSE DETAILS -----");

        System.out.println("Code: " + course.code());
        System.out.println("Title: " + course.title());
        System.out.println(
                "Credit hours: " + course.creditHours()
        );
        System.out.println(
                "Campus: " + course.campusName()
        );
        System.out.println(
                "Academic year: " + course.academicYear()
        );
        System.out.println(
                "Prerequisite information status: "
                + course.prerequisiteStatus()
        );
        System.out.println(
                "Offering status: " + course.offeringStatus()
        );

        System.out.println();
        System.out.println("Academic source:");
        System.out.println(course.sourceTitle());
        System.out.println(course.sourceUrl());

        System.out.println(
                "Source verified on: " + course.verifiedOn()
        );

        System.out.println();
        System.out.println(
                "UNKNOWN means information has not yet been "
                + "verified or implemented in CourseCompass."
        );

        System.out.println(
                "Check the official UNB calendar and registration "
                + "system before making academic decisions."
        );
    }

    /**
     * Provides both academic prerequisite lookup and
     * profile-based advisory evaluation.
     */
    private static void runPrerequisiteMenu() {

        boolean insidePrerequisites = true;

        while (insidePrerequisites) {

            System.out.println();
            System.out.println("======= CHECK PREREQUISITES =======");
            System.out.println("1. View Course Prerequisites");
            System.out.println("2. Evaluate for a Student");
            System.out.println("0. Back to Main Menu");
            System.out.println("===================================");

            String choice = readLine("Enter your choice (0-2): ");

            try {

                switch (choice) {

                    case "1" -> viewCoursePrerequisites();

                    case "2" -> EVALUATION_MENU.run();

                    case "0" -> insidePrerequisites = false;

                    default -> System.out.println(
                            "Invalid choice. Enter 0, 1, or 2."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println();
                System.out.println(
                        "Unable to load prerequisite information: "
                        + exception.getMessage()
                );
            }
        }
    }

    /**
     * Retrieves and displays prerequisite details.
     */
    private static void viewCoursePrerequisites()
            throws SQLException, IOException {

        System.out.println();
        System.out.println("----- VIEW COURSE PREREQUISITES -----");

        System.out.println(
                "This feature displays stored course requirements."
        );

        System.out.println(
                "It does not confirm your registration eligibility."
        );

        String code = readLine(
                "Enter course code (example: CS2413): "
        );

        if (code.isBlank()) {
            System.out.println(
                    "Course code cannot be empty."
            );
            return;
        }

        String campus = chooseCourseCampus();

        if (campus == null) {
            System.out.println(
                    "Please select a specific campus for "
                    + "prerequisite lookup."
            );
            return;
        }

        String academicYear = chooseCourseAcademicYear();

        if (academicYear == null) {
            System.out.println(
                    "Please select a specific academic year for "
                    + "prerequisite lookup."
            );
            return;
        }

        PrerequisiteRepository.PrerequisiteInfo info =
                PREREQUISITE_REPOSITORY.findPrerequisites(
                        code,
                        campus,
                        academicYear
                );

        PrerequisiteViewer.display(info);
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

        System.out.println("Example: 2026-2027");

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
