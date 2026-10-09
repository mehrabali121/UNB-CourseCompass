
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

/**
 * Read-only access to prerequisite information in SQLite.
 *
 * Prerequisite groups use AND logic.
 * Options inside the same group use OR logic.
 *
 * This repository retrieves information only.
 * It does not determine registration eligibility.
 */
public final class PrerequisiteRepository {

    private final Path databasePath;

    /**
     * One prerequisite option within a group.
     */
    public record PrerequisiteOption(
            int courseId,
            String courseCode,
            String courseTitle
    ) {
    }

    /**
     * One AND group containing one or more OR options.
     */
    public record PrerequisiteGroup(
            int groupNumber,
            List<PrerequisiteOption> options
    ) {
    }

    /**
     * Prerequisite information for one course.
     */
    public record PrerequisiteInfo(
            String courseCode,
            String courseTitle,
            String campus,
            String academicYear,
            String status,
            String notes,
            String sourceUrl,
            List<PrerequisiteGroup> groups
    ) {
    }

    /**
     * Uses the main CourseCompass database.
     */
    public PrerequisiteRepository() {
        this.databasePath = null;
    }

    /**
     * Uses a separate database for isolated testing.
     */
    public PrerequisiteRepository(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        this.databasePath = databasePath;
    }

    /**
     * Opens a SQLite connection with foreign keys enabled.
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
     * Finds prerequisite information for an exact course,
     * campus, and academic year.
     *
     * Returns null if the course is not in our local catalogue.
     */
    public PrerequisiteInfo findPrerequisites(
            String courseCode,
            String campusName,
            String academicYear
    ) throws SQLException, IOException {

        if (courseCode == null || courseCode.isBlank()
                || campusName == null || campusName.isBlank()
                || academicYear == null || academicYear.isBlank()) {

            return null;
        }

        String normalizedCode = courseCode
                .replaceAll("\\s+", "")
                .toUpperCase();

        String courseSql = """
                SELECT
                    c.course_code,
                    c.course_title,
                    c.academic_year,
                    ca.campus_name,
                    cc.course_campus_id,
                    cc.prerequisite_status,
                    cc.prerequisite_notes,
                    s.source_url
                FROM courses c
                JOIN course_campuses cc
                    ON cc.course_id = c.course_id
                JOIN campuses ca
                    ON ca.campus_id = cc.campus_id
                JOIN academic_sources s
                    ON s.source_id = cc.source_id
                WHERE UPPER(c.course_code) = ?
                  AND ca.campus_name = ?
                  AND c.academic_year = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(courseSql)) {

            statement.setString(1, normalizedCode);
            statement.setString(2, campusName);
            statement.setString(3, academicYear);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    return null;
                }

                String code = result.getString("course_code");
                String title = result.getString("course_title");
                String year = result.getString("academic_year");
                String campus = result.getString("campus_name");
                int courseCampusId =
                        result.getInt("course_campus_id");
                String status =
                        result.getString("prerequisite_status");
                String notes =
                        result.getString("prerequisite_notes");
                String source =
                        result.getString("source_url");

                List<PrerequisiteGroup> groups =
                        loadGroups(connection, courseCampusId);

                return new PrerequisiteInfo(
                        code,
                        title,
                        campus,
                        year,
                        status,
                        notes,
                        source,
                        List.copyOf(groups)
                );
            }
        }
    }

    /**
     * Loads all prerequisite groups for one course/campus.
     *
     * Every group must be satisfied (AND).
     * Options within a group are alternatives (OR).
     */
    private List<PrerequisiteGroup> loadGroups(
            Connection connection,
            int courseCampusId
    ) throws SQLException {

        String sql = """
                SELECT
                    pg.group_number,
                    required.course_id,
                    required.course_code,
                    required.course_title
                FROM prerequisite_groups pg
                JOIN prerequisite_options po
                    ON po.group_id = pg.group_id
                JOIN courses required
                    ON required.course_id = po.required_course_id
                WHERE pg.course_campus_id = ?
                ORDER BY pg.group_number, required.course_code
                """;

        List<PrerequisiteGroup> groups = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, courseCampusId);

            try (ResultSet result = statement.executeQuery()) {

                int currentGroupNumber = -1;
                List<PrerequisiteOption> currentOptions =
                        new ArrayList<>();

                while (result.next()) {

                    int groupNumber =
                            result.getInt("group_number");

                    if (groupNumber != currentGroupNumber) {

                        if (currentGroupNumber != -1) {
                            groups.add(
                                    new PrerequisiteGroup(
                                            currentGroupNumber,
                                            List.copyOf(currentOptions)
                                    )
                            );
                        }

                        currentGroupNumber = groupNumber;
                        currentOptions = new ArrayList<>();
                    }

                    currentOptions.add(
                            new PrerequisiteOption(
                                    result.getInt("course_id"),
                                    result.getString("course_code"),
                                    result.getString("course_title")
                            )
                    );
                }

                if (currentGroupNumber != -1) {

                    groups.add(
                            new PrerequisiteGroup(
                                    currentGroupNumber,
                                    List.copyOf(currentOptions)
                            )
                    );
                }
            }
        }

        return groups;
    }
}
