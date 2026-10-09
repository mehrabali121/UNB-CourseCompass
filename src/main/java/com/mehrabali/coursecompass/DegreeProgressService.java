package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only reporting of self-reported course completions against
 * only those degree requirements actually stored in the database.
 * This is not an official degree audit or graduation decision.
 */
public final class DegreeProgressService {

    public enum RequirementStatus {
        RECORDED_MATCH,
        NOT_RECORDED,
        REPORTED_CREDITS_AT_LEAST_MINIMUM,
        REPORTED_CREDITS_BELOW_MINIMUM,
        NO_ELIGIBLE_COURSE_MAPPING
    }

    public record RequirementProgress(
            int requirementId,
            String requirementName,
            String requirementType,
            String requiredCourseCode,
            Double minimumCredits,
            double matchedCredits,
            List<String> matchedCourseCodes,
            RequirementStatus status,
            String notes
    ) {
        public RequirementProgress {
            matchedCourseCodes = List.copyOf(matchedCourseCodes);
        }
    }

    public record ProgressReport(
            int profileId,
            String studentName,
            String campus,
            String academicYear,
            Integer programId,
            String programName,
            String auditStatus,
            int completedCourseCount,
            double recordedCreditHours,
            List<CompletedCourseRepository.CompletedCourse> completedCourses,
            List<RequirementProgress> requirements
    ) {
        public ProgressReport {
            completedCourses = List.copyOf(completedCourses);
            requirements = List.copyOf(requirements);
        }
    }

    private record ProgramDetails(int id, String name, String auditStatus) {
    }

    private final Path databasePath;
    private final ProfileRepository profileRepository;
    private final CompletedCourseRepository completedRepository;

    public DegreeProgressService() {
        this.databasePath = null;
        this.profileRepository = new ProfileRepository();
        this.completedRepository = new CompletedCourseRepository();
    }

