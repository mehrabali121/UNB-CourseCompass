package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Verifies MySQL term-plan writes only inside a transaction that is rolled back.
 * Never commits. Existing migrated profile/completion records are not edited.
 */
public final class MySQLTermPlanRollbackCheck {
    private MySQLTermPlanRollbackCheck() { }

    public static void main(String[] args) throws SQLException {
        try (Connection connection = MySQLConnectionManager.getConnection()) {
            int beforeProfiles = count(connection, "profiles");
            int beforeCompletions = count(connection, "completed_courses");
            int beforePlans = count(connection, "term_plans");
            int beforePlannedCourses = count(connection, "planned_courses");
            if (beforeProfiles != 1 || beforeCompletions != 1
                    || beforePlans != 0 || beforePlannedCourses != 0) {
                throw new IllegalStateException(
                        "Unexpected database baseline. Test aborted without writing.");
            }
            int profileId = findMigratedProfile(connection);
            if (profileId != 2) {
                throw new IllegalStateException(
                        "Expected migrated profile ID 2. Test aborted without writing.");
            }

            connection.setAutoCommit(false);
            try {
                MySQLTermPlanWriter writer = new MySQLTermPlanWriter();
                int planId = writer.createPlan(connection, profileId,
                        "ROLLBACK TEST - NOT SAVED", "2026-2027", "Fall");
                if (!writer.addCourse(connection, profileId, planId, "cs 1203")) {
                    throw new IllegalStateException("Expected initial course insert.");
                }
                if (writer.addCourse(connection, profileId, planId, "CS1203")) {
                    throw new IllegalStateException("Duplicate was not rejected.");
                }
                if (count(connection, "term_plans") != beforePlans + 1
                        || count(connection, "planned_courses") != beforePlannedCourses + 1) {
                    throw new IllegalStateException("Temporary planning records were not created.");
                }
            } finally {
                connection.rollback();
            }

            if (count(connection, "profiles") != beforeProfiles
                    || count(connection, "completed_courses") != beforeCompletions
                    || count(connection, "term_plans") != beforePlans
                    || count(connection, "planned_courses") != beforePlannedCourses) {
                throw new IllegalStateException("Record counts changed after rollback.");
            }
            System.out.println("MYSQL TERM PLAN WRITE TEST PASSED (ROLLBACK VERIFIED)");
            System.out.println("Profiles retained: " + beforeProfiles);
            System.out.println("Completed courses retained: " + beforeCompletions);
            System.out.println("Term plans retained: " + beforePlans);
            System.out.println("Planned courses retained: " + beforePlannedCourses);
            System.out.println("No test plan was retained.");
        }
    }

    private static int findMigratedProfile(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT profile_id FROM profiles WHERE profile_id = 2 AND academic_year = '2026-2027'" );
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : -1;
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        // All callers pass fixed internal table identifiers, never user input.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
