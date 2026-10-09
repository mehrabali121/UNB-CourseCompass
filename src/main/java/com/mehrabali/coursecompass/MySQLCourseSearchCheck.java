package com.mehrabali.coursecompass;

import java.util.List;

/** Read-only smoke test of the migrated MySQL course catalogue. */
public final class MySQLCourseSearchCheck {

    private MySQLCourseSearchCheck() {
    }

    public static void main(String[] args) {
        try {
            CourseRepository repository = CourseRepository.forMySQL();

            List<CourseRepository.Course> all =
                    repository.searchCourses("", "Fredericton", "2026-2027");
            System.out.println("MySQL Fredericton courses: " + all.size());
            for (CourseRepository.Course course : all) {
                System.out.println(course.code() + " | " + course.title()
                        + " | " + course.prerequisiteStatus());
            }

            CourseRepository.Course exact = repository.findByCode(
                    "cs 2043", "Fredericton", "2026-2027");
            List<CourseRepository.Course> security = repository.searchCourses(
                    "information security", null, null);
            List<String> years = repository.findAcademicYears();

            if (all.size() != 10
                    || exact == null
                    || !"CS2043".equals(exact.code())
                    || security.size() != 1
                    || !"CS2413".equals(security.get(0).code())
                    || !years.equals(List.of("2026-2027"))) {
                throw new IllegalStateException("MySQL search results did not match expectations.");
            }

            System.out.println("Exact lookup: " + exact.code());
            System.out.println("Title search: " + security.get(0).code());
            System.out.println("Academic years: " + years);
            System.out.println("MYSQL COURSE SEARCH CHECK PASSED (READ ONLY)");
        } catch (Exception exception) {
            System.err.println("MYSQL COURSE SEARCH CHECK FAILED: "
                    + exception.getMessage());
            System.exit(1);
        }
    }
}
