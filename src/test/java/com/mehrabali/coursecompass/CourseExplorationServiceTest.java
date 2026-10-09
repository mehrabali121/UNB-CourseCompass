
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
 * Integration tests for student course exploration.
 *
 * Each test uses an isolated temporary SQLite database.
 * No real student profiles or course completions are changed.
 */
public class CourseExplorationServiceTest {

    private static final String YEAR = "2026-2027";
    private static final String CAMPUS = "Fredericton";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;

    private ProfileRepository profileRepository;
    private CompletedCourseRepository completedRepository;
    private CourseExplorationService explorationService;

    private int profileId;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "course-exploration-test.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");

        profileRepository =
                new ProfileRepository(databasePath);

        profileRepository.initializeCampuses();

        profileId = profileRepository.createProfile(
                "Course Exploration Test Student",
                1,
                null,
                YEAR
        );

        completedRepository =
                new CompletedCourseRepository(databasePath);

        explorationService =
                new CourseExplorationService(databasePath);
    }

    /**
     * Reads an existing bundled SQL resource.
     */
    private String loadResource(String resourcePath)
            throws IOException {

        try (InputStream input =
                getClass().getResourceAsStream(resourcePath)) {

            if (input == null) {

                throw new IOException(
                        "Missing resource: " + resourcePath
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Initializes an isolated database with the real project
     * schema and the existing verified seed records.
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

                statement.execute(
                        "PRAGMA foreign_keys = ON"
                );
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
     * Adds one self-reported course completion.
     */
    private void completeCourse(String code)
            throws Exception {

        assertTrue(
                completedRepository.addCompletedCourse(
                        profileId,
                        code,
                        YEAR,
                        null
                )
        );
    }

    /**
     * Returns the exploration record for one course code.
     */
    private CourseExplorationService.ExploredCourse findCourse(
            CourseExplorationService.ExplorationResult result,
            String code
    ) {

        return result.courses().stream()
                .filter(course ->
                        course.course().code().equals(code)
                )
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError(
                                "Expected course not found: " + code
                        )
                );
    }

    @Test
    void exploresAllTenLoadedFrederictonCourses()
            throws Exception {

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        assertEquals(profileId, result.profileId());

        assertEquals(
                "Course Exploration Test Student",
                result.studentName()
        );

        assertEquals(CAMPUS, result.campus());
        assertEquals(YEAR, result.academicYear());

        assertEquals(0, result.completedCourseCount());
        assertEquals(10, result.courses().size());

        assertTrue(
                result.courses().stream()
                        .allMatch(course ->
                                CAMPUS.equals(
                                        course.course().campusName()
                                )
                        )
        );

        assertTrue(
                result.courses().stream()
                        .allMatch(course ->
                                YEAR.equals(
                                        course.course().academicYear()
                                )
                        )
        );
    }

    @Test
    void identifiesMissingRecordedPrerequisites()
            throws Exception {

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        var cs1083 = findCourse(result, "CS1083");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .MISSING_REQUIREMENTS,
                cs1083.status()
        );

        assertNotNull(cs1083.evaluation());

        assertEquals(
                PrerequisiteEvaluator.Status.MISSING_REQUIREMENTS,
                cs1083.evaluation().status()
        );
    }

    @Test
    void recognizesRecordedRequirementsAfterCompletion()
            throws Exception {

        completeCourse("CS1073");

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        var cs1083 = findCourse(result, "CS1083");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .RECORDED_REQUIREMENTS_MET,
                cs1083.status()
        );

        assertEquals(1, result.completedCourseCount());

        assertTrue(
                cs1083.evaluation().groups().get(0)
                        .hasRecordedMatch()
        );
    }

    @Test
    void distinguishesCoursesAlreadyCompleted()
            throws Exception {

        completeCourse("CS1073");

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        var cs1073 = findCourse(result, "CS1073");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                cs1073.status()
        );

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .RECORDED_REQUIREMENTS_MET,
                findCourse(result, "CS1083").status()
        );
    }

    @Test
    void preservesPartialPrerequisiteDataStatus()
            throws Exception {

        completeCourse("CS1083");
        completeCourse("CS1543");
        completeCourse("CS1303");

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        var cs2413 = findCourse(result, "CS2413");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .PARTIAL_DATA,
                cs2413.status()
        );

        assertNotNull(cs2413.evaluation());

        assertEquals(
                PrerequisiteEvaluator.Status.PARTIAL_DATA,
                cs2413.evaluation().status()
        );

        assertTrue(
                cs2413.evaluation().notes()
                        .contains("MATH2203")
        );
    }

    @Test
    void preservesNoListedPrerequisitesStatus()
            throws Exception {

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        var cs2253 = findCourse(result, "CS2253");

        assertEquals(
                CourseExplorationService.ExplorationStatus
                        .NO_LISTED_PREREQUISITES,
                cs2253.status()
        );

        assertTrue(
                cs2253.evaluation().notes()
                        .contains("CS2263")
        );
    }

    @Test
    void returnsEmptyCatalogueForCampusWithoutLoadedCourses()
            throws Exception {

        int saintJohnProfile =
                profileRepository.createProfile(
                        "Saint John Test Student",
                        2,
                        null,
                        YEAR
                );

        var result = explorationService.explore(
                saintJohnProfile,
                YEAR
        );

        assertEquals("Saint John", result.campus());
        assertEquals(0, result.completedCourseCount());

        assertTrue(result.courses().isEmpty());
    }

    @Test
    void rejectsNonexistentProfilesAndMismatchedYears()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> explorationService.explore(
                        999999,
                        YEAR
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> explorationService.explore(
                        profileId,
                        "2025-2026"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> explorationService.explore(
                        0,
                        YEAR
                )
        );
    }

    @Test
    void rejectsMissingYearAndReturnsImmutableResults()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> explorationService.explore(
                        profileId,
                        ""
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> explorationService.explore(
                        profileId,
                        null
                )
        );

        var result = explorationService.explore(
                profileId,
                YEAR
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.courses().clear()
        );

        assertEquals(10, result.courses().size());
    }
}
