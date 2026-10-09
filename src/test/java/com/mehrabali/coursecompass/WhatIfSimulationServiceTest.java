
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the read-only What-If Planner.
 *
 * Each test uses its own temporary SQLite database.
 * The normal CourseCompass database is never modified.
 */
public class WhatIfSimulationServiceTest {

    private static final String YEAR = "2026-2027";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private WhatIfSimulationService service;
    private int profileId;
    private int otherProfileId;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "what-if-test.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");

        ProfileRepository profiles =
                new ProfileRepository(databasePath);

        profiles.initializeCampuses();

        profileId = profiles.createProfile(
                "What-If Test Student",
                1,
                null,
                YEAR
        );

        otherProfileId = profiles.createProfile(
                "Another Test Student",
                1,
                null,
                YEAR
        );

        // Record CS1073 as actually completed for
        // the first student only.
        insertCompletedCourse(profileId, "CS1073");

        service = new WhatIfSimulationService(databasePath);
    }

    /**
     * Reads one bundled SQL file.
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
     * Initializes an isolated database using the same
     * SQL files as the application.
     */
    private void executeSqlResource(String resourcePath)
            throws Exception {

        StringBuilder content = new StringBuilder();

        for (String line :
                loadResource(resourcePath).split("\\R")) {

            if (!line.trim().startsWith("--")) {
                content.append(line).append('\n');
            }
        }

        try (Connection connection = openTestConnection()) {

            connection.setAutoCommit(false);

            try {

                try (Statement statement =
                        connection.createStatement()) {

                    for (String fragment :
                            content.toString().split(";")) {

                        String sql = fragment.trim();

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
     * Opens the temporary database with foreign keys enabled.
     */
    private Connection openTestConnection()
            throws Exception {

        Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:"
                        + databasePath.toAbsolutePath()
                );

        try (Statement statement =
                connection.createStatement()) {

            statement.execute(
                    "PRAGMA foreign_keys = ON"
            );

        } catch (Exception exception) {

            connection.close();
            throw exception;
        }

        return connection;
    }

    /**
     * Creates an actual completed-course record solely
     * inside the temporary test database.
     */
    private void insertCompletedCourse(
            int studentId,
            String courseCode
    ) throws Exception {

        String sql = """
                INSERT INTO completed_courses (
                    profile_id,
                    course_id
                )
                SELECT ?, course_id
                FROM courses
                WHERE course_code = ?
                  AND academic_year = ?
                """;

        try (Connection connection = openTestConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);
            statement.setString(2, courseCode);
            statement.setString(3, YEAR);

            assertEquals(
                    1,
                    statement.executeUpdate(),
                    "Expected one loaded course: " + courseCode
            );
        }
    }

    /**
     * Counts actual completed-course records for one student.
     */
    private int countCompletedCourses(int studentId)
            throws Exception {

        String sql = """
                SELECT COUNT(*)
                FROM completed_courses
                WHERE profile_id = ?
                """;

        try (Connection connection = openTestConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, studentId);

            try (ResultSet result =
                    statement.executeQuery()) {

                assertTrue(result.next());

                return result.getInt(1);
            }
        }
    }

    /**
     * Finds one course in the simulation result.
     */
    private WhatIfSimulationService.CourseComparison
            findComparison(
                    WhatIfSimulationService.SimulationResult result,
                    String courseCode
            ) {

        return result.courses().stream()
                .filter(course ->
                        course.courseCode().equals(courseCode)
                )
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError(
                                "Course missing from simulation: "
                                + courseCode
                        )
                );
    }

    @Test
    void simulatesMissingPrerequisiteBecomingMatched()
            throws Exception {

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083")
                );

        WhatIfSimulationService.CourseComparison cs2043 =
                findComparison(result, "CS2043");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .MISSING_REQUIREMENTS,
                cs2043.currentStatus()
        );

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .RECORDED_REQUIREMENTS_MET,
                cs2043.simulatedStatus()
        );

        assertTrue(cs2043.statusChanged());

        assertTrue(
                cs2043.hypotheticalMatches()
                        .contains("CS1083")
        );

        assertTrue(result.changedCourseCount() >= 1);
    }

    @Test
    void keepsActualCompletionRecordsUnchanged()
            throws Exception {

        assertEquals(
                1,
                countCompletedCourses(profileId)
        );

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083", "CS1203")
                );

        assertEquals(
                1,
                result.actualCompletedCourseCount()
        );

        assertEquals(
                1,
                countCompletedCourses(profileId)
        );

        assertEquals(
                0,
                countCompletedCourses(otherProfileId)
        );

        // Verify that no hypothetical CS1083 completion
        // was inserted into the database.
        String sql = """
                SELECT COUNT(*)
                FROM completed_courses cc
                JOIN courses c
                    ON c.course_id = cc.course_id
                WHERE cc.profile_id = ?
                  AND c.course_code = 'CS1083'
                """;

        try (Connection connection = openTestConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);

            try (ResultSet query =
                    statement.executeQuery()) {

                assertTrue(query.next());
                assertEquals(0, query.getInt(1));
            }
        }
    }

    @Test
    void acceptsMultipleHypotheticalCourses()
            throws Exception {

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083", "CS1203")
                );

        assertEquals(profileId, result.profileId());

        assertEquals(
                "What-If Test Student",
                result.studentName()
        );

        assertEquals(
                "Fredericton",
                result.campus()
        );

        assertEquals(YEAR, result.academicYear());

        assertEquals(
                List.of("CS1083", "CS1203"),
                result.hypotheticalCourses()
        );

        assertFalse(result.courses().isEmpty());
    }

    @Test
    void normalizesCourseCodeCaseAndWhitespace()
            throws Exception {

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("  cs 1083  ")
                );

        assertEquals(
                List.of("CS1083"),
                result.hypotheticalCourses()
        );

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .RECORDED_REQUIREMENTS_MET,
                findComparison(
                        result,
                        "CS2043"
                ).simulatedStatus()
        );
    }

    @Test
    void doesNotMarkHypotheticalCoursesActuallyCompleted()
            throws Exception {

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083")
                );

        WhatIfSimulationService.CourseComparison cs1083 =
                findComparison(result, "CS1083");

        assertNotEquals(
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                cs1083.simulatedStatus()
        );

        WhatIfSimulationService.CourseComparison cs1073 =
                findComparison(result, "CS1073");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                cs1073.currentStatus()
        );

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                cs1073.simulatedStatus()
        );

        assertEquals(
                1,
                countCompletedCourses(profileId)
        );
    }

    @Test
    void preservesPartialAndUnknownCoverage()
            throws Exception {

        WhatIfSimulationService.SimulationResult result =
                service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083")
                );

        for (WhatIfSimulationService.CourseComparison course
                : result.courses()) {

            if (course.currentStatus() ==
                    CourseExplorationService.ExplorationStatus
                            .PARTIAL_DATA) {

                assertEquals(
                        CourseExplorationService.ExplorationStatus
                                .PARTIAL_DATA,
                        course.simulatedStatus()
                );
            }

            if (course.currentStatus() ==
                    CourseExplorationService.ExplorationStatus
                            .UNKNOWN) {

                assertEquals(
                        CourseExplorationService.ExplorationStatus
                                .UNKNOWN,
                        course.simulatedStatus()
                );
            }
        }

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .PARTIAL_DATA,
                findComparison(
                        result,
                        "CS2263"
                ).simulatedStatus()
        );
    }

    @Test
    void rejectsInvalidOrDuplicateHypotheticalCourses()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        List.of(" ")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS9999")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1083", "cs 1083")
                )
        );
    }

    @Test
    void rejectsAlreadyCompletedCourseAndInvalidProfile()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        YEAR,
                        List.of("CS1073")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        0,
                        YEAR,
                        List.of("CS1083")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        999999,
                        YEAR,
                        List.of("CS1083")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new WhatIfSimulationService(null)
        );
    }

    @Test
    void rejectsMismatchedAcademicYearAndKeepsDataSafe()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(
                        profileId,
                        "2025-2026",
                        List.of("CS1083")
                )
        );

        assertEquals(
                1,
                countCompletedCourses(profileId)
        );

        assertEquals(
                0,
                countCompletedCourses(otherProfileId)
        );
    }
}
