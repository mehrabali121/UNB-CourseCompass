
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the course search repository.
 *
 * Each test receives a temporary SQLite database populated
 * with the bundled course seed.
 *
 * The real application database is never accessed.
 */
public class CourseRepositoryTest {

    private static final String ACADEMIC_YEAR = "2026-2027";

    private static final String SOURCE_URL =
            "https://www.unb.ca/academics/calendar/"
            + "undergraduate/current/frederictoncourses/"
            + "computer-science/index.html";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private CourseRepository repository;

    /**
     * Builds an isolated catalogue database before each test.
     */
    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve("catalogue.db");

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");

        repository = new CourseRepository(databasePath);
    }

    /**
     * Executes a bundled SQL resource on the test database.
     */
    private void executeSqlResource(String resourcePath)
            throws Exception {

        String content;

        try (InputStream input =
                getClass().getResourceAsStream(resourcePath)) {

            assertNotNull(
                    input,
                    "Missing SQL resource: " + resourcePath
            );

            content = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        StringBuilder cleaned = new StringBuilder();

        for (String line : content.split("\\R")) {

            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            connection.setAutoCommit(false);

            try {

                try (Statement statement =
                        connection.createStatement()) {

                    for (String part :
                            cleaned.toString().split(";")) {

                        String sql = part.trim();

                        if (!sql.isEmpty()) {
                            statement.execute(sql);
                        }
                    }
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void blankSearchListsAllImportedCourses()
            throws Exception {

        List<CourseRepository.Course> results =
                repository.searchCourses(
                        "",
                        null,
                        null
                );

        assertEquals(10, results.size());
    }

    @Test
    void searchByCourseCodeFindsCorrectCourse()
            throws Exception {

        List<CourseRepository.Course> results =
                repository.searchCourses(
                        "CS2263",
                        null,
                        null
                );

        assertEquals(1, results.size());

        CourseRepository.Course course = results.get(0);

        assertEquals("CS2263", course.code());
        assertEquals(
                "Systems Software Development",
                course.title()
        );

        assertEquals(4.0, course.creditHours());
    }

    @Test
    void searchByTitleFindsMatchingCourse()
            throws Exception {

        List<CourseRepository.Course> results =
                repository.searchCourses(
                        "Information Security",
                        null,
                        null
                );

        assertEquals(1, results.size());

        assertEquals(
                "CS2413",
                results.get(0).code()
        );
    }

    @Test
    void searchIgnoresLetterCase()
            throws Exception {

        List<CourseRepository.Course> results =
                repository.searchCourses(
                        "dIsCrEtE",
                        null,
                        null
                );

        assertEquals(1, results.size());

        assertEquals(
                "CS1303",
                results.get(0).code()
        );
    }

    @Test
    void campusFilterExcludesOtherCampuses()
            throws Exception {

        List<CourseRepository.Course> fredericton =
                repository.searchCourses(
                        "CS",
                        "Fredericton",
                        ACADEMIC_YEAR
                );

        List<CourseRepository.Course> saintJohn =
                repository.searchCourses(
                        "CS",
                        "Saint John",
                        ACADEMIC_YEAR
                );

        assertEquals(10, fredericton.size());
        assertTrue(saintJohn.isEmpty());

        for (CourseRepository.Course course : fredericton) {
            assertEquals(
                    "Fredericton",
                    course.campusName()
            );
        }
    }

    @Test
    void academicYearFilterWorks()
            throws Exception {

        List<CourseRepository.Course> current =
                repository.searchCourses(
                        "",
                        null,
                        "2026-2027"
                );

        List<CourseRepository.Course> unavailable =
                repository.searchCourses(
                        "",
                        null,
                        "2025-2026"
                );

        assertEquals(10, current.size());
        assertTrue(unavailable.isEmpty());
    }

    @Test
    void exactCodeLookupAcceptsSpacesAndLowercase()
            throws Exception {

        CourseRepository.Course course =
                repository.findByCode(
                        "cs 2043",
                        "Fredericton",
                        ACADEMIC_YEAR
                );

        assertNotNull(course);

        assertEquals("CS2043", course.code());

        assertEquals(
                "Introduction to Software Engineering",
                course.title()
        );

        assertEquals(4.0, course.creditHours());
    }

    @Test
    void unavailableCourseReturnsNull()
            throws Exception {

        assertNull(
                repository.findByCode(
                        "CS9999",
                        "Fredericton",
                        ACADEMIC_YEAR
                )
        );

        assertNull(
                repository.findByCode(
                        "CS2263",
                        "Saint John",
                        ACADEMIC_YEAR
                )
        );

        assertNull(
                repository.findByCode(
                        "",
                        "Fredericton",
                        ACADEMIC_YEAR
                )
        );
    }

    @Test
    void catalogueMetadataAndAcademicYearsAreCorrect()
            throws Exception {

        CourseRepository.Course course =
                repository.findByCode(
                        "CS2263",
                        "Fredericton",
                        ACADEMIC_YEAR
                );

        assertNotNull(course);

        assertEquals(
                "UNKNOWN",
                course.prerequisiteStatus()
        );

        assertEquals(
                "UNKNOWN",
                course.offeringStatus()
        );

        assertEquals(SOURCE_URL, course.sourceUrl());

        assertEquals(
                "2026-10-08",
                course.verifiedOn()
        );

        assertNotNull(course.sourceTitle());

        assertFalse(course.sourceTitle().isBlank());

        assertEquals(
                List.of(ACADEMIC_YEAR),
                repository.findAcademicYears()
        );
    }
}
