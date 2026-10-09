
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive console menu for hypothetical course completion.
 *
 * All simulations are advisory and read-only.
 * This menu never writes completed-course records.
 */
public final class WhatIfSimulationMenu {

    private final Scanner scanner;
    private final ProfileRepository profileRepository;
    private final CourseRepository courseRepository;
    private final WhatIfSimulationService simulationService;

    /**
     * Creates the What-If Planner menu with the application's
     * shared Scanner.
     */
    public WhatIfSimulationMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profileRepository = new ProfileRepository();
        this.courseRepository = new CourseRepository();
        this.simulationService = new WhatIfSimulationService();
    }

    /**
     * Runs the submenu until the user chooses to go back.
     */
    public void run() {

        boolean running = true;

        while (running) {

            printMenu();

            String choice = readLine("Choice (0-1): ");

            switch (choice) {

                case "1" -> {

                    try {
                        runSimulation();

                    } catch (SQLException | IOException exception) {

                        System.out.println();
                        System.out.println(
                                "Unable to run the simulation: "
                                + exception.getMessage()
                        );

                    } catch (IllegalArgumentException exception) {

                        System.out.println();
                        System.out.println(
                                "Invalid simulation request: "
                                + exception.getMessage()
                        );
                    }
                }

                case "0" -> running = false;

                default -> System.out.println(
                        "Invalid choice. Enter 0 or 1."
                );
            }
        }
    }

    /**
     * Displays the available What-If Planner actions.
     */
    private static void printMenu() {

        System.out.println();
        System.out.println("========== WHAT-IF PLANNER ==========");
        System.out.println("1. Simulate Completing Courses");
        System.out.println("0. Back to Main Menu");
        System.out.println("=====================================");
        System.out.println(
                "Simulations are hypothetical and do not "
                + "change saved academic records."
        );
    }

    /**
     * Guides the student through one simulation.
     */
    private void runSimulation()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        String academicYear = selectAcademicYear(profile);

        if (academicYear == null) {
            return;
        }

        System.out.println();
        System.out.println("----- HYPOTHETICAL COURSES -----");

        System.out.println(
                "Enter one or more courses you want to "
                + "pretend you have completed."
        );

        System.out.println(
                "Separate multiple course codes with commas."
        );

        System.out.println(
                "Example: CS1083, CS1203"
        );

        System.out.println(
                "Only courses in the locally loaded catalogue "
                + "for your campus and year are accepted."
        );

        System.out.println(
                "Courses already recorded as completed "
                + "cannot be added again."
        );

        String input = readLine(
                "Hypothetical course codes (0 to cancel): "
        );

        if (input.equals("0")) {

            System.out.println("Simulation cancelled.");
            return;
        }

        List<String> hypotheticalCourses =
                parseCourseCodes(input);

        if (hypotheticalCourses.isEmpty()) {

            System.out.println(
                    "Enter at least one course code."
            );
            return;
        }

        System.out.println();
        System.out.println("----- SIMULATION SUMMARY -----");

        System.out.println(
                "Student: " + profile.name()
        );

        System.out.println(
                "Campus: " + profile.campusName()
        );

        System.out.println(
                "Catalogue year: " + academicYear
        );

        System.out.println(
                "Hypothetical completions: "
                + String.join(", ", hypotheticalCourses)
        );

        System.out.println();
        System.out.println(
                "These courses will NOT be added to "
                + "your actual completed-course records."
        );

        String confirmation = readLine(
                "Run this simulation? (Y/N): "
        );

        if (!confirmation.equalsIgnoreCase("Y")
                && !confirmation.equalsIgnoreCase("YES")) {

            System.out.println("Simulation cancelled.");
            return;
        }

        WhatIfSimulationService.SimulationResult result =
                simulationService.simulate(
                        profile.id(),
                        academicYear,
                        hypotheticalCourses
                );

        WhatIfSimulationViewer.display(result);
    }

    /**
     * Lists and selects an existing student profile.
     */
    private ProfileRepository.Profile selectProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                profileRepository.findAll();

        System.out.println();
        System.out.println("----- SELECT STUDENT -----");

        if (profiles.isEmpty()) {

            System.out.println(
                    "No student profiles found. "
                    + "Create one from the main menu first."
            );
            return null;
        }

        for (ProfileRepository.Profile profile : profiles) {

            System.out.println(
                    profile.id()
                    + ". " + profile.name()
                    + " | " + profile.campusName()
                    + " | Catalogue: "
                    + displayOptional(profile.academicYear())
            );
        }

        while (true) {

            String input = readLine(
                    "Profile ID (0 to cancel): "
            );

            if (input.equals("0")) {
                return null;
            }

            Integer profileId = parsePositiveInteger(input);

            if (profileId == null) {

                System.out.println(
                        "Enter a valid positive profile ID."
                );
                continue;
            }

            for (ProfileRepository.Profile profile : profiles) {

                if (profile.id() == profileId) {
                    return profile;
                }
            }

            System.out.println(
                    "Choose a profile ID from the list."
            );
        }
    }

    /**
     * Uses the profile's saved year when available.
     *
     * If no year is saved, allows selecting one of the
     * locally loaded catalogue years.
     */
    private String selectAcademicYear(
            ProfileRepository.Profile profile
    ) throws SQLException, IOException {

        String savedYear = profile.academicYear();

        if (savedYear != null && !savedYear.isBlank()) {

            System.out.println();
            System.out.println(
                    "Using saved catalogue year: "
                    + savedYear
            );

            return savedYear;
        }

        List<String> years =
                courseRepository.findAcademicYears();

        if (years.isEmpty()) {

            System.out.println(
                    "No catalogue years are currently loaded."
            );
            return null;
        }

        System.out.println();
        System.out.println(
                "----- SELECT CATALOGUE YEAR -----"
        );

        System.out.println(
                "Your profile has no saved catalogue year."
        );

        System.out.println(
                "Choose a provisional year for this simulation."
        );

        for (int index = 0; index < years.size(); index++) {

            System.out.println(
                    (index + 1)
                    + ". " + years.get(index)
            );
        }

        while (true) {

            String input = readLine(
                    "Year choice (0 to cancel): "
            );

            if (input.equals("0")) {
                return null;
            }

            Integer choice = parsePositiveInteger(input);

            if (choice != null
                    && choice <= years.size()) {

                return years.get(choice - 1);
            }

            System.out.println(
                    "Choose a catalogue year from the list."
            );
        }
    }

    /**
     * Parses comma-separated course codes.
     *
     * Blank individual entries are rejected rather than
     * silently ignored.
     *
     * Duplicate detection and course validation are
     * performed by WhatIfSimulationService.
     */
    private static List<String> parseCourseCodes(
            String input
    ) {

        if (input == null || input.isBlank()) {
            return List.of();
        }

        String[] parts = input.split(",", -1);

        List<String> codes = new ArrayList<>();

        for (String part : parts) {

            String code = part.trim();

            if (code.isEmpty()) {

                throw new IllegalArgumentException(
                        "Course codes cannot contain empty "
                        + "comma-separated entries."
                );
            }

            codes.add(code);
        }

        return List.copyOf(codes);
    }

    /**
     * Parses strictly positive integers.
     */
    private static Integer parsePositiveInteger(
            String input
    ) {

        try {

            int value = Integer.parseInt(input);

            return value > 0 ? value : null;

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Reads one trimmed console input line.
     */
    private String readLine(String prompt) {

        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /**
     * Displays missing optional profile information clearly.
     */
    private static String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }
}
