
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for the UNB CourseCompass SQLite schema.
 *
 * All test databases are temporary.
 * Real student data is never used.
 */
public class DatabaseManagerTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Opens a temporary SQLite database.
     * This does not use the application's personal database.
     */
    private Connection openConnection(Path databasePath)
            throws SQLException {

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    /**
     * Creates a temporary database using the real schema.sql.
     */
    private Path createTestDatabase()
            throws SQLException, IOException {

        Path databasePath =
                temporaryDirectory.resolve("test.db");

        String schema;

        try (InputStream input = getClass()
                .getResourceAsStream("/db/schema.sql")) {

            assertNotNull(input, "schema.sql must exist");

            schema = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        StringBuilder cleaned = new StringBuilder();

        for (String line : schema.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Connection connection =
                openConnection(databasePath);
             Statement statement =
                connection.createStatement()) {

            for (String command : cleaned.toString().split(";")) {

                String sql = command.trim();

                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }

        return databasePath;
    }

    /**
     * Inserts fictional records needed for completion tests.
     */
    private void insertSampleCourseData(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                "INSERT INTO campuses (campus_id, campus_name) "
                + "VALUES (1, 'Fredericton')"
            );

            statement.executeUpdate(
                "INSERT INTO academic_sources "
                + "(source_id, source_title, source_url, "
                + "academic_year, verified_on) "
                + "VALUES (1, 'Fictional Test Source', "
                + "'https://example.com/test', "
                + "'TEST', '2026-10-08')"
            );

            statement.executeUpdate(
                "INSERT INTO courses "
                + "(course_id, course_code, course_title, "
                + "credit_hours, academic_year, source_id) "
                + "VALUES (1, 'TEST1000', 'Fictional Course', "
                + "3, 'TEST', 1)"
            );

            statement.executeUpdate(
                "INSERT INTO profiles "
                + "(profile_id, profile_name, campus_id) "
                + "VALUES (1, 'Test Student', 1)"
            );
        }
    }

    @Test
    void schemaCreatesThirteenTables() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT COUNT(*) FROM sqlite_master "
                 + "WHERE type = 'table' "
                 + "AND name NOT LIKE 'sqlite_%'")) {

            assertTrue(result.next());
            assertEquals(13, result.getInt(1));
        }
    }

    @Test
    void foreignKeysRejectInvalidCampus() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database);
             PreparedStatement statement =
                 connection.prepareStatement(
                     "INSERT INTO profiles "
                     + "(profile_name, campus_id) "
                     + "VALUES (?, ?)")) {

            statement.setString(1, "Test Student");
            statement.setInt(2, 9999);

            assertThrows(
                    SQLException.class,
                    statement::executeUpdate
            );
        }
    }

    @Test
    void duplicateCampusNamesAreRejected() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                "INSERT INTO campuses VALUES (1, 'Fredericton')"
            );

            assertThrows(
                SQLException.class,
                () -> statement.executeUpdate(
                    "INSERT INTO campuses VALUES (2, 'Fredericton')"
                )
            );
        }
    }

    @Test
    void invalidCampusNamesAreRejected() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement()) {

            assertThrows(
                SQLException.class,
                () -> statement.executeUpdate(
                    "INSERT INTO campuses VALUES (1, 'Invalid Campus')"
                )
            );
        }
    }

    @Test
    void studentRecordsPersistAfterReconnection() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                "INSERT INTO campuses VALUES (1, 'Fredericton')"
            );

            statement.executeUpdate(
                "INSERT INTO profiles "
                + "(profile_name, campus_id) "
                + "VALUES ('Fictional Student', 1)"
            );
        }

        assertTrue(Files.exists(database));

        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT profile_name FROM profiles")) {

            assertTrue(result.next());
            assertEquals(
                "Fictional Student",
                result.getString("profile_name")
            );
        }
    }

    @Test
    void duplicateCompletedCoursesAreRejected() throws Exception {

        Path database = createTestDatabase();

        try (Connection connection = openConnection(database)) {

            insertSampleCourseData(connection);

            try (Statement statement = connection.createStatement()) {

                statement.executeUpdate(
                    "INSERT INTO completed_courses "
                    + "(profile_id, course_id) VALUES (1, 1)"
                );

                assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate(
                        "INSERT INTO completed_courses "
                        + "(profile_id, course_id) VALUES (1, 1)"
                    )
                );
            }
        }
    }
}
