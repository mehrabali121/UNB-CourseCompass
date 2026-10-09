
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive console menu for evaluating modeled prerequisites
 * against a student's self-reported completed-course records.
 *
 * Results are advisory and are not official eligibility decisions.
 */
public final class PrerequisiteEvaluationMenu {

    private final Scanner scanner;
    private final ProfileRepository profileRepository;
    private final CourseRepository courseRepository;
    private final PrerequisiteEvaluator evaluator;

    /**
     * Shares the main application's Scanner to avoid conflicting
     * readers on System.in.
     */
    public PrerequisiteEvaluationMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profileRepository = new ProfileRepository();
        this.courseRepository = new CourseRepository();
        this.evaluator = new PrerequisiteEvaluator();
    }

    /**
     * Displays the evaluation submenu.
     */
    public void run() {

        boolean running = true;

        while (running) {

            System.out.println();
            System.out.println(
                    "====== PREREQUISITE EVALUATION ======"
            );
            System.out.println(
                    "1. Evaluate Course for a Student"
            );
            System.out.println(
                    "0. Back to Main Menu"
            );
            System.out.println(
                    "====================================="
            );

            String choice = readLine(
                    "Enter your choice (0-1): "
            );

            try {

                switch (choice) {

                    case "1" -> evaluateForStudent();

                    case "0" -> running = false;

                    default -> System.out.println(
                            "Invalid choice. Enter 0 or 1."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println();
                System.out.println(
                        "Unable to evaluate prerequisites: "
                        + exception.getMessage()
                );

            } catch (IllegalArgumentException exception) {

                System.out.println();
                System.out.println(
                        "Invalid evaluation request: "
                        + exception.getMessage()
                );
            }
        }
    }

    /**
     * Selects a saved student profile and evaluates one course.
     */
    private void evaluateForStudent()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        System.out.println();
        System.out.println(
                "----- EVALUATE COURSE REQUIREMENTS -----"
        );

        System.out.println(
                "Student: " + profile.name()
        );

        System.out.println(
                "Campus: " + profile.campusName()
        );

        System.out.println(
                "Recorded completions are self-reported."
        );

        String courseCode = readLine(
                "Target course code (0 to cancel): "
        );

        if ("0".equals(courseCode)) {
            return;
        }

        if (courseCode.isBlank()) {
            System.out.println(
                    "Course code cannot be empty."
            );
            return;
        }

        String year = selectAcademicYear(profile);

        if (year == null) {
            return;
        }

        PrerequisiteEvaluator.Evaluation evaluation =
                evaluator.evaluate(
                        profile.id(),
                        courseCode,
                        profile.campusName(),
                        year
                );

        PrerequisiteEvaluationViewer.display(evaluation);
    }

    /**
     * Selects an existing profile rather than trusting a
     * manually entered campus or student identity.
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
                    "No student profiles have been created."
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

            Integer selectedId = readNumber(
                    "Enter profile ID (0 to cancel): "
            );

            if (selectedId == null) {
                System.out.println(
                        "Enter a valid nonnegative number."
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
                    "Choose a profile ID from the list."
            );
        }
    }

    /**
     * Uses the profile's saved academic year when available.
     * Otherwise allows the student to select a loaded year.
     */
    private String selectAcademicYear(
            ProfileRepository.Profile profile
    ) throws SQLException, IOException {

        String savedYear = profile.academicYear();

        if (savedYear != null && !savedYear.isBlank()) {

            System.out.println(
                    "Using saved catalogue year: " + savedYear
            );

            return savedYear;
        }

        List<String> years =
                courseRepository.findAcademicYears();

        if (years.isEmpty()) {

            System.out.println(
                    "No catalogue years are loaded."
            );
            return null;
        }

        System.out.println();
        System.out.println(
                "This profile has no saved academic year."
        );

        System.out.println(
                "Choose a year provisionally for this comparison:"
        );

        for (int index = 0; index < years.size(); index++) {

            System.out.println(
                    (index + 1)
                    + ". " + years.get(index)
            );
        }

        while (true) {

            Integer choice = readNumber(
                    "Year choice (0 to cancel): "
            );

            if (choice == null) {

                System.out.println(
                        "Enter a valid nonnegative number."
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
     * Parses a nonnegative menu number.
     */
    private Integer readNumber(String prompt) {

        String input = readLine(prompt);

        try {

            int number = Integer.parseInt(input);

            return number >= 0 ? number : null;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Reads a line from the shared application scanner.
     */
    private String readLine(String prompt) {

        System.out.print(prompt);

        return scanner.nextLine().trim();
    }

    /**
     * Displays missing optional profile data clearly.
     */
    private String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }
}
