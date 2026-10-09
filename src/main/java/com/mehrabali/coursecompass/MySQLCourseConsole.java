package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive MySQL-backed course catalogue, separate from the SQLite planner.
 * Does not initialize, seed, or modify database records.
 * Independent student project, not affiliated with or endorsed by UNB.
 */
public final class MySQLCourseConsole {
    private MySQLCourseConsole() {
    }

    public static void main(String[] args) {
        CourseRepository repository = CourseRepository.forMySQL();
        try (Scanner scanner = new Scanner(System.in)) {
            try {
                // Fail before presenting a menu if the connection is unavailable.
                List<String> years = repository.findAcademicYears();
                System.out.println("UNB CourseCompass - MySQL Course Catalogue");
                System.out.println("Independent project; not affiliated with UNB.");
                System.out.println("Coverage is limited. Verify details with official UNB sources.");
                System.out.println("Academic years: " + years);
            } catch (SQLException | IOException ex) {
                System.err.println("Cannot access MySQL catalogue: " + ex.getMessage());
                System.exit(1);
                return;
            }

            while (true) {
                System.out.println();
                System.out.println("1. Browse all available course records");
                System.out.println("2. Search course code or title");
                System.out.println("3. Find an exact course code");
                System.out.println("4. List represented academic years");
                System.out.println("0. Exit");
                System.out.print("Choice: ");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String choice = scanner.nextLine().trim();
                try {
                    switch (choice) {
                        case "1" -> display(repository.searchCourses("", null, null));
                        case "2" -> {
                            System.out.print("Search: ");
                            if (!scanner.hasNextLine()) return;
                            display(repository.searchCourses(scanner.nextLine(), null, null));
                        }
                        case "3" -> {
                            System.out.print("Course code (e.g. CS2043): ");
                            if (!scanner.hasNextLine()) return;
                            String code = scanner.nextLine();
                            System.out.print("Campus [Fredericton]: ");
                            if (!scanner.hasNextLine()) return;
                            String campus = scanner.nextLine().trim();
                            if (campus.isEmpty()) campus = "Fredericton";
                            System.out.print("Academic year [2026-2027]: ");
                            if (!scanner.hasNextLine()) return;
                            String year = scanner.nextLine().trim();
                            if (year.isEmpty()) year = "2026-2027";
                            CourseRepository.Course course = repository.findByCode(code, campus, year);
                            if (course == null) {
                                System.out.println("Not in this limited catalogue; check the official UNB calendar.");
                            } else {
                                display(List.of(course));
                            }
                        }
                        case "4" -> System.out.println("Represented years: " + repository.findAcademicYears());
                        case "0" -> {
                            System.out.println("Goodbye!");
                            return;
                        }
                        default -> System.out.println("Enter 0, 1, 2, 3, or 4.");
                    }
                } catch (SQLException | IOException ex) {
                    System.err.println("Catalogue query failed: " + ex.getMessage());
                }
            }
        }
    }

    private static void display(List<CourseRepository.Course> courses) {
        if (courses.isEmpty()) {
            System.out.println("No matching records in this limited catalogue.");
            return;
        }
        for (CourseRepository.Course course : courses) {
            System.out.printf("%s | %s | %s | %s | %s%n",
                    course.code(), course.title(), course.campusName(),
                    course.academicYear(), course.prerequisiteStatus());
            System.out.println("  Offering: " + course.offeringStatus()
                    + " | Source: " + course.sourceUrl()
                    + " | Verified: " + course.verifiedOn());
        }
        System.out.println("Matching catalogue records: " + courses.size());
    }
}
