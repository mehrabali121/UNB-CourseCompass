
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the prerequisite repository.
 *
 * Each test uses an isolated SQLite database with the real
 * schema, course seed, and prerequisite seed.
 *
 * The application's normal database is never modified.
 */
public class PrerequisiteRepositoryTest {

    private static final String YEAR = "2026-2027";
    private static final String CAMPUS = "Fredericton";

    private static final String SOURCE_URL =
            "https://www.unb.ca/academics/calendar/"
            + "undergraduate/current/frederictoncourses/"
            + "computer-science/index.html";

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private PrerequisiteRepository repository;

    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve(
                "prerequisite-repository.db"
        );

        executeSqlResource("/db/schema.sql");
        executeSqlResource("/db/seed_courses.sql");
        executeSqlResource("/db/seed_prerequisites.sql");

        repository = new PrerequisiteRepository(databasePath);
    }

    /**
     * Opens the temporary database with foreign keys enabled.
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
     * Loads an SQL file from application resources.
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
     * Runs a bundled SQL resource against the temporary
     * database in one transaction.
     */
    private void executeSqlResource(String resourcePath)
            throws Exception {

        StringBuilder cleaned = new StringBuilder();

        for (String line :
                loadResource(resourcePath).split("\\R")) {

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

            } catch (Exception exception) {

                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Retrieves prerequisite information for one course.
     */
    private PrerequisiteRepository.PrerequisiteInfo find(
            String code
    ) throws Exception {

        return repository.findPrerequisites(
                code,
                CAMPUS,
                YEAR
        );
    }

    @Test
    void structuredCourseReturnsCorrectPrerequisite()
            throws Exception {

        var info = find("CS1083");

        assertNotNull(info);
        assertEquals("CS1083", info.courseCode());
        assertEquals("STRUCTURED", info.status());

        assertEquals(1, info.groups().size());

        var group = info.groups().get(0);

        assertEquals(1, group.groupNumber());
        assertEquals(1, group.options().size());

        assertEquals(
                "CS1073",
                group.options().get(0).courseCode()
        );
    }

    @Test
    void courseWithNoPrerequisitesHasNoGroups()
            throws Exception {

        var info = find("CS1073");

        assertNotNull(info);
        assertEquals("NONE", info.status());

        assertTrue(info.groups().isEmpty());

        assertNotNull(info.notes());
    }

    @Test
    void multiGroupCoursePreservesAndStructure()
            throws Exception {

        var info = find("CS2413");

        assertNotNull(info);
        assertEquals("PARTIAL", info.status());

        assertEquals(3, info.groups().size());

        assertEquals(
                1,
                info.groups().get(0).groupNumber()
        );

        assertEquals(
                "CS1083",
                info.groups().get(0)
                        .options().get(0).courseCode()
        );

        assertEquals(
                "CS1543",
                info.groups().get(1)
                        .options().get(0).courseCode()
        );

        assertEquals(
                "CS1303",
                info.groups().get(2)
                        .options().get(0).courseCode()
        );

        assertTrue(info.notes().contains("MATH2203"));
    }

    @Test
    void partialCourseRetainsMissingAlternatives()
            throws Exception {

        var info = find("CS2383");

        assertNotNull(info);
        assertEquals("PARTIAL", info.status());
        assertEquals(2, info.groups().size());

        assertTrue(info.notes().contains("ECE4403"));
        assertTrue(info.notes().contains("MATH2203"));

        // The absent alternatives must not be invented.
        assertEquals(
                1,
                info.groups().get(0).options().size()
        );

        assertEquals(
                1,
                info.groups().get(1).options().size()
        );
    }

    @Test
    void courseLookupAcceptsSpacesAndLowercase()
            throws Exception {

        var info = find("cs 2043");

        assertNotNull(info);

        assertEquals("CS2043", info.courseCode());
        assertEquals("STRUCTURED", info.status());

        assertEquals(
                "CS1083",
                info.groups().get(0)
                        .options().get(0).courseCode()
        );
    }

    @Test
    void unavailableCourseOrCampusReturnsNull()
            throws Exception {

        assertNull(find("CS9999"));

        assertNull(
                repository.findPrerequisites(
                        "CS2263",
                        "Saint John",
                        YEAR
                )
        );

        assertNull(
                repository.findPrerequisites(
                        "CS2263",
                        CAMPUS,
                        "2025-2026"
                )
        );

        assertNull(find(""));

        assertNull(
                repository.findPrerequisites(
                        null,
                        CAMPUS,
                        YEAR
                )
        );
    }

    @Test
    void sourceAndCorequisiteNotesAreAvailable()
            throws Exception {

        var info = find("CS2253");

        assertNotNull(info);
        assertEquals("NONE", info.status());

        assertTrue(info.groups().isEmpty());

        assertNotNull(info.notes());
        assertTrue(info.notes().contains("co-requisite"));

        assertEquals(CAMPUS, info.campus());
        assertEquals(YEAR, info.academicYear());
        assertEquals(SOURCE_URL, info.sourceUrl());
    }

    @Test
    void optionRecordsCannotBeChangedThroughReturnedList()
            throws Exception {

        var info = find("CS1083");

        assertNotNull(info);

        List<PrerequisiteRepository.PrerequisiteGroup> groups =
                info.groups();

        assertThrows(
                UnsupportedOperationException.class,
                () -> groups.clear()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> groups.get(0).options().clear()
        );
    }
}
