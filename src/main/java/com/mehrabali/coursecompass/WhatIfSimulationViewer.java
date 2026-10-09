
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.List;

/**
 * Displays read-only What-If Planner results.
 *
 * The viewer never changes student records.
 * Every simulation is hypothetical and advisory only.
 */
public final class WhatIfSimulationViewer {

    private WhatIfSimulationViewer() {
        // Utility class.
    }

    /**
     * Displays a simulation on the normal console.
     */
    public static void display(
            WhatIfSimulationService.SimulationResult result
    ) {
        display(result, System.out);
    }

    /**
     * Displays a simulation using a supplied output stream.
     * This overload supports isolated automated viewer tests.
     */
    public static void display(
            WhatIfSimulationService.SimulationResult result,
            PrintStream output
    ) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Simulation result cannot be null."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println("========== WHAT-IF PLANNER ==========");
        output.println("Student: " + result.studentName());
        output.println("Profile ID: " + result.profileId());
        output.println("Campus: " + result.campus());

        output.println(
                "Catalogue year: " + result.academicYear()
        );

        output.println(
                "Actual recorded completions: "
                + result.actualCompletedCourseCount()
        );

        output.println();
        output.println("HYPOTHETICAL COMPLETIONS");

        for (String code : result.hypotheticalCourses()) {
            output.println("  + " + code);
        }

        output.println();
        output.println(
                "Courses with changed advisory status: "
                + result.changedCourseCount()
        );

        output.println(
                "Courses compared: " + result.courses().size()
        );

        output.println();

        printChangedCourses(result.courses(), output);
        printUnchangedCourses(result.courses(), output);

        printDisclaimer(output);
    }

    /**
     * Shows changes prominently so students can understand
     * which modeled prerequisites are affected.
     */
    private static void printChangedCourses(
            List<WhatIfSimulationService.CourseComparison> courses,
            PrintStream output
    ) {

        output.println("----- CHANGED COURSE RESULTS -----");

        int changedCount = 0;

        for (WhatIfSimulationService.CourseComparison course
                : courses) {

            if (!course.statusChanged()) {
                continue;
            }

            changedCount++;

            output.println();
            output.println(
                    course.courseCode()
                    + " - " + course.courseTitle()
            );

            output.println(
                    "Current: "
                    + describeStatus(course.currentStatus())
            );

            output.println(
                    "Simulated: "
                    + describeStatus(course.simulatedStatus())
            );

            output.println(
                    "Credit hours: " + course.creditHours()
            );

            if (!course.hypotheticalMatches().isEmpty()) {

                output.println(
                        "Hypothetical prerequisite matches: "
                        + String.join(
                                ", ",
                                course.hypotheticalMatches()
                        )
                );
            }

            printAcademicNotes(course, output);
        }

        if (changedCount == 0) {

            output.println();
            output.println(
                    "No course classifications changed "
                    + "under this simulation."
            );

            output.println(
                    "This does not mean the hypothetical "
                    + "courses have no academic value."
            );
        }

        output.println();
    }

    /**
     * Shows unchanged classifications for context.
     */
    private static void printUnchangedCourses(
            List<WhatIfSimulationService.CourseComparison> courses,
            PrintStream output
    ) {

        output.println("----- UNCHANGED COURSE RESULTS -----");

        int unchangedCount = 0;

        for (WhatIfSimulationService.CourseComparison course
                : courses) {

            if (course.statusChanged()) {
                continue;
            }

            unchangedCount++;

            output.println(
                    course.courseCode()
                    + " | "
                    + describeStatus(course.simulatedStatus())
            );
        }

        if (unchangedCount == 0) {

            output.println(
                    "Every compared course changed status."
            );
        }

        output.println();
    }

    /**
     * Displays additional academic notes when available.
     */
    private static void printAcademicNotes(
            WhatIfSimulationService.CourseComparison course,
            PrintStream output
    ) {

        if (course.academicNotes() != null
                && !course.academicNotes().isBlank()) {

            output.println(
                    "Academic notes: "
                    + course.academicNotes().trim()
            );
        }
    }

    /**
     * Converts internal classification names into
     * readable, conservative explanations.
     */
    private static String describeStatus(
            CourseExplorationService.ExplorationStatus status
    ) {

        if (status == null) {
            return "UNKNOWN";
        }

        return switch (status) {

            case ALREADY_COMPLETED ->
                    "ALREADY_COMPLETED "
                    + "(actually recorded as completed)";

            case RECORDED_REQUIREMENTS_MET ->
                    "RECORDED_REQUIREMENTS_MET "
                    + "(modeled course prerequisites matched)";

            case MISSING_REQUIREMENTS ->
                    "MISSING_REQUIREMENTS "
                    + "(some modeled prerequisites not matched)";

            case PARTIAL_DATA ->
                    "PARTIAL_DATA "
                    + "(prerequisite coverage incomplete)";

            case UNKNOWN ->
                    "UNKNOWN "
                    + "(insufficient prerequisite information)";

            case NO_LISTED_PREREQUISITES ->
                    "NO_LISTED_PREREQUISITES "
                    + "(none listed in stored source)";
        };
    }

    /**
     * Displays safety and interpretation notes.
     */
    private static void printDisclaimer(PrintStream output) {

        output.println("----- IMPORTANT INFORMATION -----");

        output.println(
                "This is a hypothetical academic planning exercise."
        );

        output.println(
                "No completed-course records were added, "
                + "removed, or updated by this simulation."
        );

        output.println(
                "A hypothetical course is not an actual "
                + "completed course."
        );

        output.println(
                "Matched modeled prerequisites do not confirm "
                + "minimum grades, co-requisites, permissions, "
                + "registration eligibility, or course offerings."
        );

        output.println(
                "PARTIAL_DATA and UNKNOWN classifications "
                + "remain uncertain."
        );

        output.println(
                "Verify academic decisions against official "
                + "UNB information."
        );

        output.println(
                "UNB CourseCompass is an independent, "
                + "unofficial student project."
        );

        output.println("=====================================");
    }
}
