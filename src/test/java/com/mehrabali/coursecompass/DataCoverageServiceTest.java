
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Isolated integration tests for academic data coverage.
 *
 * All fixture data is fictional and stored in a temporary
 * SQLite database. The user's regular database is untouched.
 */
public class DataCoverageServiceTest {

    @TempDir
    Path tempDirectory;

    private Path databasePath;
    private DataCoverageService service;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = tempDirectory.resolve("coverage-test.db");

        try (InputStream input = getClass().getResourceAsStream(
                "/db/schema.sql")) {

            assertNotNull(input, "Database schema is missing.");

            String schema = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            StringBuilder cleaned = new StringBuilder();

            for (String line : schema.split("\\R")) {
                if (!line.trim().startsWith("--")) {
                    cleaned.append(line).append('\n');
                }
            }

            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {

                for (String part : cleaned.toString().split(";")) {
                    if (!part.isBlank()) {
                        statement.execute(part.trim());
                    }
                }
            }
        }

        service = new DataCoverageService(databasePath);
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

    private int countRows(String tableName) throws Exception {

        Set<String> allowed = Set.of(
                "courses",
                "course_campuses",
                "academic_sources",
                "programs",
                "program_requirements"
        );

        if (!allowed.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Unexpected table name."
            );
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName
             )) {

            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    /**
     * Adds fictional academic data only when a test calls it.
     */
    private void populateFixture() throws Exception {

        execute("""
                INSERT INTO campuses (campus_id, campus_name)
                VALUES
                    (1, 'Fredericton'),
                    (2, 'Saint John')
                """);

        execute("""
                INSERT INTO academic_sources (
                    source_id,
                    source_title,
                    source_url,
                    academic_year,
                    verified_on,
                    notes
                )
                VALUES
                    (
                        1,
                        'Fictional CS Catalogue',
                        'https://example.org/fictional-cs',
                        '2026-2027',
                        '2026-10-08',
                        'Test-only source'
                    ),
                    (
                        2,
                        'Fictional Program Calendar',
                        'https://example.org/fictional-program',
                        '2026-2027',
                        '2026-10-07',
                        'Test-only program source'
                    )
                """);

        execute("""
                INSERT INTO courses (
                    course_id,
                    course_code,
                    course_title,
                    credit_hours,
                    academic_year,
                    source_id
                )
                VALUES
                    (1, 'CS1073', 'Test Programming I',
                     4, '2026-2027', 1),
                    (2, 'CS1083', 'Test Programming II',
                     4, '2026-2027', 1),
                    (3, 'CS2383', 'Test Data Structures',
                     4, '2026-2027', 1),
                    (4, 'CS2413', 'Test Security',
                     4, '2026-2027', 1)
                """);

        execute("""
                INSERT INTO course_campuses (
                    course_id,
                    campus_id,
                    prerequisite_status,
                    offering_status,
                    source_id
                )
                VALUES
                    (1, 1, 'NONE', 'UNKNOWN', 1),
                    (2, 1, 'STRUCTURED', 'VERIFIED_OFFERED', 1),
                    (3, 1, 'PARTIAL', 'VERIFIED_NOT_OFFERED', 1),
                    (4, 2, 'UNKNOWN', 'UNKNOWN', 1)
                """);

        execute("""
                INSERT INTO programs (
                    program_id,
                    program_name,
                    campus_id,
                    academic_year,
                    audit_status,
                    source_id
                )
                VALUES
                    (1, 'Fictional Supported Program',
                     1, '2026-2027', 'SUPPORTED', 2),
                    (2, 'Fictional Partial Program',
                     1, '2026-2027', 'PARTIAL', 2),
                    (3, 'Fictional Unsupported Program',
                     2, '2026-2027', 'UNSUPPORTED', 2)
                """);

        execute("""
                INSERT INTO program_requirements (
                    program_id,
                    requirement_name,
                    requirement_type,
                    required_course_id,
                    minimum_credits,
                    source_id,
                    notes
                )
                VALUES
                    (1, 'Programming I Requirement',
                     'REQUIRED_COURSE', 1, NULL, 2,
                     'Fictional course requirement'),
                    (2, 'Computing Credits',
                     'MINIMUM_CREDITS', NULL, 8, 2,
                     'Fictional credit requirement')
                """);
    }

    @Test
    void handlesAnEmptyDatabase() throws Exception {

        var report = service.assess();

        assertEquals(0, report.courseRecordCount());
        assertEquals(0, report.sourceCount());

        assertTrue(report.courses().isEmpty());
        assertTrue(report.sources().isEmpty());
        assertTrue(report.academicYears().isEmpty());

        assertEquals(0, report.noPrerequisites());
        assertEquals(0, report.structuredPrerequisites());
        assertEquals(0, report.partialPrerequisites());
        assertEquals(0, report.unknownPrerequisites());

        assertEquals(0, report.verifiedOffered());
        assertEquals(0, report.verifiedNotOffered());
        assertEquals(0, report.unknownOfferings());

        assertEquals(0, report.programCount());
        assertEquals(0, report.programRequirementCount());
    }

    @Test
    void countsActualCourseAndPrerequisiteRecords()
            throws Exception {

        populateFixture();

        var report = service.assess();

        assertEquals(4, report.courseRecordCount());

        assertEquals(1, report.noPrerequisites());
        assertEquals(1, report.structuredPrerequisites());
        assertEquals(1, report.partialPrerequisites());
        assertEquals(1, report.unknownPrerequisites());

        assertEquals(
                2,
                report.fullyDescribedPrerequisiteCount()
        );

        assertEquals(
                java.util.List.of("2026-2027"),
                report.academicYears()
        );

        Set<String> campuses = report.courses().stream()
                .map(CourseRepository.Course::campusName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("Fredericton", "Saint John"),
                campuses
        );
    }

    @Test
    void distinguishesKnownAndUnknownOfferings()
            throws Exception {

        populateFixture();

        var report = service.assess();

        assertEquals(1, report.verifiedOffered());
        assertEquals(1, report.verifiedNotOffered());
        assertEquals(2, report.unknownOfferings());

        assertEquals(
                report.courseRecordCount(),
                report.verifiedOffered()
                        + report.verifiedNotOffered()
                        + report.unknownOfferings()
        );
    }

    @Test
    void preservesAcademicSourceDetails() throws Exception {

        populateFixture();

        var report = service.assess();

        assertEquals(2, report.sourceCount());

        assertTrue(
                report.sources().stream().anyMatch(source ->
                        source.title().equals(
                                "Fictional CS Catalogue"
                        )
                        && source.url().equals(
                                "https://example.org/fictional-cs"
                        )
                        && source.academicYear().equals("2026-2027")
                        && source.verifiedOn().equals("2026-10-08")
                )
        );

        assertTrue(
                report.courses().stream().allMatch(course ->
                        course.sourceTitle().equals(
                                "Fictional CS Catalogue"
                        )
                )
        );
    }

    @Test
    void reportsProgramAuditAndRequirementCoverage()
            throws Exception {

        populateFixture();

        var report = service.assess();

        assertEquals(3, report.programCount());

        assertEquals(1, report.supportedPrograms());
        assertEquals(1, report.partialPrograms());
        assertEquals(1, report.unsupportedPrograms());

        assertEquals(2, report.programRequirementCount());
    }

    @Test
    void assessesWithoutModifyingStoredRecords()
            throws Exception {

        populateFixture();

        int coursesBefore = countRows("courses");
        int campusesBefore = countRows("course_campuses");
        int sourcesBefore = countRows("academic_sources");
        int programsBefore = countRows("programs");
        int requirementsBefore = countRows("program_requirements");

        var first = service.assess();
        var second = service.assess();

        assertEquals(
                first.courseRecordCount(),
                second.courseRecordCount()
        );

        assertEquals(coursesBefore, countRows("courses"));
        assertEquals(campusesBefore, countRows("course_campuses"));
        assertEquals(sourcesBefore, countRows("academic_sources"));
        assertEquals(programsBefore, countRows("programs"));

        assertEquals(
                requirementsBefore,
                countRows("program_requirements")
        );

        assertThrows(
                NullPointerException.class,
                () -> new DataCoverageService(null)
        );
    }
}
