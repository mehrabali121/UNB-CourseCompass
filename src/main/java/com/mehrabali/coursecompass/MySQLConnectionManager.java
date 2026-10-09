package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Connects to the existing CourseCompass MySQL database.
 * This class does not initialize, seed, or modify any tables.
 */
public final class MySQLConnectionManager {

    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/coursecompass";
    private static final String USERNAME = "coursecompass_app";
    private static final int EXPECTED_TABLES = 13;
    private static final int EXPECTED_FOREIGN_KEYS = 21;

    private MySQLConnectionManager() {
    }

    public static Connection getConnection() throws SQLException {
        String password = System.getenv("COURSECOMPASS_MYSQL_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SQLException(
                    "COURSECOMPASS_MYSQL_PASSWORD is not set."
            );
        }
        return DriverManager.getConnection(URL, USERNAME, password);
    }

    /**
     * Performs read-only verification of the migrated database.
     */
    public static void main(String[] args) {
        try (Connection connection = getConnection()) {
            connection.setReadOnly(true);
            System.out.println("UNB CourseCompass - MySQL Connection Check");
            System.out.println("Connected as: " + connection.getMetaData().getUserName());
            System.out.println("Database: " + connection.getCatalog());

            int tables = count(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_type = 'BASE TABLE'");
            int foreignKeys = count(connection,
                    "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND REFERENCED_TABLE_NAME IS NOT NULL");
            int courses = count(connection, "SELECT COUNT(*) FROM courses");
            int profiles = count(connection, "SELECT COUNT(*) FROM profiles");
            int completions = count(connection,
                    "SELECT COUNT(*) FROM completed_courses");

            System.out.println("Tables: " + tables);
            System.out.println("Foreign keys: " + foreignKeys);
            System.out.println("Courses: " + courses);
            System.out.println("Profiles: " + profiles);
            System.out.println("Completed courses: " + completions);

            if (tables != EXPECTED_TABLES || foreignKeys != EXPECTED_FOREIGN_KEYS
                    || courses != 10 || profiles != 1 || completions != 1) {
                throw new SQLException("Migrated database verification failed.");
            }
            System.out.println("MYSQL CONNECTION CHECK PASSED (READ ONLY)");
        } catch (SQLException exception) {
            System.err.println("MYSQL CONNECTION CHECK FAILED: "
                    + exception.getMessage());
            System.exit(1);
        }
    }

    private static int count(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Query returned no result: " + sql);
            }
            return result.getInt(1);
        }
    }
}
