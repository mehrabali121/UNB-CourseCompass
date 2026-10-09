
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
import java.util.Objects;

/**
 * Read-only reporting of the academic data actually stored
 * in CourseCompass.
 *
 * Counts describe local database coverage. They do not
 * describe completeness of the entire UNB catalogue.
 */
public final class DataCoverageService {

    public record AcademicSource(
            int id,
            String title,
            String url,
            String academicYear,
            String verifiedOn,
            String notes
    ) {
    }

    public record CoverageReport(
            List<CourseRepository.Course> courses,
            List<AcademicSource> sources,
            List<String> academicYears,
            int noPrerequisites,
            int structuredPrerequisites,
            int partialPrerequisites,
            int unknownPrerequisites,
            int verifiedOffered,
            int verifiedNotOffered,
            int unknownOfferings,
            int programCount,
            int supportedPrograms,
            int partialPrograms,
            int unsupportedPrograms,
            int programRequirementCount
    ) {
        public CoverageReport {
            courses = List.copyOf(courses);
            sources = List.copyOf(sources);
            academicYears = List.copyOf(academicYears);
        }

        public int courseRecordCount() {
            return courses.size();
        }

        public int sourceCount() {
            return sources.size();
        }

        public int fullyDescribedPrerequisiteCount() {
            return noPrerequisites + structuredPrerequisites;
        }
    }

    private final Path databasePath;
    private final CourseRepository courseRepository;

    public DataCoverageService() {
        this.databasePath = null;
        this.courseRepository = new CourseRepository();
    }

    public DataCoverageService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(
                databasePath,
                "Database path cannot be null."
        );
        this.courseRepository = new CourseRepository(databasePath);
    }

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
     * Builds a snapshot of the locally stored academic data.
     * Does not import, update, or remove any records.
     */
    public CoverageReport assess()
            throws SQLException, IOException {

        List<CourseRepository.Course> courses =
                courseRepository.searchCourses("", null, null);

        List<String> academicYears =
                courseRepository.findAcademicYears();

        int none = 0;
        int structured = 0;
        int partial = 0;
        int unknown = 0;

        int offered = 0;
        int notOffered = 0;
        int unknownOfferings = 0;

        for (CourseRepository.Course course : courses) {

            switch (course.prerequisiteStatus()) {
                case "NONE" -> none++;
                case "STRUCTURED" -> structured++;
                case "PARTIAL" -> partial++;
                case "UNKNOWN" -> unknown++;
                default -> throw new IllegalStateException(
                        "Unexpected prerequisite status: "
                        + course.prerequisiteStatus()
                );
            }

            switch (course.offeringStatus()) {
                case "VERIFIED_OFFERED" -> offered++;
                case "VERIFIED_NOT_OFFERED" -> notOffered++;
                case "UNKNOWN" -> unknownOfferings++;
                default -> throw new IllegalStateException(
                        "Unexpected offering status: "
                        + course.offeringStatus()
                );
            }
        }

        try (Connection connection = openConnection()) {

            List<AcademicSource> sources =
                    loadSources(connection);

            int programCount = countRows(connection, "programs");
            int supported = countPrograms(connection, "SUPPORTED");
            int partialPrograms = countPrograms(connection, "PARTIAL");
            int unsupported = countPrograms(connection, "UNSUPPORTED");

            int requirements = countRows(
                    connection,
                    "program_requirements"
            );

            return new CoverageReport(
                    courses,
                    sources,
                    academicYears,
                    none,
                    structured,
                    partial,
                    unknown,
                    offered,
                    notOffered,
                    unknownOfferings,
                    programCount,
                    supported,
                    partialPrograms,
                    unsupported,
                    requirements
            );
        }
    }

    private List<AcademicSource> loadSources(
            Connection connection
    ) throws SQLException {

        String sql = """
                SELECT source_id,
                       source_title,
                       source_url,
                       academic_year,
                       verified_on,
                       notes
                FROM academic_sources
                ORDER BY academic_year DESC,
                         verified_on DESC,
                         source_id
                """;

        List<AcademicSource> sources = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                sources.add(new AcademicSource(
                        result.getInt("source_id"),
                        result.getString("source_title"),
                        result.getString("source_url"),
                        result.getString("academic_year"),
                        result.getString("verified_on"),
                        result.getString("notes")
                ));
            }
        }

        return sources;
    }

    /**
     * Table names are fixed by this class, never user input.
     */
    private int countRows(
            Connection connection,
            String tableName
    ) throws SQLException {

        if (!"programs".equals(tableName)
                && !"program_requirements".equals(tableName)) {

            throw new IllegalArgumentException(
                    "Unsupported count table."
            );
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName
             )) {

            result.next();
            return result.getInt(1);
        }
    }

    private int countPrograms(
            Connection connection,
            String auditStatus
    ) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM programs
                WHERE audit_status = ?
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setString(1, auditStatus);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
