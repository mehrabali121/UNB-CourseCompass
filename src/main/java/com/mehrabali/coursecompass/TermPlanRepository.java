
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
import java.util.Locale;

/**
 * Stores student term plans and their planned courses in SQLite.
 *
 * Term plans are unofficial planning records only.
 * They do not verify prerequisites, grades, actual course
 * offerings, registration, or program completion.
 */
public final class TermPlanRepository {

    public record TermPlan(
            int planId,
            int profileId,
            String planName,
            String academicYear,
            String termName,
            String createdAt,
            int courseCount,
            double totalCredits
    ) {
    }

    public record PlannedCourse(
            int plannedCourseId,
            int planId,
            int courseId,
            String courseCode,
            String courseTitle,
            double creditHours,
            String academicYear
    ) {
    }

    private final Path databasePath;

    /**
     * Uses the normal CourseCompass database.
     */
    public TermPlanRepository() {
        this.databasePath = null;
    }

    /**
     * Uses an isolated SQLite database for testing.
     */
    public TermPlanRepository(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        this.databasePath = databasePath;
    }

    /**
     * Opens a connection with foreign key enforcement enabled.
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
     * Creates a term plan for an existing student.
     *
     * A saved student catalogue year must agree with the
     * requested plan year.
     *
     * @return the new plan ID
     */
    public int createPlan(
            int profileId,
            String planName,
            String academicYear,
            String termName
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");

        String cleanName = requireText(planName, "Plan name");
        String cleanYear = validateYear(academicYear);
        String cleanTerm = validateTerm(termName);

        String profileSql = """
                SELECT academic_year
                FROM profiles
                WHERE profile_id = ?
                """;

        String insertSql = """
                INSERT INTO term_plans (
                    profile_id,
                    plan_name,
                    academic_year,
                    term_name
                )
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = openConnection()) {

            try (PreparedStatement profileStatement =
                    connection.prepareStatement(profileSql)) {

                profileStatement.setInt(1, profileId);

                try (ResultSet result =
                        profileStatement.executeQuery()) {

                    if (!result.next()) {
                        throw new IllegalArgumentException(
                                "Student profile not found."
                        );
                    }

                    String savedYear =
                            result.getString("academic_year");

                    if (savedYear != null
                            && !savedYear.isBlank()
                            && !savedYear.equals(cleanYear)) {

                        throw new IllegalArgumentException(
                                "Plan year must match the student's "
                                + "saved catalogue year."
                        );
                    }
                }
            }

            try (PreparedStatement statement =
                    connection.prepareStatement(
                            insertSql,
                            Statement.RETURN_GENERATED_KEYS
                    )) {

                statement.setInt(1, profileId);
                statement.setString(2, cleanName);
                statement.setString(3, cleanYear);
                statement.setString(4, cleanTerm);

                statement.executeUpdate();

                try (ResultSet keys =
                        statement.getGeneratedKeys()) {

                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }

                throw new SQLException(
                        "Plan was inserted, but its ID was not returned."
                );
            }
        }
    }

    /**
     * Lists all plans owned by the selected student.
     */
    public List<TermPlan> findByProfile(int profileId)
            throws SQLException, IOException {

        validateId(profileId, "Profile ID");

        String sql = """
                SELECT
                    tp.plan_id,
                    tp.profile_id,
                    tp.plan_name,
                    tp.academic_year,
                    tp.term_name,
                    tp.created_at,
                    COUNT(pc.planned_course_id) AS course_count,
                    COALESCE(SUM(c.credit_hours), 0) AS total_credits
                FROM term_plans tp
                LEFT JOIN planned_courses pc
                    ON pc.plan_id = tp.plan_id
                LEFT JOIN courses c
                    ON c.course_id = pc.course_id
                WHERE tp.profile_id = ?
                GROUP BY
                    tp.plan_id,
                    tp.profile_id,
                    tp.plan_name,
                    tp.academic_year,
                    tp.term_name,
                    tp.created_at
                ORDER BY tp.plan_id DESC
                """;

        List<TermPlan> plans = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    plans.add(readPlan(result));
                }
            }
        }

        return List.copyOf(plans);
    }

    /**
     * Finds a plan only when it belongs to the supplied profile.
     *
     * @return the plan, or null when not found for this student
     */
    public TermPlan findPlan(
            int profileId,
            int planId
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");
        validateId(planId, "Plan ID");

        String sql = """
                SELECT
                    tp.plan_id,
                    tp.profile_id,
                    tp.plan_name,
                    tp.academic_year,
                    tp.term_name,
                    tp.created_at,
                    COUNT(pc.planned_course_id) AS course_count,
                    COALESCE(SUM(c.credit_hours), 0) AS total_credits
                FROM term_plans tp
                LEFT JOIN planned_courses pc
                    ON pc.plan_id = tp.plan_id
                LEFT JOIN courses c
                    ON c.course_id = pc.course_id
                WHERE tp.profile_id = ?
                  AND tp.plan_id = ?
                GROUP BY
                    tp.plan_id,
                    tp.profile_id,
                    tp.plan_name,
                    tp.academic_year,
                    tp.term_name,
                    tp.created_at
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);
            statement.setInt(2, planId);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return readPlan(result);
                }
            }
        }

        return null;
    }

    /**
     * Adds a course to a student's term plan.
     *
     * The course must be loaded in the local catalogue,
     * match the plan year, and have a campus applicability
     * record for the student's saved campus.
     *
     * @return true if added, false if already in the plan
     */
    public boolean addCourse(
            int profileId,
            int planId,
            String courseCode
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");
        validateId(planId, "Plan ID");

        String cleanCode = requireText(
                courseCode,
                "Course code"
        ).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);

        String lookupSql = """
                SELECT c.course_id
                FROM term_plans tp
                JOIN profiles p
                    ON p.profile_id = tp.profile_id
                JOIN courses c
                    ON c.academic_year = tp.academic_year
                JOIN course_campuses cc
                    ON cc.course_id = c.course_id
                   AND cc.campus_id = p.campus_id
                WHERE tp.plan_id = ?
                  AND tp.profile_id = ?
                  AND UPPER(
                      REPLACE(c.course_code, ' ', '')
                  ) = ?
                LIMIT 1
                """;

        String insertSql = """
                INSERT OR IGNORE INTO planned_courses (
                    plan_id,
                    course_id
                )
                VALUES (?, ?)
                """;

        try (Connection connection = openConnection()) {

            int courseId;

            try (PreparedStatement lookup =
                    connection.prepareStatement(lookupSql)) {

                lookup.setInt(1, planId);
                lookup.setInt(2, profileId);
                lookup.setString(3, cleanCode);

                try (ResultSet result = lookup.executeQuery()) {

                    if (!result.next()) {

                        throw new IllegalArgumentException(
                                "No matching loaded course exists "
                                + "for this plan, catalogue year, "
                                + "and student's campus."
                        );
                    }

                    courseId = result.getInt("course_id");
                }
            }

            try (PreparedStatement insert =
                    connection.prepareStatement(insertSql)) {

                insert.setInt(1, planId);
                insert.setInt(2, courseId);

                return insert.executeUpdate() == 1;
            }
        }
    }

    /**
     * Lists courses in one student-owned term plan.
     */
    public List<PlannedCourse> findPlannedCourses(
            int profileId,
            int planId
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");
        validateId(planId, "Plan ID");

        String sql = """
                SELECT
                    pc.planned_course_id,
                    pc.plan_id,
                    c.course_id,
                    c.course_code,
                    c.course_title,
                    c.credit_hours,
                    c.academic_year
                FROM planned_courses pc
                JOIN term_plans tp
                    ON tp.plan_id = pc.plan_id
                JOIN courses c
                    ON c.course_id = pc.course_id
                WHERE tp.profile_id = ?
                  AND tp.plan_id = ?
                ORDER BY c.course_code
                """;

        List<PlannedCourse> courses = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);
            statement.setInt(2, planId);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    courses.add(
                            new PlannedCourse(
                                    result.getInt("planned_course_id"),
                                    result.getInt("plan_id"),
                                    result.getInt("course_id"),
                                    result.getString("course_code"),
                                    result.getString("course_title"),
                                    result.getDouble("credit_hours"),
                                    result.getString("academic_year")
                            )
                    );
                }
            }
        }

        return List.copyOf(courses);
    }

    /**
     * Removes a planned-course entry only from a plan
     * belonging to the supplied student.
     *
     * @return true when an entry was removed
     */
    public boolean removeCourse(
            int profileId,
            int planId,
            int plannedCourseId
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");
        validateId(planId, "Plan ID");
        validateId(plannedCourseId, "Planned-course ID");

        String sql = """
                DELETE FROM planned_courses
                WHERE planned_course_id = ?
                  AND plan_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM term_plans
                      WHERE plan_id = ?
                        AND profile_id = ?
                  )
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, plannedCourseId);
            statement.setInt(2, planId);
            statement.setInt(3, planId);
            statement.setInt(4, profileId);

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Returns the planned credit total for one student-owned plan.
     *
     * @return the total, or zero when the plan does not exist
     */
    public double getTotalCredits(
            int profileId,
            int planId
    ) throws SQLException, IOException {

        TermPlan plan = findPlan(profileId, planId);

        return plan == null ? 0.0 : plan.totalCredits();
    }

    /**
     * Deletes a term plan belonging to the supplied student.
     * Planned courses are removed by the database's
     * ON DELETE CASCADE constraint.
     *
     * @return true if a plan was deleted
     */
    public boolean deletePlan(
            int profileId,
            int planId
    ) throws SQLException, IOException {

        validateId(profileId, "Profile ID");
        validateId(planId, "Plan ID");

        String sql = """
                DELETE FROM term_plans
                WHERE plan_id = ?
                  AND profile_id = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, planId);
            statement.setInt(2, profileId);

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Converts one aggregated database row to a term-plan record.
     */
    private static TermPlan readPlan(ResultSet result)
            throws SQLException {

        return new TermPlan(
                result.getInt("plan_id"),
                result.getInt("profile_id"),
                result.getString("plan_name"),
                result.getString("academic_year"),
                result.getString("term_name"),
                result.getString("created_at"),
                result.getInt("course_count"),
                result.getDouble("total_credits")
        );
    }

    /**
     * Validates a required positive database identifier.
     */
    private static void validateId(
            int id,
            String fieldName
    ) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero."
            );
        }
    }

    /**
     * Validates and trims required input.
     */
    private static String requireText(
            String value,
            String fieldName
    ) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName + " cannot be empty."
            );
        }

        return value.trim();
    }

    /**
     * Accepts only consecutive-year catalogue labels.
     */
    private static String validateYear(String year) {

        String cleanYear = requireText(
                year,
                "Academic year"
        );

        if (!cleanYear.matches("\\d{4}-\\d{4}")) {

            throw new IllegalArgumentException(
                    "Academic year must use YYYY-YYYY."
            );
        }

        int start = Integer.parseInt(
                cleanYear.substring(0, 4)
        );

        int end = Integer.parseInt(
                cleanYear.substring(5)
        );

        if (end != start + 1) {

            throw new IllegalArgumentException(
                    "Academic year must contain consecutive years."
            );
        }

        return cleanYear;
    }

    /**
     * Accepts only term names supported by schema.sql.
     */
    private static String validateTerm(String termName) {

        String cleanTerm = requireText(
                termName,
                "Term name"
        );

        return switch (cleanTerm.toLowerCase(Locale.ROOT)) {

            case "fall" -> "Fall";
            case "winter" -> "Winter";
            case "summer" -> "Summer";
            case "other" -> "Other";

            default -> throw new IllegalArgumentException(
                    "Term must be Fall, Winter, Summer, or Other."
            );
        };
    }
}
