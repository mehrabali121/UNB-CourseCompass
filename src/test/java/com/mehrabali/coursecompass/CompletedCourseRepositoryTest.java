
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for student completed-course tracking.
 *
 * Every test uses a temporary SQLite database.
 * The real CourseCompass database is never modified.
 */
public class CompletedCourseRepositoryTest {

    private static final String YEAR = "2026-2027";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private CompletedCourseRepository completedRepository;
    private ProfileRepository profileRepository;

    private int firstProfileId;
    private int secondProfileId;

    /**
     * Creates an isolated database with course records
     * and two fictional student profiles.
     */
    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "completed-courses.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");

        completedRepository =
                new CompletedCourseRepository(databasePath);

        profileRepository =
                new ProfileRepository(databasePath);

        profileRepository.initializeCampuses();

        firstProfileId = profileRepository.createProfile(
                "First Test Student",
                1,
                null,
                YEAR
        );

        secondProfileId = profileRepository.createProfile(
                "Second Test Student",
                1,
                null,
                YEAR
        );
    }

    /**
     * Loads a bundled SQL resource.
     */
    private String loadResource(String resourcePath)
            throws IOException {

        try (InputStream input =
                getClass().getResourceAsStream(resourcePath)) {

            if (input == null) {
                throw new IOException(
                        "SQL resource not found: " + resourcePath
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Executes schema or seed SQL in one transaction.
     */
    private void executeSqlResource(String resourcePath)
            throws Exception {

        StringBuilder sqlContent = new StringBuilder();

        for (String line :
                loadResource(resourcePath).split("\\R")) {

            if (!line.trim().startsWith("--")) {
                sqlContent.append(line).append('\n');
            }
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {

            try (Statement statement =
                    connection.createStatement()) {

                statement.execute("PRAGMA foreign_keys = ON");
            }

            connection.setAutoCommit(false);

            try {

                try (Statement statement =
                        connection.createStatement()) {

                    for (String part :
                            sqlContent.toString().split(";")) {

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

    /**
     * Counts rows matching a query in the test database.
     */
    private int countRows(String sql) throws Exception {

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            assertTrue(result.next());

            return result.getInt(1);
        }
    }

    @Test
    void addsCompletedCourseAndRetrievesDetails()
            throws Exception {

        boolean added =
                completedRepository.addCompletedCourse(
                        firstProfileId,
                        "cs 1083",
                        YEAR,
                        "2026-04-20"
                );

        assertTrue(added);

        List<CompletedCourseRepository.CompletedCourse> courses =
                completedRepository.findByProfile(
                        firstProfileId
                );

        assertEquals(1, courses.size());

        var course = courses.get(0);

        assertEquals(firstProfileId, course.profileId());
        assertEquals("CS1083", course.courseCode());
        assertEquals(
                "Introduction to Computer Programming II (in Java)",
                course.courseTitle()
        );
        assertEquals(4.0, course.creditHours());
        assertEquals(YEAR, course.academicYear());
        assertEquals("2026-04-20", course.completedOn());
        assertTrue(course.completionId() > 0);
    }

    @Test
    void preventsDuplicateCompletions()
            throws Exception {

        assertTrue(
                completedRepository.addCompletedCourse(
                        firstProfileId, "CS1073", YEAR, null
                )
        );

        assertFalse(
                completedRepository.addCompletedCourse(
                        firstProfileId, "cs 1073", YEAR, null
                )
        );

        assertEquals(
                1,
                completedRepository.findByProfile(
                        firstProfileId
                ).size()
        );
    }

    @Test
    void acceptsOptionalDateAndRejectsInvalidDates()
            throws Exception {

        assertTrue(
                completedRepository.addCompletedCourse(
                        firstProfileId, "CS1203", YEAR, ""
                )
        );

        var courses =
                completedRepository.findByProfile(
                        firstProfileId
                );

        assertNull(courses.get(0).completedOn());

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS1303",
                        YEAR,
                        "not-a-date"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS1303",
                        YEAR,
                        "2026-02-30"
                )
        );
    }

    @Test
    void rejectsUnknownCoursesAndInvalidInputs()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS9999",
                        YEAR,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "",
                        YEAR,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS1083",
                        "2025-2026",
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS1083",
                        "2026-2028",
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        999999,
                        "CS1083",
                        YEAR,
                        null
                )
        );

        assertTrue(
                completedRepository.findByProfile(
                        firstProfileId
                ).isEmpty()
        );
    }

    @Test
    void rejectsCoursesOutsideStudentsCampus()
            throws Exception {

        // The course seed associates its courses only
        // with Fredericton, not Saint John.

        int saintJohnProfileId =
                profileRepository.createProfile(
                        "Saint John Test Student",
                        2,
                        null,
                        YEAR
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> completedRepository.addCompletedCourse(
                        saintJohnProfileId,
                        "CS1083",
                        YEAR,
                        null
                )
        );

        assertTrue(
                completedRepository.findByProfile(
                        saintJohnProfileId
                ).isEmpty()
        );
    }

    @Test
    void keepsDifferentProfilesCompletionsSeparate()
            throws Exception {

        assertTrue(
                completedRepository.addCompletedCourse(
                        firstProfileId,
                        "CS1073",
                        YEAR,
                        null
                )
        );

        assertTrue(
                completedRepository.addCompletedCourse(
                        secondProfileId,
                        "CS2413",
                        YEAR,
                        null
                )
        );

        var firstCourses =
                completedRepository.findByProfile(
                        firstProfileId
                );

        var secondCourses =
                completedRepository.findByProfile(
                        secondProfileId
                );

        assertEquals(1, firstCourses.size());
        assertEquals(1, secondCourses.size());

        assertEquals(
                "CS1073",
                firstCourses.get(0).courseCode()
        );

        assertEquals(
                "CS2413",
                secondCourses.get(0).courseCode()
        );
    }

    @Test
    void removesOnlyTheSelectedProfilesCompletion()
            throws Exception {

        completedRepository.addCompletedCourse(
                firstProfileId, "CS1073", YEAR, null
        );

        completedRepository.addCompletedCourse(
                secondProfileId, "CS1083", YEAR, null
        );

        int firstCompletionId =
                completedRepository.findByProfile(
                        firstProfileId
                ).get(0).completionId();

        // The second profile cannot delete the first
        // profile's completion record.

        assertFalse(
                completedRepository.removeCompletedCourse(
                        secondProfileId,
                        firstCompletionId
                )
        );

        assertEquals(
                1,
                completedRepository.findByProfile(
                        firstProfileId
                ).size()
        );

        assertTrue(
                completedRepository.removeCompletedCourse(
                        firstProfileId,
                        firstCompletionId
                )
        );

        assertFalse(
                completedRepository.removeCompletedCourse(
                        firstProfileId,
                        firstCompletionId
                )
        );

        assertTrue(
                completedRepository.findByProfile(
                        firstProfileId
                ).isEmpty()
        );

        assertEquals(
                1,
                completedRepository.findByProfile(
                        secondProfileId
                ).size()
        );
    }

    @Test
    void deletingProfileAlsoDeletesItsCompletions()
            throws Exception {

        completedRepository.addCompletedCourse(
                firstProfileId, "CS1073", YEAR, null
        );

        completedRepository.addCompletedCourse(
                secondProfileId, "CS1083", YEAR, null
        );

        assertTrue(
                profileRepository.deleteProfile(firstProfileId)
        );

        assertTrue(
                completedRepository.findByProfile(
                        firstProfileId
                ).isEmpty()
        );

        assertEquals(
                1,
                completedRepository.findByProfile(
                        secondProfileId
                ).size()
        );

        assertEquals(
                1,
                countRows(
                        "SELECT COUNT(*) FROM completed_courses"
                )
        );
    }
}
