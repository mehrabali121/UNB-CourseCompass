package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only MySQL term planning queries. Planning data is self-reported and
 * does not establish course offerings or registration eligibility.
 * Does not modify the existing SQLite term planning repository.
 */
public final class MySQLTermPlanRepository {
    public record TermPlan(int planId, int profileId, String planName,
                           String academicYear, String termName,
                           String createdAt, int courseCount, double totalCredits) { }

    public record PlannedCourse(int plannedCourseId, int planId, int courseId,
                                String courseCode, String courseTitle,
                                double creditHours, String academicYear) { }

    public List<TermPlan> findByProfile(int profileId) throws SQLException {
        requirePositive(profileId, "Profile ID");
        String sql = """
                SELECT tp.plan_id, tp.profile_id, tp.plan_name,
                       tp.academic_year, tp.term_name, tp.created_at,
                       COUNT(pc.planned_course_id) AS course_count,
                       COALESCE(SUM(c.credit_hours), 0) AS total_credits
                FROM term_plans tp
                LEFT JOIN planned_courses pc ON pc.plan_id = tp.plan_id
                LEFT JOIN courses c ON c.course_id = pc.course_id
                WHERE tp.profile_id = ?
                GROUP BY tp.plan_id, tp.profile_id, tp.plan_name,
                         tp.academic_year, tp.term_name, tp.created_at
                ORDER BY tp.plan_id DESC
                """;
        List<TermPlan> plans = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profileId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    plans.add(new TermPlan(
                            result.getInt("plan_id"),
                            result.getInt("profile_id"),
                            result.getString("plan_name"),
                            result.getString("academic_year"),
                            result.getString("term_name"),
                            result.getString("created_at"),
                            result.getInt("course_count"),
                            result.getDouble("total_credits")));
                }
            }
        }
        return List.copyOf(plans);
    }

    public List<PlannedCourse> findPlannedCourses(int profileId, int planId)
            throws SQLException {
        requirePositive(profileId, "Profile ID");
        requirePositive(planId, "Plan ID");
        String sql = """
                SELECT pc.planned_course_id, pc.plan_id, c.course_id,
                       c.course_code, c.course_title, c.credit_hours,
                       c.academic_year
                FROM planned_courses pc
                JOIN term_plans tp ON tp.plan_id = pc.plan_id
                JOIN courses c ON c.course_id = pc.course_id
                WHERE tp.profile_id = ? AND tp.plan_id = ?
                ORDER BY c.course_code, c.academic_year
                """;
        List<PlannedCourse> courses = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profileId);
            statement.setInt(2, planId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    courses.add(new PlannedCourse(
                            result.getInt("planned_course_id"),
                            result.getInt("plan_id"),
                            result.getInt("course_id"),
                            result.getString("course_code"),
                            result.getString("course_title"),
                            result.getDouble("credit_hours"),
                            result.getString("academic_year")));
                }
            }
        }
        return List.copyOf(courses);
    }

    private static void requirePositive(int id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero.");
        }
    }
}
