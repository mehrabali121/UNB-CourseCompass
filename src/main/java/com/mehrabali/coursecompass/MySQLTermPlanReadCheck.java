package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Read-only verification of the migrated MySQL term planning tables. */
public final class MySQLTermPlanReadCheck {
    private MySQLTermPlanReadCheck() { }

    public static void main(String[] args) throws SQLException {
        MySQLTermPlanRepository repository = new MySQLTermPlanRepository();
        List<MySQLTermPlanRepository.TermPlan> plans = repository.findByProfile(2);
        int planCount = count("SELECT COUNT(*) FROM term_plans");
        int plannedCourseCount = count("SELECT COUNT(*) FROM planned_courses");
        int profileCount = count("SELECT COUNT(*) FROM profiles");
        int completionCount = count("SELECT COUNT(*) FROM completed_courses");

        if (profileCount != 1 || completionCount != 1
                || planCount != 0 || plannedCourseCount != 0 || !plans.isEmpty()) {
            throw new IllegalStateException(
                    "Unexpected migrated data: inspect records before continuing. "
                    + "Profiles=" + profileCount + ", completions=" + completionCount
                    + ", plans=" + planCount + ", planned courses=" + plannedCourseCount);
        }
        if (!repository.findPlannedCourses(2, Integer.MAX_VALUE).isEmpty()) {
            throw new IllegalStateException("Unexpected courses in nonexistent plan.");
        }
        System.out.println("UNB CourseCompass - MySQL Term Plan Read Check");
        System.out.println("Profiles retained: " + profileCount);
        System.out.println("Completed courses retained: " + completionCount);
        System.out.println("Term plans: " + planCount);
        System.out.println("Planned courses: " + plannedCourseCount);
        System.out.println("MYSQL TERM PLAN READ CHECK PASSED (READ ONLY)");
    }

    private static int count(String sql) throws SQLException {
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Count query returned no rows.");
            }
            return result.getInt(1);
        }
    }
}
