package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Unified, read-only MySQL catalogue and student-profile dashboard.
 *
 * Keeps a single Scanner for the complete interactive session. It does not
 * initialize, seed, create, modify, or delete MySQL records. The separately
 * launched MySQLProfileConsole retains its own opt-in write mode.
 * Independent student project; not affiliated with or endorsed by UNB.
 */
public final class MySQLDashboardConsole {
    private MySQLDashboardConsole() { }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.out.println("Usage: MySQLDashboardConsole");
            return;
        }

        CourseRepository courses = CourseRepository.forMySQL();
        MySQLProfileRepository profiles = new MySQLProfileRepository();

        // Fail before entering the menu if either repository is unavailable.
        try {
            List<String> years = courses.findAcademicYears();
            profiles.findCampuses();
            System.out.println("UNB CourseCompass - Unified MySQL Dashboard");
            System.out.println("Independent student project; not affiliated with UNB.");
            System.out.println("READ-ONLY: this menu cannot change student or course data.");
            System.out.println("Limited catalogue coverage; verify with official UNB sources.");
            System.out.println("Academic years: " + years);
        } catch (SQLException | IOException error) {
            System.err.println("Cannot access MySQL: " + error.getMessage());
            return;
        }

        try (Scanner input = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n--- MySQL Dashboard (read only) ---");
                System.out.println("1. Browse course catalogue");
                System.out.println("2. Search course code or title");
                System.out.println("3. Find exact course code");
                System.out.println("4. List catalogue academic years");
                System.out.println("5. View student profile summaries");
                System.out.println("6. List campuses");
                System.out.println("0. Exit");
                String choice = ask(input, "Choice: ");
                if (choice == null || choice.equals("0")) {
                    System.out.println("Goodbye!");
                    return;
                }

                try {
                    switch (choice) {
                        case "1" -> displayCourses(courses.searchCourses("", null, null));
                        case "2" -> {
                            String search = ask(input, "Search: ");
                            if (search == null) return;
                            displayCourses(courses.searchCourses(search, null, null));
                        }
                        case "3" -> {
                            String code = ask(input, "Course code (e.g. CS2043): ");
                            if (code == null) return;
                            String campus = ask(input, "Campus [Fredericton]: ");
                            if (campus == null) return;
                            String year = ask(input, "Academic year [2026-2027]: ");
                            if (year == null) return;
                            if (campus.isEmpty()) campus = "Fredericton";
                            if (year.isEmpty()) year = "2026-2027";
                            CourseRepository.Course result = courses.findByCode(code, campus, year);
                            if (result == null) {
                                System.out.println("Not in this limited catalogue; consult the UNB calendar.");
                            } else {
                                displayCourses(List.of(result));
                            }
                        }
                        case "4" -> System.out.println("Represented years: " + courses.findAcademicYears());
                        case "5" -> displayProfiles(profiles);
                        case "6" -> {
                            for (MySQLProfileRepository.Campus campus : profiles.findCampuses()) {
                                System.out.println(campus.id() + ". " + campus.name());
                            }
                        }
                        default -> System.out.println("Enter a choice from 0 to 6.");
                    }
                } catch (SQLException | IOException error) {
                    System.err.println("Read-only query failed: " + error.getMessage());
                }
            }
        }
    }

    private static String ask(Scanner input, String prompt) {
        System.out.print(prompt);
        return input.hasNextLine() ? input.nextLine().trim() : null;
    }

    private static void displayProfiles(MySQLProfileRepository profiles) throws SQLException {
        List<MySQLProfileRepository.Profile> results = profiles.findAll();
        System.out.println("Profiles: " + results.size());
        for (MySQLProfileRepository.Profile profile : results) {
            System.out.println("ID " + profile.id()
                    + " | Campus: " + profile.campusName()
                    + " | Academic year: " + optional(profile.academicYear())
                    + " | Program: " + optional(profile.programName())
                    + " | Completed courses: " + profiles.countCompletedCourses(profile.id()));
        }
        System.out.println("Profile names are hidden in this diagnostic menu.");
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "Not selected" : value;
    }

    private static void displayCourses(List<CourseRepository.Course> results) {
        if (results.isEmpty()) {
            System.out.println("No matching records in this limited catalogue.");
            return;
        }
        for (CourseRepository.Course course : results) {
            System.out.printf("%s | %s | %s | %s | %s%n",
                    course.code(), course.title(), course.campusName(),
                    course.academicYear(), course.prerequisiteStatus());
            System.out.println("  Offering: " + course.offeringStatus()
                    + " | Source: " + course.sourceUrl()
                    + " | Verified: " + course.verifiedOn());
        }
        System.out.println("Matching catalogue records: " + results.size());
    }
}
