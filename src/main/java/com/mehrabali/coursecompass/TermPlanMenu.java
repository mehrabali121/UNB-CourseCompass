
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Student-facing console menu for unofficial term planning.
 *
 * Plans are saved locally. They are not registrations and do
 * not establish course eligibility or term availability.
 */
public final class TermPlanMenu {

    private final Scanner scanner;
    private final ProfileRepository profiles;
    private final CourseRepository courses;
    private final TermPlanRepository plans;

    public TermPlanMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profiles = new ProfileRepository();
        this.courses = new CourseRepository();
        this.plans = new TermPlanRepository();
    }

    /**
     * Runs the term-planning menu using the shared scanner.
     */
    public void run() {

        boolean running = true;

        while (running) {

            printMenu();

            String choice = readLine("Choice (0-6): ");

            try {

                switch (choice) {

                    case "1" -> createPlan();
                    case "2" -> listPlans();
                    case "3" -> viewPlan();
                    case "4" -> addCourse();
                    case "5" -> removeCourse();
                    case "6" -> deletePlan();
                    case "0" -> running = false;

                    default -> System.out.println(
                            "Invalid choice. Enter 0 through 6."
                    );
                }

            } catch (SQLException | IOException exception) {

                System.out.println(
                        "Database operation failed: "
                        + exception.getMessage()
                );

            } catch (IllegalArgumentException exception) {

                System.out.println(
                        "Invalid request: "
                        + exception.getMessage()
                );
            }
        }
    }

    private static void printMenu() {

        System.out.println();
        System.out.println("========== MY TERM PLANS ==========");
        System.out.println("1. Create a Term Plan");
        System.out.println("2. List My Term Plans");
        System.out.println("3. View a Term Plan");
        System.out.println("4. Add a Course to a Plan");
        System.out.println("5. Remove a Course from a Plan");
        System.out.println("6. Delete a Term Plan");
        System.out.println("0. Back to Main Menu");
        System.out.println("===================================");
    }

    /**
     * Allows a student to create a named plan.
     */
    private void createPlan()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        String year = selectYear(profile);

        if (year == null) {
            return;
        }

        String name = readLine(
                "Plan name (0 to cancel): "
        );

        if (name.equals("0")) {
            return;
        }

        if (name.isBlank()) {
            System.out.println(
                    "Plan name cannot be empty."
            );
            return;
        }

        String term = selectTerm();

        if (term == null) {
            return;
        }

        int planId = plans.createPlan(
                profile.id(),
                name,
                year,
                term
        );

        System.out.println();
        System.out.println(
                "Term plan created successfully."
        );
        System.out.println("New plan ID: " + planId);

        System.out.println(
                "This is a personal planning record, "
                + "not a registration."
        );
    }

    /**
     * Lists all plans owned by the selected student.
     */
    private void listPlans()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        List<TermPlanRepository.TermPlan> studentPlans =
                plans.findByProfile(profile.id());

        System.out.println();
        System.out.println(
                "----- PLANS FOR " + profile.name() + " -----"
        );

        if (studentPlans.isEmpty()) {

            System.out.println(
                    "This student has no saved term plans."
            );
            return;
        }

        for (TermPlanRepository.TermPlan plan
                : studentPlans) {

            printPlanSummary(plan);
        }
    }

    /**
     * Shows the contents and total credits of a plan.
     */
    private void viewPlan()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        TermPlanRepository.TermPlan plan =
                selectPlan(profile.id());

        if (plan == null) {
            return;
        }

        printPlanDetails(profile.id(), plan);
    }

    /**
     * Adds a catalogue course to an owned plan.
     */
    private void addCourse()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        TermPlanRepository.TermPlan plan =
                selectPlan(profile.id());

        if (plan == null) {
            return;
        }

        System.out.println();
        System.out.println(
                "Adding a course to: " + plan.planName()
        );

        System.out.println(
                "Only courses in the locally loaded catalogue "
                + "for this campus and year can be added."
        );

        String code = readLine(
                "Course code (0 to cancel): "
        );

        if (code.equals("0")) {
            return;
        }

        if (code.isBlank()) {
            System.out.println(
                    "Course code cannot be empty."
            );
            return;
        }

        boolean added = plans.addCourse(
                profile.id(),
                plan.planId(),
                code
        );

        if (added) {

            System.out.println(
                    "Course added to the plan."
            );

        } else {

            System.out.println(
                    "That course is already in this plan."
            );
        }

        TermPlanRepository.TermPlan updated =
                plans.findPlan(
                        profile.id(),
                        plan.planId()
                );

        if (updated != null) {

            System.out.println(
                    "Planned credit hours: "
                    + updated.totalCredits()
            );
        }

        System.out.println(
                "This does not confirm prerequisites, "
                + "course offering, or enrollment."
        );
    }

    /**
     * Removes a specific entry from an owned plan.
     */
    private void removeCourse()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        TermPlanRepository.TermPlan plan =
                selectPlan(profile.id());

        if (plan == null) {
            return;
        }

        List<TermPlanRepository.PlannedCourse> entries =
                plans.findPlannedCourses(
                        profile.id(),
                        plan.planId()
                );

        if (entries.isEmpty()) {

            System.out.println(
                    "This plan contains no courses."
            );
            return;
        }

        printCourseEntries(entries);

        Integer entryId = readNumber(
                "Planned-course entry ID (0 to cancel): "
        );

        if (entryId == null || entryId == 0) {
            return;
        }

        boolean belongsToPlan = entries.stream()
                .anyMatch(entry ->
                        entry.plannedCourseId() == entryId
                );

        if (!belongsToPlan) {

            System.out.println(
                    "Choose an entry ID from this plan."
            );
            return;
        }

        boolean removed = plans.removeCourse(
                profile.id(),
                plan.planId(),
                entryId
        );

        System.out.println(
                removed
                        ? "Course removed from the plan."
                        : "Course entry was not found."
        );

        if (removed) {

            System.out.println(
                    "Remaining planned credit hours: "
                    + plans.getTotalCredits(
                            profile.id(),
                            plan.planId()
                    )
            );
        }
    }

    /**
     * Requires explicit confirmation before deleting a plan.
     */
    private void deletePlan()
            throws SQLException, IOException {

        ProfileRepository.Profile profile = selectProfile();

        if (profile == null) {
            return;
        }

        TermPlanRepository.TermPlan plan =
                selectPlan(profile.id());

        if (plan == null) {
            return;
        }

        System.out.println();
        System.out.println(
                "Plan to delete: " + plan.planName()
        );

        System.out.println(
                "This will permanently delete the plan "
                + "and its planned-course entries."
        );

        System.out.println(
                "The student's recorded completed courses "
                + "will not be deleted."
        );

        String confirmation = readLine(
                "Type DELETE to confirm: "
        );

        if (!confirmation.equals("DELETE")) {

            System.out.println("Deletion cancelled.");
            return;
        }

        boolean deleted = plans.deletePlan(
                profile.id(),
                plan.planId()
        );

        System.out.println(
                deleted
                        ? "Term plan deleted successfully."
                        : "Term plan was not found."
        );
    }

    /**
     * Selects an existing student by profile ID.
     */
    private ProfileRepository.Profile selectProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> allProfiles =
                profiles.findAll();

        System.out.println();
        System.out.println("----- SELECT STUDENT -----");

        if (allProfiles.isEmpty()) {

            System.out.println(
                    "No profiles exist. Create one using "
                    + "main menu option 1."
            );
            return null;
        }

        for (ProfileRepository.Profile profile
                : allProfiles) {

            System.out.println(
                    profile.id()
                    + ". " + profile.name()
                    + " | " + profile.campusName()
                    + " | Year: "
                    + optionalYear(profile.academicYear())
            );
        }

        while (true) {

            Integer id = readNumber(
                    "Profile ID (0 to cancel): "
            );

            if (id == null) {
                continue;
            }

            if (id == 0) {
                return null;
            }

            for (ProfileRepository.Profile profile
                    : allProfiles) {

                if (profile.id() == id) {
                    return profile;
                }
            }

            System.out.println(
                    "Choose a listed profile ID."
            );
        }
    }

    /**
     * Selects a plan only from the selected student's plans.
     */
    private TermPlanRepository.TermPlan selectPlan(
            int profileId
    ) throws SQLException, IOException {

        List<TermPlanRepository.TermPlan> studentPlans =
                plans.findByProfile(profileId);

        System.out.println();
        System.out.println("----- SELECT TERM PLAN -----");

        if (studentPlans.isEmpty()) {

            System.out.println(
                    "No saved plans for this student."
            );
            return null;
        }

        for (TermPlanRepository.TermPlan plan
                : studentPlans) {

            printPlanSummary(plan);
        }

        while (true) {

            Integer id = readNumber(
                    "Plan ID (0 to cancel): "
            );

            if (id == null) {
                continue;
            }

            if (id == 0) {
                return null;
            }

            for (TermPlanRepository.TermPlan plan
                    : studentPlans) {

                if (plan.planId() == id) {
                    return plan;
                }
            }

            System.out.println(
                    "Choose a plan ID from this student's list."
            );
        }
    }

    /**
     * Uses a saved catalogue year or asks for a loaded year.
     */
    private String selectYear(
            ProfileRepository.Profile profile
    ) throws SQLException, IOException {

        String savedYear = profile.academicYear();

        if (savedYear != null && !savedYear.isBlank()) {

            System.out.println(
                    "Using saved catalogue year: "
                    + savedYear
            );

            return savedYear;
        }

        List<String> years = courses.findAcademicYears();

        if (years.isEmpty()) {

            System.out.println(
                    "No academic catalogue years are loaded."
            );
            return null;
        }

        System.out.println();
        System.out.println(
                "Select a provisional catalogue year."
        );

        for (int i = 0; i < years.size(); i++) {

            System.out.println(
                    (i + 1) + ". " + years.get(i)
            );
        }

        while (true) {

            Integer choice = readNumber(
                    "Year choice (0 to cancel): "
            );

            if (choice == null) {
                continue;
            }

            if (choice == 0) {
                return null;
            }

            if (choice <= years.size()) {
                return years.get(choice - 1);
            }

            System.out.println(
                    "Choose a year from the list."
            );
        }
    }

    /**
     * Selects one of the four schema-supported terms.
     */
    private String selectTerm() {

        while (true) {

            System.out.println();
            System.out.println("1. Fall");
            System.out.println("2. Winter");
            System.out.println("3. Summer");
            System.out.println("4. Other");
            System.out.println("0. Cancel");

            String choice = readLine(
                    "Term choice (0-4): "
            );

            switch (choice) {

                case "1" -> {
                    return "Fall";
                }

                case "2" -> {
                    return "Winter";
                }

                case "3" -> {
                    return "Summer";
                }

                case "4" -> {
                    return "Other";
                }

                case "0" -> {
                    return null;
                }

                default -> System.out.println(
                        "Enter a number from 0 through 4."
                );
            }
        }
    }

    private static void printPlanSummary(
            TermPlanRepository.TermPlan plan
    ) {

        System.out.println();
        System.out.println(
                "Plan ID: " + plan.planId()
                + " | " + plan.planName()
        );

        System.out.println(
                plan.termName()
                + " | Catalogue: " + plan.academicYear()
                + " | Courses: " + plan.courseCount()
                + " | Credits: " + plan.totalCredits()
        );
    }

    private void printPlanDetails(
            int profileId,
            TermPlanRepository.TermPlan plan
    ) throws SQLException, IOException {

        System.out.println();
        System.out.println("----- TERM PLAN DETAILS -----");

        printPlanSummary(plan);

        System.out.println(
                "Created: " + plan.createdAt()
        );

        List<TermPlanRepository.PlannedCourse> entries =
                plans.findPlannedCourses(
                        profileId,
                        plan.planId()
                );

        if (entries.isEmpty()) {

            System.out.println(
                    "No courses added yet."
            );

        } else {

            printCourseEntries(entries);
        }

        System.out.println();
        System.out.println(
                "Total planned credit hours: "
                + plan.totalCredits()
        );

        System.out.println(
                "Important: This plan is unofficial. "
                + "Term offerings and registration eligibility "
                + "are not confirmed."
        );
    }

    private static void printCourseEntries(
            List<TermPlanRepository.PlannedCourse> entries
    ) {

        System.out.println();
        System.out.println("----- PLANNED COURSES -----");

        for (TermPlanRepository.PlannedCourse entry
                : entries) {

            System.out.println(
                    "Entry ID: " + entry.plannedCourseId()
                    + " | " + entry.courseCode()
                    + " - " + entry.courseTitle()
                    + " | Credits: " + entry.creditHours()
            );
        }
    }

    private Integer readNumber(String prompt) {

        String input = readLine(prompt);

        try {

            int number = Integer.parseInt(input);

            if (number >= 0) {
                return number;
            }

        } catch (NumberFormatException exception) {
            // Show a friendly message below.
        }

        System.out.println(
                "Enter a valid nonnegative whole number."
        );

        return null;
    }

    private String readLine(String prompt) {

        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String optionalYear(String year) {

        return year == null || year.isBlank()
                ? "Not selected"
                : year;
    }
}
