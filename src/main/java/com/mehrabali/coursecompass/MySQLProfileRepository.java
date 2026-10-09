package com.mehrabali.coursecompass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to migrated student profiles in MySQL.
 * This does not create, update, delete, or seed records.
 * The existing SQLite ProfileRepository remains unchanged.
 */
public final class MySQLProfileRepository {

    public record Profile(
            int id,
            String name,
            int campusId,
            String campusName,
            Integer programId,
            String programName,
            String academicYear
    ) { }

    public record Campus(int id, String name) { }

    public record Program(
            int id,
            String name,
            int campusId,
            String academicYear,
            String auditStatus
    ) { }

    public List<Campus> findCampuses() throws SQLException {
        String sql = "SELECT campus_id, campus_name FROM campuses ORDER BY campus_id";
        List<Campus> result = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Campus(rows.getInt("campus_id"), rows.getString("campus_name")));
            }
        }
        return result;
    }

    /** Lists only programs with a recorded academic source. */
    public List<Program> findPrograms(int campusId, String academicYear) throws SQLException {
        String sql = """
                SELECT program_id, program_name, campus_id, academic_year, audit_status
                FROM programs
                WHERE campus_id = ? AND academic_year = ? AND source_id IS NOT NULL
                ORDER BY program_name
                """;
        List<Program> result = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, campusId);
            statement.setString(2, academicYear);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Program(
                            rows.getInt("program_id"),
                            rows.getString("program_name"),
                            rows.getInt("campus_id"),
                            rows.getString("academic_year"),
                            rows.getString("audit_status")));
                }
            }
        }
        return result;
    }

    public List<Profile> findAll() throws SQLException {
        String sql = """
                SELECT p.profile_id, p.profile_name, p.campus_id, c.campus_name,
                       p.program_id, pr.program_name, p.academic_year
                FROM profiles p
                JOIN campuses c ON c.campus_id = p.campus_id
                LEFT JOIN programs pr ON pr.program_id = p.program_id
                ORDER BY p.profile_id
                """;
        List<Profile> result = new ArrayList<>();
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int rawProgramId = rows.getInt("program_id");
                Integer programId = rows.wasNull() ? null : rawProgramId;
                result.add(new Profile(
                        rows.getInt("profile_id"),
                        rows.getString("profile_name"),
                        rows.getInt("campus_id"),
                        rows.getString("campus_name"),
                        programId,
                        rows.getString("program_name"),
                        rows.getString("academic_year")));
            }
        }
        return result;
    }

    /** Returns the number of completed courses without showing student data. */
    public int countCompletedCourses(int profileId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM completed_courses WHERE profile_id = ?";
        try (Connection connection = MySQLConnectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profileId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Count query returned no row.");
                }
                return rows.getInt(1);
            }
        }
    }
}
