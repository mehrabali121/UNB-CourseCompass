
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides read-only access to the UNB CourseCompass
 * academic course catalogue.
 *
 * All course data is retrieved from SQLite.
 * This class does not invent or generate academic records.
 */
public final class CourseRepository {

    private final Path databasePath;

    /**
     * Represents one course catalogue entry.
     */
    public record Course(
            int id,
            String code,
            String title,
            double creditHours,
            String academicYear,
            String campusName,
            String prerequisiteStatus,
            String offeringStatus,
            String sourceTitle,
            String sourceUrl,
            String verifiedOn
    ) {
    }

    /**
     * Uses the application's normal SQLite database.
     */
    public CourseRepository() {
        this.databasePath = null;
    }

    /**
     * Uses a supplied database, allowing isolated JUnit tests.
     */
    public CourseRepository(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        this.databasePath = databasePath;
    }

    /**
     * Opens the correct database with foreign keys enabled.
     */
    private Connection openConnection()
            throws SQLException, IOException {

        if (databasePath == null) {
            return DatabaseManager.getConnection();
        }

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
     * Searches courses by code or title.
     *
     * Optional filters:
     * - campusName: null means all campuses
     * - academicYear: null means all years
     *
     * Blank search text returns matching catalogue entries.
     */
    public List<Course> searchCourses(
            String searchText,
            String campusName,
            String academicYear
    ) throws SQLException, IOException {

        String query = searchText == null
                ? ""
                : searchText.trim().toLowerCase();

        String sql = """
                SELECT
                    c.course_id,
                    c.course_code,
                    c.course_title,
                    c.credit_hours,
                    c.academic_year,
                    ca.campus_name,
                    cc.prerequisite_status,
                    cc.offering_status,
                    s.source_title,
                    s.source_url,
                    s.verified_on
                FROM courses c
                JOIN course_campuses cc
                    ON cc.course_id = c.course_id
                JOIN campuses ca
                    ON ca.campus_id = cc.campus_id
                JOIN academic_sources s
                    ON s.source_id = cc.source_id
                WHERE (
                    LOWER(c.course_code) LIKE ?
                    OR LOWER(c.course_title) LIKE ?
                )
                  AND (? IS NULL OR ca.campus_name = ?)
                  AND (? IS NULL OR c.academic_year = ?)
                ORDER BY c.course_code, ca.campus_name
                """;

        List<Course> courses = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            String searchPattern = "%" + query + "%";

            statement.setString(1, searchPattern);
            statement.setString(2, searchPattern);
            statement.setString(3, campusName);
            statement.setString(4, campusName);
            statement.setString(5, academicYear);
            statement.setString(6, academicYear);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    courses.add(readCourse(result));
                }
            }
        }

        return courses;
    }

    /**
     * Finds one course by its exact code, campus, and year.
     *
     * Returns null when the course is not in our database.
     */
    public Course findByCode(
            String courseCode,
            String campusName,
            String academicYear
    ) throws SQLException, IOException {

        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    c.course_id,
                    c.course_code,
                    c.course_title,
                    c.credit_hours,
                    c.academic_year,
                    ca.campus_name,
                    cc.prerequisite_status,
                    cc.offering_status,
                    s.source_title,
                    s.source_url,
                    s.verified_on
                FROM courses c
                JOIN course_campuses cc
                    ON cc.course_id = c.course_id
                JOIN campuses ca
                    ON ca.campus_id = cc.campus_id
                JOIN academic_sources s
                    ON s.source_id = cc.source_id
                WHERE UPPER(c.course_code) = ?
                  AND ca.campus_name = ?
                  AND c.academic_year = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    courseCode.replaceAll("\\s+", "")
                            .toUpperCase()
            );

            statement.setString(2, campusName);
            statement.setString(3, academicYear);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return readCourse(result);
                }
            }
        }

        return null;
    }

    /**
     * Returns the academic years currently represented
     * in the course catalogue.
     */
    public List<String> findAcademicYears()
            throws SQLException, IOException {

        String sql = """
                SELECT DISTINCT academic_year
                FROM courses
                ORDER BY academic_year DESC
                """;

        List<String> years = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                years.add(result.getString("academic_year"));
            }
        }

        return years;
    }

    /**
     * Converts a SQL result row into a Java course record.
     */
    private Course readCourse(ResultSet result)
            throws SQLException {

        return new Course(
                result.getInt("course_id"),
                result.getString("course_code"),
                result.getString("course_title"),
                result.getDouble("credit_hours"),
                result.getString("academic_year"),
                result.getString("campus_name"),
                result.getString("prerequisite_status"),
                result.getString("offering_status"),
                result.getString("source_title"),
                result.getString("source_url"),
                result.getString("verified_on")
        );
    }
}
