
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
 * Integration tests for term planning.
 *
 * Each test uses a temporary SQLite database.
 * The application's normal database is never modified.
 */
public class TermPlanRepositoryTest {

    private static final String YEAR = "2026-2027";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;

    private TermPlanRepository repository;
    private ProfileRepository profileRepository;

    private int frederictonProfileId;
    private int otherProfileId;
    private int saintJohnProfileId;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "term-plan-test.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");

        profileRepository = new ProfileRepository(databasePath);
        profileRepository.initializeCampuses();

        frederictonProfileId =
                profileRepository.createProfile(
                        "Fredericton Test Student",
                        1,
                        null,
                        YEAR
                );

        otherProfileId =
                profileRepository.createProfile(
                        "Another Fredericton Student",
                        1,
                        null,
                        YEAR
                );

        saintJohnProfileId =
                profileRepository.createProfile(
                        "Saint John Test Student",
                        2,
                        null,
                        YEAR
                );

        repository = new TermPlanRepository(databasePath);
    }

    /**
     * Loads one existing project SQL resource.
     */
    private String loadResource(String path)
            throws IOException {

        try (InputStream input =
                getClass().getResourceAsStream(path)) {

            if (input == null) {
                throw new IOException(
                        "Missing SQL resource: " + path
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Executes bundled SQL against the isolated test database.
     */
    private void executeSqlResource(String path)
            throws Exception {

        StringBuilder content = new StringBuilder();

        for (String line : loadResource(path).split("\\R")) {

            if (!line.trim().startsWith("--")) {
                content.append(line).append('\n');
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
                            content.toString().split(";")) {

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
     * Creates a standard test plan.
     */
    private int createPlan() throws Exception {

        return repository.createPlan(
                frederictonProfileId,
                "Fall Planning",
                YEAR,
                "Fall"
        );
    }

    @Test
    void createsTermPlanAndReturnsGeneratedId()
            throws Exception {

        int planId = createPlan();

        assertTrue(planId > 0);

        TermPlanRepository.TermPlan plan =
                repository.findPlan(
                        frederictonProfileId,
                        planId
                );

        assertNotNull(plan);
        assertEquals(planId, plan.planId());

        assertEquals(
                frederictonProfileId,
                plan.profileId()
        );

        assertEquals("Fall Planning", plan.planName());
        assertEquals(YEAR, plan.academicYear());
        assertEquals("Fall", plan.termName());

        assertEquals(0, plan.courseCount());
        assertEquals(0.0, plan.totalCredits(), 0.0001);
        assertNotNull(plan.createdAt());
    }

    @Test
    void listsOnlyPlansBelongingToSelectedProfile()
            throws Exception {

        int first = createPlan();

        int second = repository.createPlan(
                frederictonProfileId,
                "Winter Planning",
                YEAR,
                "Winter"
        );

        repository.createPlan(
                otherProfileId,
                "Private Plan",
                YEAR,
                "Summer"
        );

        List<TermPlanRepository.TermPlan> plans =
                repository.findByProfile(
                        frederictonProfileId
                );

        assertEquals(2, plans.size());

        assertEquals(
                second,
                plans.get(0).planId()
        );

        assertEquals(
                first,
                plans.get(1).planId()
        );

        assertTrue(
                repository.findByProfile(otherProfileId)
                        .stream()
                        .allMatch(plan ->
                                plan.profileId() == otherProfileId
                        )
        );
    }

    @Test
    void addsCoursesAndCalculatesCreditTotals()
            throws Exception {

        int planId = createPlan();

        assertTrue(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertTrue(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS1203"
                )
        );

        TermPlanRepository.TermPlan plan =
                repository.findPlan(
                        frederictonProfileId,
                        planId
                );

        assertNotNull(plan);
        assertEquals(2, plan.courseCount());

        // CS1073 is 4 credits and CS1203 is 3 credits.
        assertEquals(
                7.0,
                plan.totalCredits(),
                0.0001
        );

        assertEquals(
                7.0,
                repository.getTotalCredits(
                        frederictonProfileId,
                        planId
                ),
                0.0001
        );

        List<TermPlanRepository.PlannedCourse> courses =
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                );

        assertEquals(2, courses.size());

        assertEquals("CS1073", courses.get(0).courseCode());
        assertEquals("CS1203", courses.get(1).courseCode());

        assertEquals(
                YEAR,
                courses.get(0).academicYear()
        );
    }

    @Test
    void preventsDuplicateCoursesWithinOnePlan()
            throws Exception {

        int planId = createPlan();

        assertTrue(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertFalse(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertEquals(
                1,
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                ).size()
        );

        assertEquals(
                4.0,
                repository.getTotalCredits(
                        frederictonProfileId,
                        planId
                ),
                0.0001
        );
    }

    @Test
    void normalizesCourseCodeWhitespaceAndCase()
            throws Exception {

        int planId = createPlan();

        assertTrue(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "  cs 1073  "
                )
        );

        assertFalse(
                repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertEquals(
                "CS1073",
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                ).get(0).courseCode()
        );
    }

    @Test
    void rejectsCoursesNotLoadedForPlanYear()
            throws Exception {

        int planId = createPlan();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "CS9999"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.addCourse(
                        frederictonProfileId,
                        planId,
                        "MATH9999"
                )
        );

        assertTrue(
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                ).isEmpty()
        );
    }

    @Test
    void rejectsCourseWhenCampusApplicabilityIsMissing()
            throws Exception {

        int planId = repository.createPlan(
                saintJohnProfileId,
                "Saint John Plan",
                YEAR,
                "Fall"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.addCourse(
                        saintJohnProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertEquals(
                0,
                repository.findPlannedCourses(
                        saintJohnProfileId,
                        planId
                ).size()
        );
    }

    @Test
    void preventsAccessToOtherStudentsPlans()
            throws Exception {

        int planId = createPlan();

        assertNull(
                repository.findPlan(
                        otherProfileId,
                        planId
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.addCourse(
                        otherProfileId,
                        planId,
                        "CS1073"
                )
        );

        assertTrue(
                repository.findPlannedCourses(
                        otherProfileId,
                        planId
                ).isEmpty()
        );

        assertEquals(
                0.0,
                repository.getTotalCredits(
                        otherProfileId,
                        planId
                ),
                0.0001
        );

        assertFalse(
                repository.deletePlan(
                        otherProfileId,
                        planId
                )
        );

        assertNotNull(
                repository.findPlan(
                        frederictonProfileId,
                        planId
                )
        );
    }

    @Test
    void removesPlannedCourseAndUpdatesCredits()
            throws Exception {

        int planId = createPlan();

        repository.addCourse(
                frederictonProfileId,
                planId,
                "CS1073"
        );

        repository.addCourse(
                frederictonProfileId,
                planId,
                "CS1203"
        );

        int plannedCourseId =
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                ).get(0).plannedCourseId();

        assertFalse(
                repository.removeCourse(
                        otherProfileId,
                        planId,
                        plannedCourseId
                )
        );

        assertTrue(
                repository.removeCourse(
                        frederictonProfileId,
                        planId,
                        plannedCourseId
                )
        );

        assertFalse(
                repository.removeCourse(
                        frederictonProfileId,
                        planId,
                        plannedCourseId
                )
        );

        assertEquals(
                3.0,
                repository.getTotalCredits(
                        frederictonProfileId,
                        planId
                ),
                0.0001
        );

        assertEquals(
                1,
                repository.findPlannedCourses(
                        frederictonProfileId,
                        planId
                ).size()
        );
    }

    @Test
    void deletesPlanAndCascadesPlannedCourses()
            throws Exception {

        int planId = createPlan();

        repository.addCourse(
                frederictonProfileId,
                planId,
                "CS1073"
        );

        assertTrue(
                repository.deletePlan(
                        frederictonProfileId,
                        planId
                )
        );

        assertFalse(
                repository.deletePlan(
                        frederictonProfileId,
                        planId
                )
        );

        assertNull(
                repository.findPlan(
                        frederictonProfileId,
                        planId
                )
        );

        try (Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:"
                        + databasePath.toAbsolutePath()
                );
             Statement statement =
                     connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM planned_courses"
             )) {

            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void rejectsInvalidPlanInputsAndMismatchedYears()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        0,
                        "Invalid",
                        YEAR,
                        "Fall"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        999999,
                        "Unknown Student",
                        YEAR,
                        "Fall"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        frederictonProfileId,
                        " ",
                        YEAR,
                        "Fall"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        frederictonProfileId,
                        "Wrong Year",
                        "2025-2026",
                        "Fall"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        frederictonProfileId,
                        "Bad Year",
                        "2026-2028",
                        "Fall"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createPlan(
                        frederictonProfileId,
                        "Bad Term",
                        YEAR,
                        "Spring"
                )
        );

        assertTrue(
                repository.findByProfile(
                        frederictonProfileId
                ).isEmpty()
        );
    }

    @Test
    void acceptsSupportedTermNamesAndValidatesIds()
            throws Exception {

        String[] termInputs = {
                "fall",
                "WINTER",
                "Summer",
                " other "
        };

        String[] expectedTerms = {
                "Fall",
                "Winter",
                "Summer",
                "Other"
        };

        for (int index = 0; index < termInputs.length; index++) {

            int planId = repository.createPlan(
                    frederictonProfileId,
                    "Plan " + index,
                    YEAR,
                    termInputs[index]
            );

            TermPlanRepository.TermPlan plan =
                    repository.findPlan(
                            frederictonProfileId,
                            planId
                    );

            assertNotNull(plan);

            assertEquals(
                    expectedTerms[index],
                    plan.termName()
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findPlan(
                        frederictonProfileId,
                        0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByProfile(-1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.addCourse(
                        frederictonProfileId,
                        -1,
                        "CS1073"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.removeCourse(
                        frederictonProfileId,
                        1,
                        0
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new TermPlanRepository(null)
        );
    }
}
