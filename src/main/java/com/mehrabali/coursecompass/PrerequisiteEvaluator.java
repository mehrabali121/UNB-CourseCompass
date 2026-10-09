
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates modeled prerequisite groups against a student's
 * self-reported completed courses.
 *
 * This class never determines official registration eligibility.
 * Grade requirements, co-requisites, and incomplete alternatives
 * must be checked using official university information.
 */
public final class PrerequisiteEvaluator {

    public enum Status {
        RECORDED_REQUIREMENTS_MET,
        MISSING_REQUIREMENTS,
        PARTIAL_DATA,
        UNKNOWN,
        NO_LISTED_PREREQUISITES
    }

    /**
     * A record of how one AND group compares with
     * the student's completed-course records.
     */
    public record GroupResult(
            int groupNumber,
            List<String> acceptedCourseCodes,
            List<String> recordedCourseCodes,
            boolean hasRecordedMatch
    ) {
        public GroupResult {
            acceptedCourseCodes = List.copyOf(acceptedCourseCodes);
            recordedCourseCodes = List.copyOf(recordedCourseCodes);
        }
    }

    /**
     * The result of a prerequisite evaluation.
     */
    public record Evaluation(
            int profileId,
            String courseCode,
            String courseTitle,
            String campus,
            String academicYear,
            Status status,
            List<GroupResult> groups,
            String notes,
            String sourceUrl,
            String explanation
    ) {
        public Evaluation {
            groups = List.copyOf(groups);
        }
    }

    private final PrerequisiteRepository prerequisiteRepository;
    private final CompletedCourseRepository completedRepository;
    private final ProfileRepository profileRepository;

    /**
     * Uses the application's regular database.
     */
    public PrerequisiteEvaluator() {

        prerequisiteRepository =
                new PrerequisiteRepository();

        completedRepository =
                new CompletedCourseRepository();

        profileRepository =
                new ProfileRepository();
    }

    /**
     * Uses an isolated database, primarily for tests.
     */
    public PrerequisiteEvaluator(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        prerequisiteRepository =
                new PrerequisiteRepository(databasePath);

        completedRepository =
                new CompletedCourseRepository(databasePath);

        profileRepository =
                new ProfileRepository(databasePath);
    }

    /**
     * Evaluates modeled prerequisite information.
     *
     * The student profile must exist and match the
     * requested campus and saved catalogue year.
     *
     * If a profile has no saved catalogue year,
     * the requested year may be used provisionally.
     *
     * Returns null when the target course is not found.
     * Results are advisory only.
     */
    public Evaluation evaluate(
            int profileId,
            String targetCourseCode,
            String campus,
            String academicYear
    ) throws SQLException, IOException {

        validateProfileId(profileId);

        validateProfileContext(
                profileId,
                campus,
                academicYear
        );

        PrerequisiteRepository.PrerequisiteInfo info =
                prerequisiteRepository.findPrerequisites(
                        targetCourseCode,
                        campus,
                        academicYear
                );

        if (info == null) {
            return null;
        }

        List<CompletedCourseRepository.CompletedCourse>
                completedCourses =
                completedRepository.findByProfile(profileId);

        Set<Integer> recordedCourseIds = new HashSet<>();

        for (CompletedCourseRepository.CompletedCourse course
                : completedCourses) {

            recordedCourseIds.add(course.courseId());
        }

        List<GroupResult> results = info.groups().stream()
                .map(group -> evaluateGroup(
                        group,
                        recordedCourseIds
                ))
                .toList();

        boolean everyGroupHasMatch = results.stream()
                .allMatch(GroupResult::hasRecordedMatch);

        Status resultStatus = determineStatus(
                info.status(),
                everyGroupHasMatch,
                results.isEmpty()
        );

        return new Evaluation(
                profileId,
                info.courseCode(),
                info.courseTitle(),
                info.campus(),
                info.academicYear(),
                resultStatus,
                results,
                info.notes(),
                info.sourceUrl(),
                explain(resultStatus)
        );
    }

    /**
     * Requires a valid positive profile ID.
     */
    private static void validateProfileId(int profileId) {

        if (profileId <= 0) {
            throw new IllegalArgumentException(
                    "Profile ID must be greater than zero."
            );
        }
    }

