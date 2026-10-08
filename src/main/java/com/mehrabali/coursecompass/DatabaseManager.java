
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the local SQLite database for UNB CourseCompass.
 *
 * Responsibilities:
 * - Create the local data directory.
 * - Open SQLite database connections.
 * - Enable foreign key enforcement.
 * - Initialize tables using schema.sql.
 * - Verify database structure and integrity.
 */
public final class DatabaseManager {

    private static final Path DATABASE_PATH =
            Path.of("data", "coursecompass.db");

    private static final String SCHEMA_RESOURCE =
            "/db/schema.sql";

    private DatabaseManager() {
        // Prevent creating DatabaseManager objects.
    }

    /**
     * Opens a database connection with foreign keys enabled.
     *
     * @return an open SQLite connection
     * @throws SQLException if the database cannot be opened
     * @throws IOException if the data directory cannot be created
     */
    public static Connection getConnection()
            throws SQLException, IOException {

        Files.createDirectories(DATABASE_PATH.getParent());

        String databaseUrl = "jdbc:sqlite:"
                + DATABASE_PATH.toAbsolutePath();

        Connection connection =
                DriverManager.getConnection(databaseUrl);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }

    /**
     * Creates the database tables from the bundled schema.
     * Existing tables and student records are preserved.
     *
     * @throws SQLException if SQL execution fails
     * @throws IOException if schema.sql cannot be read
     */
    public static void initializeDatabase()
            throws SQLException, IOException {

        String schema = loadSchema();

        try (Connection connection = getConnection()) {

            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {

                // The Version 1 schema contains ordinary SQL
                // statements separated by semicolons.
                // Comment-only lines are removed first.
                for (String sql : schema.split(";")) {

                    String command = sql.trim();

                    if (!command.isEmpty()) {
                        statement.execute(command);
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
     * Reads schema.sql from the application resources.
     */
    private static String loadSchema() throws IOException {

        try (InputStream input =
                DatabaseManager.class.getResourceAsStream(
                        SCHEMA_RESOURCE)) {

            if (input == null) {
                throw new IOException(
                        "Database schema resource was not found: "
                        + SCHEMA_RESOURCE
                );
            }

            String content = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            StringBuilder cleaned = new StringBuilder();

            for (String line : content.split("\\R")) {

                String trimmed = line.trim();

                if (!trimmed.startsWith("--")) {
                    cleaned.append(line).append('\n');
                }
            }

            return cleaned.toString();
        }
    }

    /**
     * Checks the database structure and integrity.
     */
    public static void verifyDatabase()
            throws SQLException, IOException {

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master "
                    + "WHERE type = 'table' "
                    + "AND name NOT LIKE 'sqlite_%'")) {

                result.next();

                int tableCount = result.getInt(1);

                System.out.println(
                        "Application tables found: " + tableCount
                );

                if (tableCount != 13) {
                    throw new SQLException(
                            "Expected 13 application tables, found "
                            + tableCount
                    );
                }
            }

            try (ResultSet result = statement.executeQuery(
                    "PRAGMA foreign_keys")) {

                result.next();

                if (result.getInt(1) != 1) {
                    throw new SQLException(
                            "Foreign key enforcement is disabled."
                    );
                }
            }

            try (ResultSet result = statement.executeQuery(
                    "PRAGMA foreign_key_check")) {

                if (result.next()) {
                    throw new SQLException(
                            "Foreign key violations were found."
                    );
                }
            }

            try (ResultSet result = statement.executeQuery(
                    "PRAGMA integrity_check")) {

                if (!result.next()
                        || !"ok".equalsIgnoreCase(
                                result.getString(1))) {

                    throw new SQLException(
                            "SQLite database integrity check failed."
                    );
                }
            }

            System.out.println(
                    "Foreign key enforcement: ENABLED"
            );

            System.out.println(
                    "Foreign key violations: NONE"
            );

            System.out.println(
                    "Database integrity: OK"
            );
        }
    }

    /**
     * Database initialization and diagnostic entry point.
     */
    public static void main(String[] args) {

        System.out.println(
                "UNB CourseCompass - Database Initialization"
        );

        try {

            initializeDatabase();
            verifyDatabase();

            System.out.println(
                    "Database initialization successful."
            );

        } catch (SQLException | IOException exception) {

            System.err.println(
                    "Database initialization failed: "
                    + exception.getMessage()
            );

            System.exit(1);
        }
    }
}
