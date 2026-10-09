
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Safely imports the bundled prerequisite SQL data.
 *
 * Requires the verified course catalogue to be loaded first.
 * All changes execute inside one SQLite transaction.
 */
public final class PrerequisiteSeedImporter {

    private static final String RESOURCE_PATH =
            "/db/seed_prerequisites.sql";

    private PrerequisiteSeedImporter() {
        // Utility class.
    }

    /**
     * Loads the complete prerequisite SQL file.
     */
    private static String loadSeed() throws IOException {

        try (InputStream input =
                PrerequisiteSeedImporter.class
                        .getResourceAsStream(RESOURCE_PATH)) {

            if (input == null) {
                throw new IOException(
                        "Prerequisite seed not found: "
                        + RESOURCE_PATH
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
     */
    private static String removeComments(String content) {

        StringBuilder result = new StringBuilder();

        for (String line : content.split("\\R")) {

            if (!line.trim().startsWith("--")) {
                result.append(line).append('\n');
            }
        }

        return result.toString();
    }

    /**
     * Counts rows in an existing table.
     */
    private static int countRows(
            Connection connection,
            String tableName
    ) throws SQLException {

        String sql = switch (tableName) {
            case "courses" ->
                    "SELECT COUNT(*) FROM courses "
                    + "WHERE academic_year = '2026-2027'";

            case "prerequisite_groups" ->
                    "SELECT COUNT(*) FROM prerequisite_groups";

            case "prerequisite_options" ->
                    "SELECT COUNT(*) FROM prerequisite_options";

            default -> throw new IllegalArgumentException(
                    "Unsupported table: " + tableName
            );
        };

        try (Statement statement =
                     connection.createStatement();
             ResultSet result =
                     statement.executeQuery(sql)) {

            result.next();
            return result.getInt(1);
        }
    }

    /**
     * Imports prerequisite data with validation and rollback.
     */
    public static void importPrerequisites()
            throws SQLException, IOException {

        String sqlContent = removeComments(loadSeed());

        try (Connection connection =
                     DatabaseManager.getConnection()) {

            connection.setAutoCommit(false);

            try {

                int courseCount =
                        countRows(connection, "courses");

                if (courseCount != 10) {
                    throw new SQLException(
                            "Expected 10 catalogue courses "
                            + "for 2026-2027, found "
                            + courseCount
                            + ". Import the course catalogue first."
                    );
                }

                try (Statement statement =
                             connection.createStatement()) {

                    for (String part : sqlContent.split(";")) {

                        String sql = part.trim();

                        if (!sql.isEmpty()) {
                            statement.execute(sql);
                        }
                    }
                }

                int groupCount = countRows(
                        connection,
                        "prerequisite_groups"
                );

                int optionCount = countRows(
                        connection,
                        "prerequisite_options"
                );

                if (groupCount != 9 || optionCount != 9) {
                    throw new SQLException(
                            "Unexpected prerequisite counts. "
                            + "Groups: " + groupCount
                            + ", Options: " + optionCount
                    );
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
     * Displays the current prerequisite database counts.
     */
    private static void printSummary()
            throws SQLException, IOException {

        try (Connection connection =
                     DatabaseManager.getConnection()) {

            System.out.println(
                    "Prerequisite groups: "
                    + countRows(connection, "prerequisite_groups")
            );

            System.out.println(
                    "Prerequisite options: "
                    + countRows(connection, "prerequisite_options")
            );
        }
    }

    /**
     * Standalone entry point for controlled imports.
     */
    public static void main(String[] args) {

        System.out.println(
                "UNB CourseCompass - Prerequisite Import"
        );

        try {

            DatabaseManager.initializeDatabase();

            importPrerequisites();

            printSummary();

            DatabaseManager.verifyDatabase();

            System.out.println();
            System.out.println(
                    "Prerequisite import completed successfully."
            );

        } catch (SQLException | IOException exception) {

            System.err.println(
                    "Prerequisite import failed: "
                    + exception.getMessage()
            );

            System.exit(1);
        }
    }
}
