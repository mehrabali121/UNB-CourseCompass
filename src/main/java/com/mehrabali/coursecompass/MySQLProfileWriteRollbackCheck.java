package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tests MySQL profile insert and update in a transaction that ALWAYS rolls back.
 * Never updates or deletes the migrated student profile.
 */
public final class MySQLProfileWriteRollbackCheck {

    private MySQLProfileWriteRollbackCheck() { }

    public static void main(String[] args) {
        try (Connection connection = MySQLConnectionManager.getConnection()) {
            int originalProfiles = count(connection, "SELECT COUNT(*) FROM profiles");
            int originalCompletions = count(connection,
                    "SELECT COUNT(*) FROM completed_courses");
            if (originalProfiles != 1 || originalCompletions != 1) {
                throw new SQLException("Unexpected starting data; test aborted.");
            }

            connection.setAutoCommit(false);
            boolean successful = false;
            try {
                MySQLProfileWriter writer = new MySQLProfileWriter();
                int temporaryId = writer.createProfile(connection,
                        "Fictional Rollback Test Student", 1, null, "2026-2027");
                require(temporaryId != 2, "Test must not change migrated profile ID 2.");
                require(count(connection, "SELECT COUNT(*) FROM profiles")
                                == originalProfiles + 1,
                        "Temporary insert was not visible in its transaction.");
                require(writer.updateProfile(connection, temporaryId,
                                "Updated Fictional Rollback Test", 2, null, "2027-2028"),
                        "Temporary profile update failed.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT profile_name, campus_id, academic_year "
                                + "FROM profiles WHERE profile_id = ?")) {
                    statement.setInt(1, temporaryId);
                    try (ResultSet result = statement.executeQuery()) {
                        require(result.next(), "Temporary profile not found.");
                        require("Updated Fictional Rollback Test".equals(
                                result.getString("profile_name")), "Name update missing.");
                        require(result.getInt("campus_id") == 2,
                                "Campus update missing.");
                        require("2027-2028".equals(result.getString("academic_year")),
                                "Year update missing.");
                    }
                }
                successful = true;
            } finally {
                // Intentionally never commit, including when an assertion fails.
                connection.rollback();
                connection.setAutoCommit(true);
            }

            require(successful, "Test did not finish normally.");
            require(count(connection, "SELECT COUNT(*) FROM profiles") == originalProfiles,
                    "Profile count changed after rollback.");
            require(count(connection, "SELECT COUNT(*) FROM completed_courses")
                            == originalCompletions,
                    "Completed-course count changed after rollback.");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM profiles WHERE profile_id = 2")) {
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next() && result.getInt(1) == 1,
                            "Migrated profile ID 2 is missing.");
                }
            }
            System.out.println("MYSQL PROFILE WRITE TEST PASSED (ROLLBACK VERIFIED)");
            System.out.println("Profiles retained: " + originalProfiles);
            System.out.println("Completed courses retained: " + originalCompletions);
            System.out.println("No test profile was retained.");
        } catch (SQLException | IllegalStateException exception) {
            System.err.println("MYSQL PROFILE WRITE TEST FAILED: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("No count result.");
            }
            return result.getInt(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
