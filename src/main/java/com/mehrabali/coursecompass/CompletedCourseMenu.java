
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Console interface for managing self-reported completed courses.
 *
 * This class uses the existing profile and completed-course
 * repositories. It does not calculate registration eligibility.
 */
public final class CompletedCourseMenu {

    private final Scanner scanner;
    private final ProfileRepository profileRepository;
    private final CompletedCourseRepository completedRepository;
    private final CourseRepository courseRepository;

    /**
     * Creates a completed-course menu using the application's
     * existing console scanner and database.
     */
    public CompletedCourseMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profileRepository = new ProfileRepository();
        this.completedRepository = new CompletedCourseRepository();
        this.courseRepository = new CourseRepository();
    }

    /**
     * Runs the completed-course submenu.
     */
    public void run() {

        boolean running = true;

        while (running) {

            System.out.println();
            System.out.println("====== MY COMPLETED COURSES ======");
            System.out.println("1. Add Completed Course");
            System.out.println("2. View Completed Courses");
            System.out.println("3. Remove Completed Course");
            System.out.println("0. Back to Main Menu");
            System.out.println("==================================");

            String choice = readLine("Enter your choice (0-3): ");

            try {

                switch (choice) {

                    case "1" -> addCompletedCourse();

                    case "2" -> viewCompletedCourses();

                    case "3" -> removeCompletedCourse();

                    case "0" -> running = false;

                    default -> System.out.println(
                            "Invalid choice. Enter 0, 1, 2, or 3."
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
     * Lets a student select an existing profile.
     *
     * Profile creation remains in the Student Profiles menu.
     */
    private Integer selectProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                profileRepository.findAll();

        System.out.println();
        System.out.println("----- SELECT STUDENT PROFILE -----");

        if (profiles.isEmpty()) {

            System.out.println(
                    "No student profiles have been created yet."
            );

            System.out.println(
                    "Return to the main menu and choose "
                    + "1. Student Profiles to create a profile."
            );

            return null;
        }

        for (ProfileRepository.Profile profile : profiles) {

            System.out.println(
                    profile.id() + ". "
                    + profile.name()
                    + " | Campus: "
                    + profile.campusName()
                    + " | Year: "
                    + displayOptional(profile.academicYear())
            );
        }

        while (true) {

            String input = readLine(
                    "Enter profile ID (0 to cancel): "
            );

            Integer profileId = parseNonnegativeInteger(input);

            if (profileId == null) {
                System.out.println(
                        "Enter a valid nonnegative whole number."
                );
                continue;
            }

            if (profileId == 0) {
                return null;
            }

            for (ProfileRepository.Profile profile : profiles) {

                if (profile.id() == profileId) {
                    return profileId;
                }
            }

            System.out.println(
                    "Profile not found. Choose an ID from the list."
            );
        }
    }

    /**
     * Records a self-reported course completion.
     */
    private void addCompletedCourse()
            throws SQLException, IOException {

        Integer profileId = selectProfile();

        if (profileId == null) {
            return;
        }

        System.out.println();
        System.out.println("----- ADD COMPLETED COURSE -----");

        System.out.println(
                "This is a self-reported completion, not an "
                + "official academic transcript."
        );

        String code = readLine(
                "Course code (example: CS1083, 0 to cancel): "
        );

        if ("0".equals(code)) {
            return;
        }

        if (code.isBlank()) {
            System.out.println("Course code cannot be empty.");
            return;
        }

        List<String> years = courseRepository.findAcademicYears();

        if (years.isEmpty()) {

            System.out.println(
                    "There are no catalogue years available."
            );

            return;
        }

        System.out.println();
        System.out.println("Available catalogue years:");

        for (int index = 0; index < years.size(); index++) {

            System.out.println(
                    (index + 1) + ". " + years.get(index)
            );
        }

        Integer yearChoice = askListChoice(
                "Choose a year (0 to cancel): ",
                years.size()
        );

        if (yearChoice == null) {
            return;
        }

        String year = years.get(yearChoice - 1);

        String completedOn = readLine(
                "Completion date YYYY-MM-DD (optional): "
        );

        boolean added = completedRepository.addCompletedCourse(
                profileId,
                code,
                year,
                completedOn
        );

        if (added) {

            System.out.println();
            System.out.println(
                    "Completed course saved successfully."
            );

        } else {

            System.out.println();
            System.out.println(
                    "This course is already recorded as completed "
                    + "for the selected profile."
            );
        }

        System.out.println(
                "Minimum grades and official completion status "
                + "have not been verified."
        );
    }

    /**
     * Lists saved course completions for a profile.
     */
    private void viewCompletedCourses()
            throws SQLException, IOException {

        Integer profileId = selectProfile();

        if (profileId == null) {
            return;
        }

        printCompletedCourses(profileId);
    }

    /**
     * Prints completed courses for a specific student.
     */
    private List<CompletedCourseRepository.CompletedCourse>
            printCompletedCourses(int profileId)
            throws SQLException, IOException {

        List<CompletedCourseRepository.CompletedCourse> courses =
                completedRepository.findByProfile(profileId);

        System.out.println();
        System.out.println("----- SAVED COMPLETED COURSES -----");

        if (courses.isEmpty()) {

            System.out.println(
                    "No completed courses have been recorded "
                    + "for this profile."
            );

            return courses;
        }

        for (CompletedCourseRepository.CompletedCourse course
                : courses) {

            System.out.println();
            System.out.println(
                    "Completion ID: " + course.completionId()
            );

            System.out.println(
                    course.courseCode() + " - " + course.courseTitle()
            );

            System.out.println(
                    "Credits: " + course.creditHours()
                    + " | Catalogue year: " + course.academicYear()
            );

            System.out.println(
                    "Completion date: "
                    + displayOptional(course.completedOn())
            );
        }

        System.out.println();
        System.out.println(
                "Total completed-course records: " + courses.size()
        );

        System.out.println(
                "These records are self-reported, and "
                + "grades have not been verified."
        );

        return courses;
    }

    /**
     * Removes a completion record after confirmation.
     */
    private void removeCompletedCourse()
            throws SQLException, IOException {

        Integer profileId = selectProfile();

        if (profileId == null) {
            return;
        }

        List<CompletedCourseRepository.CompletedCourse> courses =
                printCompletedCourses(profileId);

        if (courses.isEmpty()) {
            return;
        }

        Integer completionId = askPositiveInteger(
                "Completion ID to remove (0 to cancel): "
        );

        if (completionId == null) {
            return;
        }

        boolean belongsToProfile = false;

        for (CompletedCourseRepository.CompletedCourse course
                : courses) {

            if (course.completionId() == completionId) {
                belongsToProfile = true;
                break;
            }
        }

        if (!belongsToProfile) {

            System.out.println(
                    "The selected completion ID is not "
                    + "in this profile's list."
            );

            return;
        }

        String confirmation = readLine(
                "Type REMOVE to confirm deletion: "
        );

        if (!"REMOVE".equals(confirmation)) {

            System.out.println("Removal cancelled.");
            return;
        }

        boolean removed =
                completedRepository.removeCompletedCourse(
                        profileId,
                        completionId
                );

        System.out.println(
                removed
                        ? "Completed course removed successfully."
                        : "Completion record was not found."
        );
    }

    /**
     * Asks the user to choose from a numbered list.
     */
    private Integer askListChoice(
            String prompt,
            int numberOfChoices
    ) {

        while (true) {

            String input = readLine(prompt);

            Integer choice = parseNonnegativeInteger(input);

            if (choice == null) {

                System.out.println(
                        "Enter a valid whole number."
                );

                continue;
            }

            if (choice == 0) {
                return null;
            }

            if (choice <= numberOfChoices) {
                return choice;
            }

            System.out.println(
                    "Choose a number from the displayed list."
            );
        }
    }

    /**
     * Reads a positive identifier or zero to cancel.
     */
    private Integer askPositiveInteger(String prompt) {

        while (true) {

            Integer value = parseNonnegativeInteger(
                    readLine(prompt)
            );

            if (value == null) {

                System.out.println(
                        "Enter a valid nonnegative whole number."
                );

                continue;
            }

            if (value == 0) {
                return null;
            }

            return value;
        }
    }

    /**
     * Parses nonnegative integers without crashing on bad input.
     */
    private Integer parseNonnegativeInteger(String input) {

        try {

            int number = Integer.parseInt(input);

            return number >= 0 ? number : null;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Reads one console input line.
     */
    private String readLine(String prompt) {

        System.out.print(prompt);

        return scanner.nextLine().trim();
    }

    /**
     * Displays optional values without exposing null.
     */
    private String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not recorded"
                : value;
    }
}
