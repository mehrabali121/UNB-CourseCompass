package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Explicit MySQL profile creation and editing. No deletion operations.
 *
 * The caller owns the JDBC connection and its transaction. This class never
 * commits, rolls back, closes the connection, or changes auto-commit settings.
 * The existing SQLite profile repository is unaffected.
 */
public final class MySQLProfileWriter {

    public int createProfile(Connection connection, String name, int campusId,
                             Integer programId, String academicYear)
            throws SQLException {
        validate(connection, name, campusId, programId, academicYear);
        String sql = """
                INSERT INTO profiles
                    (profile_name, campus_id, program_id, academic_year)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            bindProfileFields(statement, name, campusId, programId, academicYear);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Profile insert affected an unexpected number of rows.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL did not return the new profile ID.");
                }
                return keys.getInt(1);
            }
        }
    }

    public boolean updateProfile(Connection connection, int profileId, String name,
                                 int campusId, Integer programId, String academicYear)
            throws SQLException {
        if (profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be positive.");
        }
        validate(connection, name, campusId, programId, academicYear);
        String sql = """
                UPDATE profiles
                SET profile_name = ?, campus_id = ?, program_id = ?, academic_year = ?
                WHERE profile_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProfileFields(statement, name, campusId, programId, academicYear);
            statement.setInt(5, profileId);
            return statement.executeUpdate() > 0;
        }
    }

    private static void bindProfileFields(PreparedStatement statement, String name,
                                          int campusId, Integer programId,
                                          String academicYear) throws SQLException {
        statement.setString(1, name.trim());
        statement.setInt(2, campusId);
        if (programId == null) {
            statement.setNull(3, Types.INTEGER);
        } else {
            statement.setInt(3, programId);
        }
        statement.setString(4, academicYear);
    }

    private static void validate(Connection connection, String name, int campusId,
                                 Integer programId, String academicYear)
            throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Profile name cannot be empty.");
        }
        if (name.trim().length() > 255) {
            throw new IllegalArgumentException("Profile name exceeds 255 characters.");
        }
        if (academicYear != null &&
                (academicYear.isBlank() || academicYear.length() > 20)) {
            throw new IllegalArgumentException("Invalid academic year.");
        }
        if (campusId <= 0) {
            throw new IllegalArgumentException("Campus ID must be positive.");
        }
        if (programId != null) {
            if (academicYear == null) {
                throw new IllegalArgumentException(
                        "Select an academic year before selecting a program.");
            }
            String sql = """
                    SELECT COUNT(*) FROM programs
                    WHERE program_id = ? AND campus_id = ?
                      AND academic_year = ? AND source_id IS NOT NULL
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, programId);
                statement.setInt(2, campusId);
                statement.setString(3, academicYear);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new IllegalArgumentException(
                                "Program is not verified for this campus and year.");
                    }
                }
            }
        }
        // MySQL foreign keys reject campus IDs not present in campuses.
    }
}
