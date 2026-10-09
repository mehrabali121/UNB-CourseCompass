
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
 * Tests verified prerequisite seed data using isolated
 * temporary SQLite databases.
 *
 * The real application database is never modified.
 */
public class PrerequisiteSeedTest {

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;

    /**
     * Initializes a fresh database with the existing schema,
     * course catalogue, and prerequisite seed.
     */
    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "prerequisites.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");
    }

    /**
     * Opens a connection to the temporary database.
     */
    private Connection openConnection() throws SQLException {

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        );

        try (Statement statement =
                connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");

        } catch (SQLException exception) {

            connection.close();
            throw exception;
        }

        return connection;
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
     * Executes semicolon-separated SQL statements safely.
     */
    private void executeSqlResource(String resourcePath)
            throws SQLException, IOException {

        String content = loadResource(resourcePath);

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

            } finally {

                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Returns one integer result from a SQL query.
     */
    private int count(String sql) throws SQLException {

        try (Connection connection = openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(sql)) {

            assertTrue(result.next());

            return result.getInt(1);
        }
    }

    @Test
    void createsExpectedGroupsAndOptions()
            throws Exception {

        assertEquals(
                9,
                count("SELECT COUNT(*) FROM prerequisite_groups")
        );

        assertEquals(
                9,
                count("SELECT COUNT(*) FROM prerequisite_options")
        );
    }

    @Test
    void assignsExpectedPrerequisiteStatuses()
            throws Exception {

        assertEquals(
                3,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE prerequisite_status = 'STRUCTURED'
                        """)
        );

        assertEquals(
                3,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE prerequisite_status = 'PARTIAL'
                        """)
        );

        assertEquals(
                4,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE prerequisite_status = 'NONE'
                        """)
        );

        assertEquals(
                0,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE prerequisite_status = 'UNKNOWN'
                        """)
        );
    }

    @Test
    void structuredCoursesHaveCorrectPrerequisites()
            throws Exception {

        assertEquals(
                1,
                count("""
                        SELECT COUNT(*)
                        FROM prerequisite_options po
                        JOIN prerequisite_groups pg
                          ON pg.group_id = po.group_id
                        JOIN course_campuses cc
                          ON cc.course_campus_id = pg.course_campus_id
                        JOIN courses target
                          ON target.course_id = cc.course_id
                        JOIN courses required
                          ON required.course_id = po.required_course_id
                        WHERE target.course_code = 'CS1083'
                          AND required.course_code = 'CS1073'
                        """)
        );

        assertEquals(
                1,
                count("""
                        SELECT COUNT(*)
                        FROM prerequisite_options po
                        JOIN prerequisite_groups pg
                          ON pg.group_id = po.group_id
                        JOIN course_campuses cc
                          ON cc.course_campus_id = pg.course_campus_id
                        JOIN courses target
                          ON target.course_id = cc.course_id
                        JOIN courses required
                          ON required.course_id = po.required_course_id
                        WHERE target.course_code = 'CS2043'
                          AND required.course_code = 'CS1083'
                        """)
        );
    }

    @Test
    void incompleteAlternativesRemainMarkedPartial()
            throws Exception {

        assertEquals(
                3,
                count("""
                        SELECT COUNT(*)
                        FROM courses c
                        JOIN course_campuses cc
                          ON cc.course_id = c.course_id
                        WHERE c.course_code IN (
                            'CS2263', 'CS2383', 'CS2413'
                        )
                          AND cc.prerequisite_status = 'PARTIAL'
                        """)
        );

        assertEquals(
                1,
                count("""
                        SELECT COUNT(*)
                        FROM courses c
                        JOIN course_campuses cc
                          ON cc.course_id = c.course_id
                        WHERE c.course_code = 'CS2253'
                          AND cc.prerequisite_status = 'NONE'
                          AND cc.prerequisite_notes LIKE '%co-requisite%'
                        """)
        );
    }

    @Test
    void repeatedSeedDoesNotCreateDuplicates()
            throws Exception {

        executeSqlResource("/db/seed_prerequisites.sql");

        assertEquals(
                9,
                count("SELECT COUNT(*) FROM prerequisite_groups")
        );

        assertEquals(
                9,
                count("SELECT COUNT(*) FROM prerequisite_options")
        );

        assertEquals(
                10,
                count("SELECT COUNT(*) FROM course_campuses")
        );
    }

    @Test
    void databaseIntegrityAndOfferingStatusesRemainValid()
            throws Exception {

        assertEquals(
                0,
                count("SELECT COUNT(*) FROM pragma_foreign_key_check")
        );

        assertEquals(
                10,
                count("""
                        SELECT COUNT(*)
                        FROM course_campuses
                        WHERE offering_status = 'UNKNOWN'
                        """)
        );

        try (Connection connection = openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(
                             "PRAGMA integrity_check"
                     )) {

            assertTrue(result.next());
            assertEquals("ok", result.getString(1));
        }
    }
}
