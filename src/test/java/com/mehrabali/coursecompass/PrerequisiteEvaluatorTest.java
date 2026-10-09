
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
 * Integration tests for prerequisite evaluation.
 *
 * All tests use a temporary SQLite database.
 * No real student data is modified.
 */
public class PrerequisiteEvaluatorTest {

    private static final String YEAR = "2026-2027";
    private static final String CAMPUS = "Fredericton";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private ProfileRepository profileRepository;
    private CompletedCourseRepository completedRepository;
    private PrerequisiteEvaluator evaluator;

    private int profileId;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "prerequisite-evaluation.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");

        profileRepository = new ProfileRepository(databasePath);
        profileRepository.initializeCampuses();

        profileId = profileRepository.createProfile(
                "Prerequisite Evaluation Test Student",
                1,
                null,
                YEAR
        );

        completedRepository =
                new CompletedCourseRepository(databasePath);

        evaluator = new PrerequisiteEvaluator(databasePath);
    }

    /**
     * Loads SQL from the application's existing resources.
     */
    private String loadResource(String resourcePath)
            throws IOException {

        try (InputStream input =
                getClass().getResourceAsStream(resourcePath)) {

            if (input == null) {
                throw new IOException(
                        "Missing SQL resource: " + resourcePath
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Executes schema and seed SQL on the temporary database.
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

        try (Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:"
                        + databasePath.toAbsolutePath()
                )) {

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
     * Runs one SQL statement on the temporary database.
     */
    private void executeUpdate(String sql) throws Exception {

        try (Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:"
                        + databasePath.toAbsolutePath()
                );
             Statement statement =
                    connection.createStatement()) {

            statement.executeUpdate(sql);
        }
    }

    /**
     * Adds one self-reported completion for the test student.
     */
    private void completeCourse(String courseCode)
            throws Exception {

        assertTrue(
                completedRepository.addCompletedCourse(
                        profileId,
                        courseCode,
                        YEAR,
                        null
                )
        );
    }

    /**
     * Evaluates a course for the matching Fredericton profile.
     */
    private PrerequisiteEvaluator.Evaluation evaluate(
            String courseCode
    ) throws Exception {

        return evaluator.evaluate(
                profileId,
                courseCode,
                CAMPUS,
                YEAR
        );
    }

    @Test
    void identifiesMissingStructuredRequirements()
            throws Exception {

        var result = evaluate("CS1083");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.MISSING_REQUIREMENTS,
                result.status()
        );

        assertEquals(1, result.groups().size());

        var group = result.groups().get(0);

        assertEquals(1, group.groupNumber());

        assertEquals(
                List.of("CS1073"),
                group.acceptedCourseCodes()
        );

        assertTrue(group.recordedCourseCodes().isEmpty());
        assertFalse(group.hasRecordedMatch());

        assertEquals(profileId, result.profileId());
        assertEquals(CAMPUS, result.campus());
        assertEquals(YEAR, result.academicYear());
    }

    @Test
    void recognizesAllModeledRequirementsRecorded()
            throws Exception {

        completeCourse("CS1073");

        var result = evaluate("CS1083");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.RECORDED_REQUIREMENTS_MET,
                result.status()
        );

        assertEquals(
                List.of("CS1073"),
                result.groups().get(0).recordedCourseCodes()
        );

        assertTrue(
                result.groups().get(0).hasRecordedMatch()
        );

        assertTrue(
                result.explanation().contains(
                        "does not verify grades"
                )
        );
    }

    @Test
    void keepsPartiallyModeledPrerequisitesUncertain()
            throws Exception {

        completeCourse("CS1083");
        completeCourse("CS1543");
        completeCourse("CS1303");

        var result = evaluate("CS2413");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.PARTIAL_DATA,
                result.status()
        );

        assertEquals(3, result.groups().size());

        assertTrue(
                result.groups().stream()
                        .allMatch(
                                PrerequisiteEvaluator.GroupResult
                                        ::hasRecordedMatch
                        )
        );

        assertTrue(result.notes().contains("MATH2203"));

        assertTrue(
                result.explanation().contains(
                        "No definitive eligibility conclusion"
                )
        );
    }

    @Test
    void distinguishesNoPrerequisitesFromEligibility()
            throws Exception {

        var result = evaluate("CS2253");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.NO_LISTED_PREREQUISITES,
                result.status()
        );

        assertTrue(result.groups().isEmpty());

        assertTrue(result.notes().contains("CS2263"));

        assertTrue(
                result.explanation().contains("Co-requisites")
        );
    }

    @Test
    void reportsUnknownPrerequisiteCoverage()
            throws Exception {

        executeUpdate("""
                UPDATE course_campuses
                SET prerequisite_status = 'UNKNOWN',
                    prerequisite_notes = 'Not verified'
                WHERE course_id = (
                    SELECT course_id
                    FROM courses
                    WHERE course_code = 'CS1083'
                      AND academic_year = '2026-2027'
                )
                """);

        var result = evaluate("CS1083");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.UNKNOWN,
                result.status()
        );
    }

    @Test
    void returnsNullForUnavailableTargetCourse()
            throws Exception {

        // The profile's campus matches the requested campus.
        // Only the course itself is absent.

        assertNull(evaluate("CS9999"));
    }

    @Test
    void acceptsOneRecordedAlternativeWithinAnOrGroup()
            throws Exception {

        // Synthetic test-only OR alternative:
        // CS1073 OR CS1203 for CS1083.
        //
        // This is NOT an official UNB prerequisite rule.

        executeUpdate("""
                INSERT INTO prerequisite_options (
                    group_id,
                    required_course_id
                )
                SELECT pg.group_id, c_alt.course_id
                FROM prerequisite_groups pg
                JOIN course_campuses cc
                    ON cc.course_campus_id = pg.course_campus_id
                JOIN courses c_target
                    ON c_target.course_id = cc.course_id
                JOIN courses c_alt
                    ON c_alt.course_code = 'CS1203'
                   AND c_alt.academic_year = '2026-2027'
                WHERE c_target.course_code = 'CS1083'
                  AND c_target.academic_year = '2026-2027'
                  AND pg.group_number = 1
                """);

        completeCourse("CS1203");

        var result = evaluate("CS1083");

        assertNotNull(result);

        assertEquals(
                PrerequisiteEvaluator.Status.RECORDED_REQUIREMENTS_MET,
                result.status()
        );

        assertEquals(1, result.groups().size());

        var group = result.groups().get(0);

        assertEquals(
                List.of("CS1073", "CS1203"),
                group.acceptedCourseCodes()
        );

        assertEquals(
                List.of("CS1203"),
                group.recordedCourseCodes()
        );

        assertTrue(group.hasRecordedMatch());
    }

    @Test
    void rejectsInvalidProfileIdentifiers()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(
                        0, "CS1083", CAMPUS, YEAR
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(
                        -1, "CS1083", CAMPUS, YEAR
                )
        );
    }

    @Test
    void rejectsNonexistentStudentProfile()
            throws Exception {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(
                        999999,
                        "CS1083",
                        CAMPUS,
                        YEAR
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "Student profile not found"
                )
        );
    }

    @Test
    void rejectsCampusMismatch()
            throws Exception {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(
                        profileId,
                        "CS1083",
                        "Saint John",
                        YEAR
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "does not match"
                )
        );
    }

    @Test
    void rejectsAcademicYearMismatch()
            throws Exception {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(
                        profileId,
                        "CS1083",
                        CAMPUS,
                        "2025-2026"
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "does not match"
                )
        );
    }

    @Test
    void acceptsMatchingProfileWithUnspecifiedSavedYear()
            throws Exception {

        int profileWithoutYear =
                profileRepository.createProfile(
                        "Student With No Saved Year",
                        1,
                        null,
                        null
                );

        var result = evaluator.evaluate(
                profileWithoutYear,
                "CS1083",
                CAMPUS,
                YEAR
        );

        assertNotNull(result);

        assertEquals(
                profileWithoutYear,
                result.profileId()
        );

        assertEquals(
                PrerequisiteEvaluator.Status.MISSING_REQUIREMENTS,
                result.status()
        );
    }
}
