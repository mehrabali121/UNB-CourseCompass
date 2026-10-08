
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the bundled academic catalogue SQL seed.
 *
 * Each test uses an isolated temporary SQLite database.
 * The application's real database is never modified.
 *
 * These tests verify SQL seed behavior independently
 * of the production database connection.
 */
public class CourseSeedImporterTest {

    private static final String SOURCE_URL =
            "https://www.unb.ca/academics/calendar/"
            + "undergraduate/current/frederictoncourses/"
            + "computer-science/index.html";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;

    /**
     * Creates an empty temporary database with the real schema.
     */
    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve("courses.db");

        executeSqlResource("/db/schema.sql");
    }

    /**
     * Opens the temporary database with foreign keys enabled.
     */
    private Connection openConnection() throws SQLException {

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }

    /**
     * Reads SQL from an application resource.
     */
    private String readResource(String resourcePath)
            throws IOException {

        try (InputStream input =
                getClass().getResourceAsStream(resourcePath)) {

            assertNotNull(
                    input,
                    "Missing SQL resource: " + resourcePath
            );

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Executes the schema or seed SQL in a transaction.
     *
     * Our SQL files contain ordinary semicolon-separated
     * statements and full-line comments.
     */
    private void executeSqlResource(String resourcePath)
            throws SQLException, IOException {

        String content = readResource(resourcePath);

        StringBuilder cleaned = new StringBuilder();

        for (String line : content.split("\\R")) {

            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Connection connection = openConnection()) {

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

            } catch (SQLException exception) {

                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Executes the bundled catalogue seed.
     */
    private void importSeed() throws Exception {

        executeSqlResource("/db/seed_courses.sql");
    }

    /**
     * Counts rows matching a SQL query that returns
     * one integer value.
     */
    private int count(String sql) throws SQLException {

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            assertTrue(result.next());

            return result.getInt(1);
        }
    }

    @Test
    void firstImportCreatesTenCourses() throws Exception {

        importSeed();

        assertEquals(
                10,
                count("""
                        SELECT COUNT(*)
                        FROM courses
                        WHERE academic_year = '2026-2027'
                        """)
        );

        assertEquals(
                1,
                count("""
                        SELECT COUNT(*)
                        FROM courses
                        WHERE course_code = 'CS2263'
                          AND academic_year = '2026-2027'
                          AND course_title =
                              'Systems Software Development'
                        """)
        );
    }

    @Test
    void importCreatesOfficialSourceRecord()
            throws Exception {

        importSeed();

        try (Connection connection = openConnection();
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM academic_sources
                     WHERE source_url = ?
                       AND academic_year = '2026-2027'
                       AND verified_on = '2026-10-08'
                     """)) {

            statement.setString(1, SOURCE_URL);

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void importAssociatesCoursesWithFredericton()
            throws Exception {

        importSeed();

        assertEquals(
                10,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses cc
                        JOIN campuses ca
                            ON ca.campus_id = cc.campus_id
                        JOIN courses c
                            ON c.course_id = cc.course_id
                        WHERE ca.campus_name = 'Fredericton'
                          AND c.academic_year = '2026-2027'
                        """)
        );
    }

    @Test
    void prerequisitesAndOfferingsRemainUnknown()
            throws Exception {

        importSeed();

        assertEquals(
                10,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE prerequisite_status = 'UNKNOWN'
                          AND offering_status = 'UNKNOWN'
                        """)
        );
    }

    @Test
    void repeatedImportDoesNotDuplicateOrEraseData()
            throws Exception {

        importSeed();

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO profiles (
                        profile_name,
                        campus_id,
                        academic_year
                    )
                    VALUES (
                        'Fictional Test Student',
                        1,
                        '2026-2027'
                    )
                    """);
        }

        // Run the exact same seed again.
        importSeed();

        assertEquals(
                10,
                count("SELECT COUNT(*) FROM courses")
        );

        assertEquals(
                10,
                count("SELECT COUNT(*) FROM course_campuses")
        );

        assertEquals(
                1,
                count("SELECT COUNT(*) FROM academic_sources")
        );

        assertEquals(
                1,
                count("""
                        SELECT COUNT(*)
                        FROM profiles
                        WHERE profile_name =
                            'Fictional Test Student'
                        """)
        );
    }
}
