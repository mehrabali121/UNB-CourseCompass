
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides SQLite operations for student profiles.
 *
 * Uses prepared statements to safely handle user input.
 * Program selection is optional until verified UNB program
 * information is available.
 *
 * Supports isolated temporary databases for automated tests.
 */
public final class ProfileRepository {

    private final Path testDatabasePath;

    /**
     * Creates a repository using the normal application database.
     */
    public ProfileRepository() {
        this.testDatabasePath = null;
    }

    /**
     * Creates a repository using a specified SQLite database.
     * Intended for isolated automated testing.
     */
    public ProfileRepository(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        this.testDatabasePath = databasePath;
    }

    /**
     * Opens the configured database with foreign keys enabled.
     */
    private Connection openConnection()
            throws SQLException, IOException {

        if (testDatabasePath == null) {
            return DatabaseManager.getConnection();
        }

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + testDatabasePath.toAbsolutePath()
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
     * Represents a saved student profile.
     */
    public record Profile(
            int id,
            String name,
            int campusId,
            String campusName,
            Integer programId,
            String programName,
            String academicYear
    ) {
    }

    /**
     * Represents a UNB campus.
     */
    public record Campus(
            int id,
            String name
    ) {
    }

    /**
     * Represents an academic program.
     */
    public record Program(
            int id,
            String name,
            int campusId,
            String academicYear,
            String auditStatus
    ) {
    }

    /**
     * Creates the two supported campus records if missing.
     */
    public void initializeCampuses()
            throws SQLException, IOException {

        String sql = """
                INSERT OR IGNORE INTO campuses
                    (campus_id, campus_name)
                VALUES (?, ?)
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, 1);
            statement.setString(2, "Fredericton");
            statement.executeUpdate();

            statement.setInt(1, 2);
            statement.setString(2, "Saint John");
            statement.executeUpdate();
        }
    }

    /**
     * Returns all supported campuses.
     */
    public List<Campus> findCampuses()
            throws SQLException, IOException {

        String sql = """
                SELECT campus_id, campus_name
                FROM campuses
                ORDER BY campus_id
                """;

        List<Campus> campuses = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                campuses.add(new Campus(
                        result.getInt("campus_id"),
                        result.getString("campus_name")
                ));
            }
        }

        return campuses;
    }

    /**
     * Lists programs with a recorded academic source for
     * the selected campus and academic year.
     *
     * A source record does not itself guarantee that
     * a complete degree audit is supported.
     */
    public List<Program> findPrograms(
            int campusId,
            String academicYear
    ) throws SQLException, IOException {

        String sql = """
                SELECT program_id,
                       program_name,
                       campus_id,
                       academic_year,
                       audit_status
                FROM programs
                WHERE campus_id = ?
                  AND academic_year = ?
                  AND source_id IS NOT NULL
                ORDER BY program_name
                """;

        List<Program> programs = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, campusId);
            statement.setString(2, academicYear);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    programs.add(new Program(
                            result.getInt("program_id"),
                            result.getString("program_name"),
                            result.getInt("campus_id"),
                            result.getString("academic_year"),
                            result.getString("audit_status")
                    ));
                }
            }
        }

        return programs;
    }

    /**
     * Creates a new student profile.
     *
     * Program and academic year may be null when unavailable.
     */
    public int createProfile(
            String name,
            int campusId,
            Integer programId,
            String academicYear
    ) throws SQLException, IOException {

        validateName(name);
        validateAcademicYear(academicYear);
        validateProgramSelection(
                campusId,
                programId,
                academicYear
        );

        String sql = """
                INSERT INTO profiles
                    (profile_name,
                     campus_id,
                     program_id,
                     academic_year)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(1, name.trim());
            statement.setInt(2, campusId);

            if (programId == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, programId);
            }

            statement.setString(4, academicYear);

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {

                if (keys.next()) {
                    return keys.getInt(1);
                }
            }

            throw new SQLException(
                    "Profile created, but its ID was not returned."
            );
        }
    }

    /**
     * Returns all saved profiles with campus and program names.
     */
    public List<Profile> findAll()
            throws SQLException, IOException {

        String sql = """
                SELECT p.profile_id,
                       p.profile_name,
                       p.campus_id,
                       c.campus_name,
                       p.program_id,
                       pr.program_name,
                       p.academic_year
                FROM profiles p
                JOIN campuses c
                    ON c.campus_id = p.campus_id
                LEFT JOIN programs pr
                    ON pr.program_id = p.program_id
                ORDER BY p.profile_id
                """;

        List<Profile> profiles = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                int rawProgramId =
                        result.getInt("program_id");

                Integer programId = result.wasNull()
                        ? null
                        : rawProgramId;

                profiles.add(new Profile(
                        result.getInt("profile_id"),
                        result.getString("profile_name"),
                        result.getInt("campus_id"),
                        result.getString("campus_name"),
                        programId,
                        result.getString("program_name"),
                        result.getString("academic_year")
                ));
            }
        }

        return profiles;
    }

    /**
     * Updates an existing student profile.
     *
     * Returns false when the specified profile does not exist.
     */
    public boolean updateProfile(
            int profileId,
            String name,
            int campusId,
            Integer programId,
            String academicYear
    ) throws SQLException, IOException {

        validateName(name);
        validateAcademicYear(academicYear);
        validateProgramSelection(
                campusId,
                programId,
                academicYear
        );

        String sql = """
                UPDATE profiles
                SET profile_name = ?,
                    campus_id = ?,
                    program_id = ?,
                    academic_year = ?
                WHERE profile_id = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name.trim());
            statement.setInt(2, campusId);

            if (programId == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, programId);
            }

            statement.setString(4, academicYear);
            statement.setInt(5, profileId);

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a student profile.
     *
     * Associated completed courses and term plans
     * are deleted according to schema foreign key rules.
     *
     * Returns false if the profile does not exist.
     */
    public boolean deleteProfile(int profileId)
            throws SQLException, IOException {

        String sql = """
                DELETE FROM profiles
                WHERE profile_id = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);

            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Rejects missing or whitespace-only profile names.
     */
    private void validateName(String name) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Profile name cannot be empty."
            );
        }
    }

    /**
     * Academic year is optional, but cannot be blank.
     */
    private void validateAcademicYear(
            String academicYear
    ) {

        if (academicYear != null
                && academicYear.isBlank()) {

            throw new IllegalArgumentException(
                    "Academic year cannot be blank."
            );
        }
    }

    /**
     * Ensures that a selected program has a source record
     * and belongs to the selected campus and academic year.
     */
    private void validateProgramSelection(
            int campusId,
            Integer programId,
            String academicYear
    ) throws SQLException, IOException {

        if (programId == null) {
            return;
        }

        if (academicYear == null) {
            throw new IllegalArgumentException(
                    "Select an academic year before a program."
            );
        }

        String sql = """
                SELECT COUNT(*)
                FROM programs
                WHERE program_id = ?
                  AND campus_id = ?
                  AND academic_year = ?
                  AND source_id IS NOT NULL
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, programId);
            statement.setInt(2, campusId);
            statement.setString(3, academicYear);

            try (ResultSet result =
                    statement.executeQuery()) {

                result.next();

                if (result.getInt(1) == 0) {
                    throw new IllegalArgumentException(
                            "Program is unavailable for the "
                            + "selected campus and academic year."
                    );
                }
            }
        }
    }
}
