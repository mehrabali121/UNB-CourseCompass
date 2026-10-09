
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explores possible future courses for a saved student profile.
 *
 * Uses the existing course catalogue, completed-course records,
 * and prerequisite evaluation engine.
 *
 * This service is advisory only. It cannot establish official
 * registration eligibility or confirm course availability.
 */
public final class CourseExplorationService {

    public enum ExplorationStatus {
        ALREADY_COMPLETED,
        RECORDED_REQUIREMENTS_MET,
        MISSING_REQUIREMENTS,
        PARTIAL_DATA,
        UNKNOWN,
        NO_LISTED_PREREQUISITES
    }

    /**
     * One course and its exploratory classification.
     */
    public record ExploredCourse(
            CourseRepository.Course course,
            ExplorationStatus status,
            PrerequisiteEvaluator.Evaluation evaluation
    ) {

        public ExploredCourse {

            if (course == null) {
                throw new IllegalArgumentException(
                        "Course cannot be null."
                );
            }

            if (status == null) {
                throw new IllegalArgumentException(
                        "Exploration status cannot be null."
                );
            }
        }
    }

    /**
     * All explored courses for one student and catalogue year.
     */
    public record ExplorationResult(
            int profileId,
            String studentName,
            String campus,
            String academicYear,
            int completedCourseCount,
            List<ExploredCourse> courses
    ) {

        public ExplorationResult {
            courses = List.copyOf(courses);
        }
    }

    private final ProfileRepository profileRepository;
    private final CourseRepository courseRepository;
    private final CompletedCourseRepository completedRepository;
    private final PrerequisiteEvaluator evaluator;

    /**
     * Uses the application's regular database.
     */
    public CourseExplorationService() {

        profileRepository = new ProfileRepository();
        courseRepository = new CourseRepository();

        completedRepository =
                new CompletedCourseRepository();

        evaluator = new PrerequisiteEvaluator();
    }

    /**
     * Uses a separate database for automated tests.
     */
    public CourseExplorationService(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        profileRepository =
                new ProfileRepository(databasePath);

        courseRepository =
                new CourseRepository(databasePath);

        completedRepository =
                new CompletedCourseRepository(databasePath);

        evaluator =
                new PrerequisiteEvaluator(databasePath);
    }

    /**
     * Explores all loaded catalogue courses for a student.
     *
     * If the profile has a saved catalogue year, that year
     * must match the requested year.
     *
     * Courses not present in the local catalogue cannot
     * appear in these results.
     */
    public ExplorationResult explore(
            int profileId,
            String academicYear
    ) throws SQLException, IOException {

        if (profileId <= 0) {
            throw new IllegalArgumentException(
                    "Profile ID must be greater than zero."
            );
        }

        if (academicYear == null
                || academicYear.isBlank()) {

            throw new IllegalArgumentException(
                    "Academic year must be specified."
            );
        }

        String requestedYear = academicYear.trim();

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

        String savedYear = selectedProfile.academicYear();

        if (savedYear != null
                && !savedYear.isBlank()
                && !savedYear.equals(requestedYear)) {

            throw new IllegalArgumentException(
                    "Requested academic year does not match "
                    + "the student's saved profile year."
            );
        }

        String campus = selectedProfile.campusName();

        List<CompletedCourseRepository.CompletedCourse>
                completedCourses =
                completedRepository.findByProfile(profileId);

        Set<String> completedCodes = new HashSet<>();

        for (CompletedCourseRepository.CompletedCourse completed
                : completedCourses) {

            completedCodes.add(
                    completed.courseCode().trim().toUpperCase()
            );
        }

        List<CourseRepository.Course> catalogueCourses =
                courseRepository.searchCourses(
                        "",
                        campus,
                        requestedYear
                );

        List<ExploredCourse> exploredCourses =
                new java.util.ArrayList<>();

        for (CourseRepository.Course course
                : catalogueCourses) {

            PrerequisiteEvaluator.Evaluation evaluation =
                    evaluator.evaluate(
                            profileId,
                            course.code(),
                            campus,
                            requestedYear
                    );

            ExplorationStatus status;

            if (completedCodes.contains(
                    course.code().trim().toUpperCase()
            )) {

                status = ExplorationStatus.ALREADY_COMPLETED;

            } else if (evaluation == null) {

                status = ExplorationStatus.UNKNOWN;

            } else {

                status = switch (evaluation.status()) {

                    case RECORDED_REQUIREMENTS_MET ->
                            ExplorationStatus.RECORDED_REQUIREMENTS_MET;

                    case MISSING_REQUIREMENTS ->
                            ExplorationStatus.MISSING_REQUIREMENTS;

                    case PARTIAL_DATA ->
                            ExplorationStatus.PARTIAL_DATA;

                    case UNKNOWN ->
                            ExplorationStatus.UNKNOWN;

                    case NO_LISTED_PREREQUISITES ->
                            ExplorationStatus.NO_LISTED_PREREQUISITES;
                };
            }

            exploredCourses.add(
                    new ExploredCourse(
                            course,
                            status,
                            evaluation
                    )
            );
        }

        return new ExplorationResult(
                profileId,
                selectedProfile.name(),
                campus,
                requestedYear,
                completedCourses.size(),
                exploredCourses
        );
    }
}