    public DegreeProgressService(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path cannot be null.");
        }
        this.databasePath = databasePath;
        this.profileRepository = new ProfileRepository(databasePath);
        this.completedRepository = new CompletedCourseRepository(databasePath);
    }

    private Connection openConnection() throws SQLException, IOException {
        if (databasePath == null) {
            return DatabaseManager.getConnection();
        }

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        );
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    /**
     * Reports recorded credits for any valid student profile.
     * Returns NO_PROGRAM_SELECTED when no program is saved.
     */
    public ProgressReport assess(int profileId) throws SQLException, IOException {
        if (profileId <= 0) {
            throw new IllegalArgumentException("Profile ID must be greater than zero.");
        }

        ProfileRepository.Profile profile = null;
        for (ProfileRepository.Profile candidate : profileRepository.findAll()) {
            if (candidate.id() == profileId) {
                profile = candidate;
                break;
            }
        }
        if (profile == null) {
            throw new IllegalArgumentException("Student profile not found.");
        }

        List<CompletedCourseRepository.CompletedCourse> completed =
                completedRepository.findByProfile(profileId);
        double recordedCredits = completed.stream()
                .mapToDouble(CompletedCourseRepository.CompletedCourse::creditHours)
                .sum();

        if (profile.programId() == null) {
            return new ProgressReport(
                    profile.id(), profile.name(), profile.campusName(),
                    profile.academicYear(), null, null, "NO_PROGRAM_SELECTED",
                    completed.size(), recordedCredits, completed, List.of()
            );
        }

        try (Connection connection = openConnection()) {
            ProgramDetails program = findProgram(
                    connection, profile.programId(), profile.campusId(),
                    profile.academicYear()
            );
            if (program == null) {
                throw new IllegalStateException(
                        "The selected program does not match the student's campus "
                        + "and catalogue year."
                );
            }

            List<RequirementProgress> requirements = findRequirements(
                    connection, profile.id(), program.id(), completed
            );

            return new ProgressReport(
                    profile.id(), profile.name(), profile.campusName(),
                    profile.academicYear(), program.id(), program.name(),
                    program.auditStatus(), completed.size(), recordedCredits,
                    completed, requirements
            );
        }
    }

    private ProgramDetails findProgram(
            Connection connection,
            int programId,
            int campusId,
            String academicYear
    ) throws SQLException {
        String sql = """
                SELECT program_id, program_name, audit_status
                FROM programs
                WHERE program_id = ?
                  AND campus_id = ?
                  AND academic_year = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, programId);
            statement.setInt(2, campusId);
            statement.setString(3, academicYear);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new ProgramDetails(
                        result.getInt("program_id"),
                        result.getString("program_name"),
                        result.getString("audit_status")
                );
            }
        }
    }

    private List<RequirementProgress> findRequirements(
            Connection connection,
            int profileId,
            int programId,
            List<CompletedCourseRepository.CompletedCourse> completed
    ) throws SQLException {
        String sql = """
                SELECT pr.requirement_id,
                       pr.requirement_name,
                       pr.requirement_type,
                       pr.required_course_id,
                       pr.minimum_credits,
                       pr.notes,
                       c.course_code AS required_course_code
                FROM program_requirements pr
                LEFT JOIN courses c ON c.course_id = pr.required_course_id
                WHERE pr.program_id = ?
                ORDER BY pr.requirement_id
                """;

        List<RequirementProgress> requirements = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, programId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int requirementId = result.getInt("requirement_id");
                    String name = result.getString("requirement_name");
                    String type = result.getString("requirement_type");
                    String notes = result.getString("notes");

                    if ("REQUIRED_COURSE".equals(type)) {
                        int requiredId = result.getInt("required_course_id");
                        String requiredCode = result.getString("required_course_code");

                        List<String> matches = new ArrayList<>();
                        double matchedCredits = 0.0;
                        for (CompletedCourseRepository.CompletedCourse course : completed) {
                            if (course.courseId() == requiredId) {
                                matches.add(course.courseCode());
                                matchedCredits += course.creditHours();
                            }
                        }

                        RequirementStatus status = matches.isEmpty()
                                ? RequirementStatus.NOT_RECORDED
                                : RequirementStatus.RECORDED_MATCH;
                        requirements.add(new RequirementProgress(
                                requirementId, name, type, requiredCode, null,
                                matchedCredits, matches, status, notes
                        ));
                    } else if ("MINIMUM_CREDITS".equals(type)) {
                        requirements.add(assessMinimumCredits(
                                connection, profileId, requirementId, name,
                                result.getDouble("minimum_credits"), notes
                        ));
                    } else {
                        throw new IllegalStateException(
                                "Unknown program requirement type: " + type
                        );
                    }
                }
            }
        }
        return requirements;
    }

    /**
     * Credit category matches are based exclusively on explicit
     * requirement_courses rows, not on an assumed degree total.
     */
    private RequirementProgress assessMinimumCredits(
            Connection connection,
            int profileId,
            int requirementId,
            String name,
            double minimum,
            String notes
    ) throws SQLException {
        String sql = """
                SELECT c.course_code, c.credit_hours,
                       CASE WHEN cc.completion_id IS NULL
                            THEN 0 ELSE 1 END AS is_recorded
                FROM requirement_courses rc
                JOIN courses c ON c.course_id = rc.course_id
                LEFT JOIN completed_courses cc
                       ON cc.course_id = c.course_id
                      AND cc.profile_id = ?
                WHERE rc.requirement_id = ?
                ORDER BY c.course_code
                """;

        List<String> matches = new ArrayList<>();
        double matchedCredits = 0.0;
        int eligibleCourseCount = 0;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profileId);
            statement.setInt(2, requirementId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    eligibleCourseCount++;
                    if (result.getInt("is_recorded") == 1) {
                        matches.add(result.getString("course_code"));
                        matchedCredits += result.getDouble("credit_hours");
                    }
                }
            }
        }

        RequirementStatus status;
        if (eligibleCourseCount == 0) {
            status = RequirementStatus.NO_ELIGIBLE_COURSE_MAPPING;
        } else if (matchedCredits >= minimum) {
            status = RequirementStatus.REPORTED_CREDITS_AT_LEAST_MINIMUM;
        } else {
            status = RequirementStatus.REPORTED_CREDITS_BELOW_MINIMUM;
        }

        return new RequirementProgress(
                requirementId, name, "MINIMUM_CREDITS", null, minimum,
                matchedCredits, matches, status, notes
        );
    }
}
