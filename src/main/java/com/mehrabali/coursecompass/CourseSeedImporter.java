
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Imports verified academic catalogue seed data into SQLite.
 *
 * The SQL seed contains source metadata and course records.
 * This importer does not generate or invent academic data.
 *
 * All SQL commands run inside one transaction.
 */
public final class CourseSeedImporter {

    private static final String SEED_RESOURCE =
            "/db/seed_courses.sql";

    private static final String ACADEMIC_YEAR = "2026-2027";

    private static final String SOURCE_URL =
            "https://www.unb.ca/academics/calendar/"
            + "undergraduate/current/frederictoncourses/"
            + "computer-science/index.html";

    private CourseSeedImporter() {
        // Utility class.
    }

    /**
     * Reads the SQL seed bundled with the application.
     */
    private static String loadSeed() throws IOException {

        try (InputStream input =
                CourseSeedImporter.class.getResourceAsStream(
                        SEED_RESOURCE)) {

            if (input == null) {
                throw new IOException(
                        "Course seed file was not found: "
                        + SEED_RESOURCE
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Removes full-line SQL comments.
     *
     * The bundled seed uses semicolons to separate
     * ordinary SQL statements.
     */
    private static String removeComments(String content) {

        StringBuilder cleaned = new StringBuilder();

        for (String line : content.split("\\R")) {

            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        return cleaned.toString();
    }

    /**
     * Imports the seed into the application's SQLite database.
     *
     * If any command fails, the transaction is rolled back.
     */
    public static void importCourses()
            throws SQLException, IOException {

        String seed = removeComments(loadSeed());

        try (Connection connection =
                DatabaseManager.getConnection()) {

            connection.setAutoCommit(false);

            try {

                try (Statement statement =
                        connection.createStatement()) {

                    for (String part : seed.split(";")) {

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
     * Counts imported course records for the configured
     * academic year and source URL.
     */
    public static int countImportedCourses()
            throws SQLException, IOException {

        String sql = """
                SELECT COUNT(*)
                FROM courses c
                JOIN academic_sources s
                    ON s.source_id = c.source_id
                WHERE c.academic_year = '2026-2027'
                  AND s.source_url = ?
                """;

        try (Connection connection =
                DatabaseManager.getConnection();
             var statement =
                connection.prepareStatement(sql)) {

            statement.setString(1, SOURCE_URL);

            try (ResultSet result =
                    statement.executeQuery()) {

                result.next();
                return result.getInt(1);
            }
        }
    }

    /**
     * Displays the imported courses and their credits.
     */
    public static void printImportedCourses()
            throws SQLException, IOException {

        String sql = """
                SELECT c.course_code,
                       c.course_title,
                       c.credit_hours
                FROM courses c
                JOIN academic_sources s
                    ON s.source_id = c.source_id
                WHERE c.academic_year = ?
                  AND s.source_url = ?
                ORDER BY c.course_code
                """;

        try (Connection connection =
                DatabaseManager.getConnection();
             var statement =
                connection.prepareStatement(sql)) {

            statement.setString(1, ACADEMIC_YEAR);
            statement.setString(2, SOURCE_URL);

            try (ResultSet result =
                    statement.executeQuery()) {

                while (result.next()) {

                    System.out.println(
                            result.getString("course_code")
                            + " | "
                            + result.getString("course_title")
                            + " | "
                            + result.getDouble("credit_hours")
                            + " credits"
                    );
                }
            }
        }
    }

    /**
     * Standalone entry point for controlled imports.
     */
    public static void main(String[] args) {

        System.out.println(
                "UNB CourseCompass - Course Data Import"
        );

        try {

            DatabaseManager.initializeDatabase();

            importCourses();

            int courseCount = countImportedCourses();

            System.out.println();
            System.out.println(
                    "Imported source course count: "
                    + courseCount
            );

            printImportedCourses();

            if (courseCount != 10) {
                throw new SQLException(
                        "Expected 10 source-linked courses, found "
                        + courseCount
                );
            }

            DatabaseManager.verifyDatabase();

            System.out.println();
            System.out.println(
                    "Course seed import completed successfully."
            );

        } catch (SQLException | IOException exception) {

            System.err.println(
                    "Course seed import failed: "
                    + exception.getMessage()
            );

            System.exit(1);
        }
    }
}
