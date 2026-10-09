
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages completed courses for student profiles.
 *
 * Records are stored in SQLite and linked to both
 * an existing profile and an existing catalogue course.
 *
 * Completion records are self-reported information.
 * They do not prove grades or registration eligibility.
 */
public final class CompletedCourseRepository {

    private final Path databasePath;

    /**
     * Represents one completed course belonging to a profile.
     */
    public record CompletedCourse(
            int completionId,
            int profileId,
            int courseId,
            String courseCode,
            String courseTitle,
            double creditHours,
            String academicYear,
            String completedOn
    ) {
    }

    /**
     * Uses the application's normal SQLite database.
     */
    public CompletedCourseRepository() {
        this.databasePath = null;
    }

    /**
     * Uses a supplied database for isolated testing.
     */
    public CompletedCourseRepository(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        this.databasePath = databasePath;
    }

    /**
     * Opens SQLite with foreign key enforcement enabled.
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
     * Adds a course completion to a saved student profile.
     *
     * The course must exist in the requested catalogue year
     * and must be associated with the profile's campus.
     *
     * Returns true when a new record is inserted.
     * Returns false when that completion already exists.
     *
     * Throws IllegalArgumentException for invalid input
     * or an unavailable profile/course combination.
     */
    public boolean addCompletedCourse(
            int profileId,
            String courseCode,
            String academicYear,
            String completedOn
    ) throws SQLException, IOException {

        validateProfileId(profileId);

        String normalizedCode = normalizeCode(courseCode);
        validateAcademicYear(academicYear);
        validateDate(completedOn);

        String normalizedDate = completedOn == null
                || completedOn.isBlank()
                ? null
                : completedOn.trim();

        try (Connection connection = openConnection()) {

            int courseId = findCourseForProfile(
                    connection,
                    profileId,
                    normalizedCode,
                    academicYear
            );

            if (courseId == -1) {
                throw new IllegalArgumentException(
                        "The profile or course was not found, "
                        + "or the course is not associated with "
                        + "the profile's campus and selected "
                        + "catalogue year."
                );
            }

            String sql = """
                    INSERT OR IGNORE INTO completed_courses (
                        profile_id,
                        course_id,
                        completed_on
                    )
                    VALUES (?, ?, ?)
                    """;

            try (PreparedStatement statement =
                    connection.prepareStatement(sql)) {

                statement.setInt(1, profileId);
                statement.setInt(2, courseId);
                statement.setString(3, normalizedDate);

                return statement.executeUpdate() == 1;
            }
        }
    }

    /**
     * Finds a catalogue course matching a profile's campus.
     *
     * A course ID is returned only when both the profile
     * and course are present and compatible by campus.
     */
    private int findCourseForProfile(
            Connection connection,
            int profileId,
            String courseCode,
            String academicYear
    ) throws SQLException {

        String sql = """
                SELECT c.course_id
                FROM profiles p
                JOIN course_campuses cc
                    ON cc.campus_id = p.campus_id
                JOIN courses c
                    ON c.course_id = cc.course_id
                WHERE p.profile_id = ?
                  AND UPPER(c.course_code) = ?
                  AND c.academic_year = ?
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);
            statement.setString(2, courseCode);
            statement.setString(3, academicYear);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return result.getInt("course_id");
                }
            }
        }

        return -1;
    }

    /**
     * Lists the completed courses for one profile.
     *
     * Returns an empty list when the profile has no
     * saved course completions.
     */
    public List<CompletedCourse> findByProfile(
            int profileId
    ) throws SQLException, IOException {

        validateProfileId(profileId);

        String sql = """
                SELECT
                    cc.completion_id,
                    cc.profile_id,
                    c.course_id,
                    c.course_code,
                    c.course_title,
                    c.credit_hours,
                    c.academic_year,
                    cc.completed_on
                FROM completed_courses cc
                JOIN courses c
                    ON c.course_id = cc.course_id
                WHERE cc.profile_id = ?
                ORDER BY c.course_code, c.academic_year
                """;

        List<CompletedCourse> courses = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    courses.add(
                            new CompletedCourse(
                                    result.getInt("completion_id"),
                                    result.getInt("profile_id"),
                                    result.getInt("course_id"),
                                    result.getString("course_code"),
                                    result.getString("course_title"),
                                    result.getDouble("credit_hours"),
                                    result.getString("academic_year"),
                                    result.getString("completed_on")
                            )
                    );
                }
            }
        }

        return List.copyOf(courses);
    }

    /**
     * Removes one completion owned by the specified profile.
     *
     * Returns false if that record does not exist or belongs
     * to another profile.
     */
    public boolean removeCompletedCourse(
            int profileId,
            int completionId
    ) throws SQLException, IOException {

        validateProfileId(profileId);

        if (completionId <= 0) {
            throw new IllegalArgumentException(
                    "Completion ID must be greater than zero."
            );
        }

        String sql = """
                DELETE FROM completed_courses
                WHERE completion_id = ?
                  AND profile_id = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, completionId);
            statement.setInt(2, profileId);

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Normalizes a course code such as "cs 1083".
     */
    private static String normalizeCode(String courseCode) {

        if (courseCode == null || courseCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Course code cannot be empty."
            );
        }

        return courseCode
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    /**
     * Validates the selected academic catalogue year.
     */
    private static void validateAcademicYear(
            String academicYear
    ) {

        if (academicYear == null
                || !academicYear.matches("\\d{4}-\\d{4}")) {

            throw new IllegalArgumentException(
                    "Academic year must use YYYY-YYYY."
            );
        }

        int firstYear = Integer.parseInt(
                academicYear.substring(0, 4)
        );

        int secondYear = Integer.parseInt(
                academicYear.substring(5)
        );

        if (secondYear != firstYear + 1) {
            throw new IllegalArgumentException(
                    "Academic years must be consecutive."
            );
        }
    }

    /**
     * Validates an optional ISO completion date.
     */
    private static void validateDate(String completedOn) {

        if (completedOn == null || completedOn.isBlank()) {
            return;
        }

        try {
            LocalDate.parse(completedOn.trim());

        } catch (DateTimeParseException exception) {

            throw new IllegalArgumentException(
                    "Completion date must use YYYY-MM-DD."
            );
        }
    }

    /**
     * Validates an existing profile ID argument.
     */
    private static void validateProfileId(int profileId) {

        if (profileId <= 0) {
            throw new IllegalArgumentException(
                    "Profile ID must be greater than zero."
            );
        }
    }
}