    /**
     * Ensures the evaluation uses the selected student's
     * actual campus and saved academic catalogue year.
     */
    private void validateProfileContext(
            int profileId,
            String requestedCampus,
            String requestedYear
    ) throws SQLException, IOException {

        ProfileRepository.Profile selectedProfile = null;

        for (ProfileRepository.Profile profile
                : profileRepository.findAll()) {

            if (profile.id() == profileId) {
                selectedProfile = profile;
                break;
            }
        }

        if (selectedProfile == null) {

            throw new IllegalArgumentException(
                    "Student profile not found."
            );
        }

        if (requestedCampus == null
                || requestedCampus.isBlank()) {

            throw new IllegalArgumentException(
                    "Campus must be specified."
            );
        }

        if (!selectedProfile.campusName()
                .equalsIgnoreCase(requestedCampus.trim())) {

            throw new IllegalArgumentException(
                    "The requested campus does not match "
                    + "the student's saved profile campus."
            );
        }

        if (requestedYear == null
                || requestedYear.isBlank()) {

            throw new IllegalArgumentException(
                    "Academic catalogue year must be specified."
            );
        }

        String profileYear = selectedProfile.academicYear();

        if (profileYear != null
                && !profileYear.isBlank()
                && !profileYear.equals(requestedYear.trim())) {

            throw new IllegalArgumentException(
                    "The requested academic year does not match "
                    + "the student's saved profile year."
            );
        }
    }

    /**
     * Evaluates OR alternatives within one AND group.
     */
    private static GroupResult evaluateGroup(
            PrerequisiteRepository.PrerequisiteGroup group,
            Set<Integer> recordedCourseIds
    ) {

        List<String> acceptedCodes = group.options().stream()
                .map(
                        PrerequisiteRepository.PrerequisiteOption
                                ::courseCode
                )
                .toList();

        List<String> recordedCodes = group.options().stream()
                .filter(option ->
                        recordedCourseIds.contains(
                                option.courseId()
                        )
                )
                .map(
                        PrerequisiteRepository.PrerequisiteOption
                                ::courseCode
                )
                .toList();

        return new GroupResult(
                group.groupNumber(),
                acceptedCodes,
                recordedCodes,
                !recordedCodes.isEmpty()
        );
    }

    /**
     * Applies conservative precedence to incomplete information.
     */
    private static Status determineStatus(
            String prerequisiteCoverage,
            boolean everyGroupHasMatch,
            boolean noGroups
    ) {

        if ("UNKNOWN".equals(prerequisiteCoverage)) {
            return Status.UNKNOWN;
        }

        if ("PARTIAL".equals(prerequisiteCoverage)) {
            return Status.PARTIAL_DATA;
        }

        if ("NONE".equals(prerequisiteCoverage)) {
            return Status.NO_LISTED_PREREQUISITES;
        }

        if (!"STRUCTURED".equals(prerequisiteCoverage)
                || noGroups) {

            return Status.UNKNOWN;
        }

        return everyGroupHasMatch
                ? Status.RECORDED_REQUIREMENTS_MET
                : Status.MISSING_REQUIREMENTS;
    }

    /**
     * Explains what an evaluation status does and does not mean.
     */
    private static String explain(Status status) {

        return switch (status) {

            case RECORDED_REQUIREMENTS_MET ->
                    "All modeled prerequisite groups have "
                    + "matching self-reported course records. "
                    + "This does not verify grades, "
                    + "co-requisites, or registration eligibility.";

            case MISSING_REQUIREMENTS ->
                    "At least one modeled prerequisite group "
                    + "has no matching completed-course record. "
                    + "The student may have other unrecorded "
                    + "academic history.";

            case PARTIAL_DATA ->
                    "The academic prerequisite data is incomplete. "
                    + "Review the results for individual groups "
                    + "and consult the academic notes. "
                    + "No definitive eligibility conclusion "
                    + "can be made.";

            case UNKNOWN ->
                    "Prerequisite information is unknown or "
                    + "not fully represented. Consult the "
                    + "official academic calendar.";

            case NO_LISTED_PREREQUISITES ->
                    "No course prerequisites are listed in "
                    + "the stored source. Co-requisites, "
                    + "grades, permissions, or other "
                    + "restrictions may still apply.";
        };
    }
}
