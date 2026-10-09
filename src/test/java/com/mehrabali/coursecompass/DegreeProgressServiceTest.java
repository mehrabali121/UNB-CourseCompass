
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Isolated SQLite integration tests for degree progress.
 *
 * All program requirements in these tests are fictional.
 * No actual student records are modified.
 */
public class DegreeProgressServiceTest {

    private static final String YEAR = "2026-2027";

    @TempDir
    Path tempDirectory;

    private Path databasePath;
    private DegreeProgressService service;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = tempDirectory.resolve("degree-progress.db");

        try (InputStream input = getClass().getResourceAsStream(
                "/db/schema.sql")) {

            assertNotNull(input, "Database schema resource is missing.");

            String schema = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            StringBuilder sql = new StringBuilder();

            for (String line : schema.split("\\R")) {
                if (!line.trim().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }

            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {

                for (String part : sql.toString().split(";")) {
                    if (!part.isBlank()) {
                        statement.execute(part.trim());
                    }
                }
            }
        }

        execute("""
                INSERT INTO campuses (campus_id, campus_name)
                VALUES (1, 'Fredericton'), (2, 'Saint John')
                """);

        execute("""
                INSERT INTO academic_sources (
                    source_id, source_title, source_url,
                    academic_year, verified_on
                )
                VALUES (
                    1,
                    'Fictional test source',
                    'https://example.org/test-only',
                    '2026-2027',
                    '2026-10-09'
                )
                """);

        execute("""
                INSERT INTO courses (
                    course_id, course_code, course_title,
                    credit_hours, academic_year, source_id
                )
                VALUES
                    (1, 'CS1073', 'Test Programming I', 4, '2026-2027', 1),
                    (2, 'CS1083', 'Test Programming II', 4, '2026-2027', 1),
                    (3, 'CS1203', 'Test Overview', 3, '2026-2027', 1)
                """);

        execute("""
                INSERT INTO programs (
                    program_id, program_name, campus_id,
                    academic_year, audit_status, source_id
                )
                VALUES (
                    1,
                    'Fictional Test Program',
                    1,
                    '2026-2027',
                    'PARTIAL',
                    1
                )
                """);

        execute("""
                INSERT INTO profiles (
                    profile_id, profile_name, campus_id,
                    program_id, academic_year
                )
                VALUES
                    (1, 'Student With Program', 1, 1, '2026-2027'),
                    (2, 'Student Without Program', 1, NULL, '2026-2027'),
                    (3, 'Another Program Student', 1, 1, '2026-2027')
                """);

        execute("""
                INSERT INTO completed_courses (
                    profile_id, course_id, completed_on
                )
                VALUES
                    (1, 1, '2026-04-20'),
                    (1, 3, '2026-05-01'),
                    (2, 2, '2026-06-01')
                """);

        execute("""
                INSERT INTO program_requirements (
                    requirement_id, program_id, requirement_name,
                    requirement_type, required_course_id,
                    minimum_credits, source_id, notes
                )
                VALUES
                    (1, 1, 'Programming I',
                     'REQUIRED_COURSE', 1, NULL, 1,
                     'Test requirement only'),
                    (2, 1, 'Programming II',
                     'REQUIRED_COURSE', 2, NULL, 1,
                     'Test requirement only'),
                    (3, 1, 'Mapped Computing Credits',
                     'MINIMUM_CREDITS', NULL, 7, 1,
                     'Test category only'),
                    (4, 1, 'Unmapped Credit Requirement',
                     'MINIMUM_CREDITS', NULL, 3, 1,
                     'Coverage intentionally missing')
                """);

        execute("""
                INSERT INTO requirement_courses (
                    requirement_id, course_id
                )
                VALUES (3, 1), (3, 2)
                """);

        service = new DegreeProgressService(databasePath);
    }

    private Connection openConnection() throws Exception {

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    private void execute(String sql) throws Exception {

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(sql);
        }
    }

