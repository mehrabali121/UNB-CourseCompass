
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Console menu for exploring possible courses using a student's
 * saved profile and self-reported completed-course records.
 *
 * All exploration results are advisory, not official decisions
 * about eligibility, course offerings, or degree requirements.
 */
public final class CourseExplorationMenu {

    private final Scanner scanner;
    private final ProfileRepository profileRepository;
    private final CourseRepository courseRepository;
    private final CourseExplorationService explorationService;

    /**
     * Reuses the application's shared input scanner.
     */
    public CourseExplorationMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profileRepository = new ProfileRepository();
        this.courseRepository = new CourseRepository();
        this.explorationService = new CourseExplorationService();
    }

    /**
     * Runs the course exploration submenu.
     */
    public void run() {

        boolean running = true;

        while (running) {

            System.out.println();
            System.out.println(
                    "====== EXPLORE POSSIBLE COURSES ======"
            );
            System.out.println(
                    "1. Explore Courses for a Student"
            );
            System.out.println(
                    "0. Back to Main Menu"
            );
            System.out.println(
                    "======================================"
            );

            String choice = readLine(
                    "Enter your choice (0-1): "
            );

            try {

                switch (choice) {

                    case "1" -> exploreForStudent();

                    case "0" -> running = false;

                    default -> System.out.println(
                            "Invalid choice. Enter 0 or 1."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println();
                System.out.println(
                        "Unable to explore courses: "
                        + exception.getMessage()
                );

            } catch (IllegalArgumentException exception) {

                System.out.println();
                System.out.println(
                        "Invalid exploration request: "
                        + exception.getMessage()
                );
            }
        }
    }

    /**
     * Selects a student, determines their catalogue year,
     * and presents the exploration results.
     */
    private void exploreForStudent()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        String year = selectAcademicYear(profile);

        if (year == null) {
            return;
        }

        System.out.println();
        System.out.println(
                "Exploring the locally stored catalogue for:"
        );
        System.out.println("Student: " + profile.name());
        System.out.println("Campus: " + profile.campusName());
        System.out.println("Catalogue year: " + year);

        System.out.println();
        System.out.println(
                "This may not include every course offered by UNB."
        );

        CourseExplorationService.ExplorationResult result =
                explorationService.explore(
                        profile.id(),
                        year
                );

        CourseExplorationViewer.display(result);
    }

    /**
     * Selects a saved student profile by its exact ID.
     */
    private ProfileRepository.Profile selectProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                profileRepository.findAll();

        System.out.println();
        System.out.println(
                "----- SELECT STUDENT PROFILE -----"
        );

        if (profiles.isEmpty()) {

            System.out.println(
                    "No student profiles are saved."
            );

            System.out.println(
                    "Use main menu option 1 to create a profile."
            );

            return null;
        }

        for (ProfileRepository.Profile profile : profiles) {

            System.out.println(
                    profile.id()
                    + ". " + profile.name()
                    + " | " + profile.campusName()
                    + " | Year: "
                    + displayOptional(profile.academicYear())
            );
        }

        while (true) {

            Integer selectedId = readNonnegativeNumber(
                    "Enter profile ID (0 to cancel): "
            );

            if (selectedId == null) {

                System.out.println(
                        "Enter a valid nonnegative whole number."
                );
                continue;
            }

            if (selectedId == 0) {
                return null;
            }

            for (ProfileRepository.Profile profile : profiles) {

                if (profile.id() == selectedId) {
                    return profile;
                }
            }

            System.out.println(
                    "Choose a profile ID from the displayed list."
            );
        }
    }

    /**
     * Uses the profile's saved catalogue year where available.
     * Otherwise asks the user to select a locally loaded year.
     */
    private String selectAcademicYear(
            ProfileRepository.Profile profile
    ) throws SQLException, IOException {

        String savedYear = profile.academicYear();

        if (savedYear != null && !savedYear.isBlank()) {

            System.out.println();
            System.out.println(
                    "Using saved catalogue year: " + savedYear
            );

            return savedYear;
        }

        List<String> years =
                courseRepository.findAcademicYears();

        if (years.isEmpty()) {

            System.out.println();
            System.out.println(
                    "No academic catalogue years are loaded."
            );

            return null;
        }

        System.out.println();
        System.out.println(
                "This profile has no saved catalogue year."
        );
        System.out.println(
                "Choose a year provisionally for this comparison:"
        );

        for (int index = 0; index < years.size(); index++) {

            System.out.println(
                    (index + 1) + ". " + years.get(index)
            );
        }

        while (true) {

            Integer choice = readNonnegativeNumber(
                    "Year choice (0 to cancel): "
            );

            if (choice == null) {

                System.out.println(
                        "Enter a valid nonnegative whole number."
                );
                continue;
            }

            if (choice == 0) {
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
     * Reads a trimmed console line from the shared scanner.
     */
    private String readLine(String prompt) {

        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /**
     * Parses a nonnegative integer without terminating
     * the menu on invalid user input.
     */
    private Integer readNonnegativeNumber(String prompt) {

        String input = readLine(prompt);

        try {

            int number = Integer.parseInt(input);

            return number >= 0 ? number : null;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Displays a friendly placeholder for missing profile data.
     */
    private static String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }
}
