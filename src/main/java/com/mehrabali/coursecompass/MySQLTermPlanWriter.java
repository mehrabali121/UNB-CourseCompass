package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * MySQL term-plan insert operations. The caller owns the connection and must
 * explicitly commit or roll back its transaction. No delete methods are exposed.
 * A plan is unofficial and does not confirm course availability or eligibility.
 */
public final class MySQLTermPlanWriter {

    public int createPlan(Connection connection, int profileId, String name,
                          String academicYear, String termName) throws SQLException {
        requireConnection(connection);
        positive(profileId, "Profile ID");
        String cleanName = required(name, "Plan name");
        if (cleanName.length() > 255) {
            throw new IllegalArgumentException("Plan name exceeds 255 characters.");
        }
        String year = validateYear(academicYear);
        String term = validateTerm(termName);

        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT academic_year FROM profiles WHERE profile_id = ?")) {
            lookup.setInt(1, profileId);
            try (ResultSet result = lookup.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Student profile not found.");
                }
                String profileYear = result.getString(1);
                if (profileYear != null && !profileYear.isBlank()
                        && !profileYear.equals(year)) {
                    throw new IllegalArgumentException(
                            "Plan year must match the profile's saved academic year.");
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO term_plans (profile_id, plan_name, academic_year, term_name) "
                        + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, profileId);
            insert.setString(2, cleanName);
            insert.setString(3, year);
            insert.setString(4, term);
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Unexpected term-plan insert count.");
            }
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL did not return the term-plan ID.");
                }
                return keys.getInt(1);
            }
        }
    }

    /**
     * Adds an existing catalogue course for the plan's year and profile campus.
     * Returns false if the course is already in this plan.
     */
    public boolean addCourse(Connection connection, int profileId,
                             int planId, String courseCode) throws SQLException {
        requireConnection(connection);
        positive(profileId, "Profile ID");
        positive(planId, "Plan ID");
        String code = required(courseCode, "Course code")
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (code.length() > 30) {
            throw new IllegalArgumentException("Course code exceeds 30 characters.");
        }

        String lookupSql = """
                SELECT c.course_id
                FROM term_plans tp
                JOIN profiles p ON p.profile_id = tp.profile_id
                JOIN courses c ON c.academic_year = tp.academic_year
                JOIN course_campuses cc
                  ON cc.course_id = c.course_id AND cc.campus_id = p.campus_id
                WHERE tp.plan_id = ? AND tp.profile_id = ?
                  AND UPPER(REPLACE(c.course_code, ' ', '')) = ?
                LIMIT 1
                """;
        int courseId;
        try (PreparedStatement lookup = connection.prepareStatement(lookupSql)) {
            lookup.setInt(1, planId);
            lookup.setInt(2, profileId);
            lookup.setString(3, code);
            try (ResultSet result = lookup.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                            "Course not found for this profile's campus and plan year.");
                }
                courseId = result.getInt(1);
            }
        }
        // A no-op ON DUPLICATE KEY UPDATE can report one affected row with
        // Connector/J's CLIENT_FOUND_ROWS behavior, so never rely on row counts
        // from a no-op upsert to distinguish a duplicate from an insertion.
        String duplicateSql = "SELECT 1 FROM planned_courses "
                + "WHERE plan_id = ? AND course_id = ?";
        try (PreparedStatement existing = connection.prepareStatement(duplicateSql)) {
            existing.setInt(1, planId);
            existing.setInt(2, courseId);
            try (ResultSet result = existing.executeQuery()) {
                if (result.next()) {
                    return false;
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO planned_courses (plan_id, course_id) VALUES (?, ?)")) {
            insert.setInt(1, planId);
            insert.setInt(2, courseId);
            return insert.executeUpdate() == 1;
        } catch (SQLException exception) {
            // The unique (plan_id, course_id) key also protects against
            // a concurrent duplicate inserted after the existence check.
            if (exception.getErrorCode() == 1062 && "23000".equals(exception.getSQLState())) {
                return false;
            }
            throw exception;
        }
    }

    private static void requireConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
    }

    private static void positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        return value.trim();
    }

    private static String validateYear(String value) {
        String year = required(value, "Academic year");
        if (!year.matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException("Academic year must be YYYY-YYYY.");
        }
        int first = Integer.parseInt(year.substring(0, 4));
        int second = Integer.parseInt(year.substring(5));
        if (second != first + 1) {
            throw new IllegalArgumentException("Academic years must be consecutive.");
        }
        return year;
    }

    private static String validateTerm(String value) {
        return switch (required(value, "Term").toLowerCase(Locale.ROOT)) {
            case "fall" -> "Fall";
            case "winter" -> "Winter";
            case "summer" -> "Summer";
            case "other" -> "Other";
            default -> throw new IllegalArgumentException(
                    "Term must be Fall, Winter, Summer, or Other.");
        };
    }
}