    private int countCompletions() throws Exception {

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM completed_courses"
             );
             ResultSet result = statement.executeQuery()) {

            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private DegreeProgressService.RequirementProgress requirement(
            DegreeProgressService.ProgressReport report,
            int requirementId
    ) {

        return report.requirements().stream()
                .filter(item -> item.requirementId() == requirementId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void reportsRecordedCoursesAndCredits() throws Exception {

        var report = service.assess(1);

        assertEquals(1, report.profileId());
        assertEquals("Student With Program", report.studentName());
        assertEquals("Fredericton", report.campus());
        assertEquals(YEAR, report.academicYear());

        assertEquals(2, report.completedCourseCount());
        assertEquals(7.0, report.recordedCreditHours(), 0.001);
        assertEquals(2, report.completedCourses().size());
    }

    @Test
    void supportsStudentWithoutSelectedProgram() throws Exception {

        var report = service.assess(2);

        assertNull(report.programId());
        assertNull(report.programName());
        assertEquals("NO_PROGRAM_SELECTED", report.auditStatus());

        assertEquals(1, report.completedCourseCount());
        assertEquals(4.0, report.recordedCreditHours(), 0.001);
        assertTrue(report.requirements().isEmpty());
    }

    @Test
    void evaluatesRecordedAndMissingRequiredCourses()
            throws Exception {

        var report = service.assess(1);

        assertEquals("Fictional Test Program", report.programName());
        assertEquals("PARTIAL", report.auditStatus());

        var recorded = requirement(report, 1);

        assertEquals(
                DegreeProgressService.RequirementStatus.RECORDED_MATCH,
                recorded.status()
        );

        assertEquals("CS1073", recorded.requiredCourseCode());
        assertEquals(4.0, recorded.matchedCredits(), 0.001);
        assertEquals(
                java.util.List.of("CS1073"),
                recorded.matchedCourseCodes()
        );

        var missing = requirement(report, 2);

        assertEquals(
                DegreeProgressService.RequirementStatus.NOT_RECORDED,
                missing.status()
        );

        assertEquals("CS1083", missing.requiredCourseCode());
        assertEquals(0.0, missing.matchedCredits(), 0.001);
        assertTrue(missing.matchedCourseCodes().isEmpty());
    }

    @Test
    void countsOnlyExplicitlyMappedCredits() throws Exception {

        var report = service.assess(1);
        var mapped = requirement(report, 3);

        assertEquals("MINIMUM_CREDITS", mapped.requirementType());
        assertEquals(7.0, mapped.minimumCredits(), 0.001);

        // CS1203 is recorded but not mapped to this requirement.
        assertEquals(4.0, mapped.matchedCredits(), 0.001);

        assertEquals(
                java.util.List.of("CS1073"),
                mapped.matchedCourseCodes()
        );

        assertEquals(
                DegreeProgressService.RequirementStatus
                        .REPORTED_CREDITS_BELOW_MINIMUM,
                mapped.status()
        );
    }

    @Test
    void recognizesMinimumWhenMappedCompletionsReachIt()
            throws Exception {

        execute("""
                INSERT INTO completed_courses (
                    profile_id, course_id, completed_on
                )
                VALUES (1, 2, '2026-06-10')
                """);

        var report = service.assess(1);
        var mapped = requirement(report, 3);

        assertEquals(8.0, mapped.matchedCredits(), 0.001);

        assertEquals(
                DegreeProgressService.RequirementStatus
                        .REPORTED_CREDITS_AT_LEAST_MINIMUM,
                mapped.status()
        );

        assertEquals(3, report.completedCourseCount());
        assertEquals(11.0, report.recordedCreditHours(), 0.001);
    }

    @Test
    void reportsMissingCreditCategoryMappings() throws Exception {

        var report = service.assess(1);
        var unmapped = requirement(report, 4);

        assertEquals(
                DegreeProgressService.RequirementStatus
                        .NO_ELIGIBLE_COURSE_MAPPING,
                unmapped.status()
        );

        assertEquals(0.0, unmapped.matchedCredits(), 0.001);
        assertTrue(unmapped.matchedCourseCodes().isEmpty());
    }

    @Test
    void keepsProgressSeparateForDifferentProfiles()
            throws Exception {

        var first = service.assess(1);
        var third = service.assess(3);

        assertEquals(2, first.completedCourseCount());
        assertEquals(0, third.completedCourseCount());

        assertEquals(7.0, first.recordedCreditHours(), 0.001);
        assertEquals(0.0, third.recordedCreditHours(), 0.001);

        assertEquals(
                DegreeProgressService.RequirementStatus.NOT_RECORDED,
                requirement(third, 1).status()
        );

        assertEquals(
                DegreeProgressService.RequirementStatus
                        .REPORTED_CREDITS_BELOW_MINIMUM,
                requirement(third, 3).status()
        );
    }

    @Test
    void rejectsInvalidProfilesAndDoesNotModifyDatabase()
            throws Exception {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.assess(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.assess(999999)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DegreeProgressService(null)
        );

        assertEquals(3, countCompletions());

        service.assess(1);
        service.assess(2);
        service.assess(3);

        assertEquals(3, countCompletions());
    }
}
