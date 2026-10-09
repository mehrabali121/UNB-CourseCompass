package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only MySQL access to self-reported completed courses.
 * A completion does not prove a grade, a transcript, or prerequisite eligibility.
 * The SQLite CompletedCourseRepository remains unchanged.
 */
public final class MySQLCompletedCourseRepository {

    public record CompletedCourse(
            int completionId,
            int profileId,
            int courseId,
            String courseCode,
            String courseTitle,
            double creditHours,
            String academicYear,
            String completedOn
    ) { }

    /**
     * Finds all completed courses owned by a profile. An unknown profile returns
     * an empty list. The query cannot modify data.
     */
    public List<CompletedCourse> findByProfile(int profileId) throws SQLException {
        if (profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be positive.");
        }
        String sql = """
                SELECT cc.completion_id, cc.profile_id, c.course_id,
                       c.course_code, c.course_title, c.credit_hours,
                       c.academic_year, cc.completed_on
                FROM completed_courses cc
                JOIN courses c ON c.course_id = cc.course_id
                WHERE cc.profile_id = ?
                ORDER BY c.course_code, c.academic_year, cc.completion_id
                """;
        List<CompletedCourse> results = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, profileId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        results.add(new CompletedCourse(
                                rows.getInt("completion_id"),
                                rows.getInt("profile_id"),
                                rows.getInt("course_id"),
                                rows.getString("course_code"),
                                rows.getString("course_title"),
                                rows.getDouble("credit_hours"),
                                rows.getString("academic_year"),
                                rows.getString("completed_on")));
                    }
                }
            }
        }
        return List.copyOf(results);
    }
}
