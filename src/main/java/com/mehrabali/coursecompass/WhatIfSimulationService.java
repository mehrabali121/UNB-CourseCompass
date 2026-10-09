
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Performs read-only, in-memory what-if course simulations.
 *
 * Hypothetical courses are never written to the database.
 * Results are advisory, not official registration eligibility.
 */
public final class WhatIfSimulationService {

    /**
     * Comparison for one locally loaded catalogue course.
     */
    public record CourseComparison(
            String courseCode,
            String courseTitle,
            double creditHours,
            CourseExplorationService.ExplorationStatus currentStatus,
            CourseExplorationService.ExplorationStatus simulatedStatus,
            List<String> hypotheticalMatches,
            boolean statusChanged,
            String academicNotes
    ) {
        public CourseComparison {
            hypotheticalMatches = List.copyOf(hypotheticalMatches);
        }
    }

    /**
     * Read-only result of a hypothetical completion scenario.
     */
    public record SimulationResult(
            int profileId,
            String studentName,
            String campus,
            String academicYear,
            List<String> hypotheticalCourses,
            int actualCompletedCourseCount,
            int changedCourseCount,
            List<CourseComparison> courses
    ) {
        public SimulationResult {
            hypotheticalCourses = List.copyOf(hypotheticalCourses);
            courses = List.copyOf(courses);
        }
    }

    private final CourseExplorationService explorationService;

    /**
     * Uses the normal application database in read-only fashion.
     */
    public WhatIfSimulationService() {
        explorationService = new CourseExplorationService();
    }

    /**
     * Uses an isolated database for automated testing.
     */
    public WhatIfSimulationService(Path databasePath) {

        if (databasePath == null) {
            throw new IllegalArgumentException(
                    "Database path cannot be null."
            );
        }

        explorationService =
                new CourseExplorationService(databasePath);
    }

    /**
     * Simulates recording additional completed courses.
     *
     * Uses the existing exploration service as its baseline.
     * Does not call any database write operation.
     *
     * Each hypothetical course must exist in the loaded
     * catalogue for the selected profile's campus and year.
     */
    public SimulationResult simulate(
            int profileId,
            String academicYear,
            List<String> hypotheticalCourseCodes
    ) throws SQLException, IOException {

        if (hypotheticalCourseCodes == null) {
            throw new IllegalArgumentException(
                    "Hypothetical course list cannot be null."
            );
        }

        if (hypotheticalCourseCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose at least one hypothetical course."
            );
        }

        CourseExplorationService.ExplorationResult baseline =
                explorationService.explore(profileId, academicYear);

        Map<String, CourseExplorationService.ExploredCourse>
                availableCourses = new HashMap<>();

        for (CourseExplorationService.ExploredCourse entry
                : baseline.courses()) {

            availableCourses.put(
                    normalizeCode(entry.course().code()),
                    entry
            );
        }

        Set<String> simulatedCodes = new HashSet<>();
        List<String> orderedSimulatedCodes = new ArrayList<>();

        for (String suppliedCode : hypotheticalCourseCodes) {

            String normalized = normalizeCode(suppliedCode);

            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "Hypothetical course code cannot be empty."
                );
            }

            CourseExplorationService.ExploredCourse found =
                    availableCourses.get(normalized);

            if (found == null) {
                throw new IllegalArgumentException(
                        "Course " + normalized
                        + " is not in the loaded catalogue "
                        + "for this student's campus and year."
                );
            }

            if (found.status() ==
                    CourseExplorationService.ExplorationStatus
                            .ALREADY_COMPLETED) {

                throw new IllegalArgumentException(
                        normalized
                        + " is already recorded as completed. "
                        + "Choose a different hypothetical course."
                );
            }

            if (!simulatedCodes.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate hypothetical course: "
                        + normalized
                );
            }

            orderedSimulatedCodes.add(found.course().code());
        }

        List<CourseComparison> comparisons = new ArrayList<>();
        int changedCount = 0;

        for (CourseExplorationService.ExploredCourse entry
                : baseline.courses()) {

            CourseExplorationService.ExplorationStatus before =
                    entry.status();

            CourseExplorationService.ExplorationStatus after =
                    before;

            List<String> newMatches = new ArrayList<>();

            PrerequisiteEvaluator.Evaluation evaluation =
                    entry.evaluation();

            if (evaluation != null) {

                boolean allGroupsMatch = true;

                for (PrerequisiteEvaluator.GroupResult group
                        : evaluation.groups()) {

                    boolean groupMatches =
                            group.hasRecordedMatch();

                    for (String acceptedCode :
                            group.acceptedCourseCodes()) {

                        String normalized =
                                normalizeCode(acceptedCode);

                        if (simulatedCodes.contains(normalized)) {
                            groupMatches = true;

                            if (!group.hasRecordedMatch()
                                    && !newMatches.contains(
                                            acceptedCode)) {

                                newMatches.add(acceptedCode);
                            }
                        }
                    }

                    if (!groupMatches) {
                        allGroupsMatch = false;
                    }
                }

                // Preserve the evaluator's conservative
                // classification for incomplete or unknown data.
                // Only fully structured missing requirements
                // can become recorded-requirement matches.
                if (before ==
                        CourseExplorationService.ExplorationStatus
                                .MISSING_REQUIREMENTS
                        && !evaluation.groups().isEmpty()
                        && allGroupsMatch) {

                    after =
                            CourseExplorationService.ExplorationStatus
                                    .RECORDED_REQUIREMENTS_MET;
                }
            }

            boolean changed = before != after;

            if (changed) {
                changedCount++;
            }

            String notes = evaluation == null
                    ? null
                    : evaluation.notes();

            comparisons.add(
                    new CourseComparison(
                            entry.course().code(),
                            entry.course().title(),
                            entry.course().creditHours(),
                            before,
                            after,
                            newMatches,
                            changed,
                            notes
                    )
            );
        }

        return new SimulationResult(
                baseline.profileId(),
                baseline.studentName(),
                baseline.campus(),
                baseline.academicYear(),
                orderedSimulatedCodes,
                baseline.completedCourseCount(),
                changedCount,
                comparisons
        );
    }

    /**
     * Normalizes codes such as "cs 1083" to "CS1083".
     */
    private static String normalizeCode(String code) {

        if (code == null) {
            return "";
        }

        return code.replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }
}
