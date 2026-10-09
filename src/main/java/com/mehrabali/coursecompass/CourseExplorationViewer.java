
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Displays advisory course exploration results in the console.
 *
 * This viewer does not determine official registration eligibility
 * or verify that a course is offered in a particular term.
 */
public final class CourseExplorationViewer {

    private CourseExplorationViewer() {
        // Utility class.
    }

    /**
     * Displays exploration results using standard output.
     */
    public static void display(
            CourseExplorationService.ExplorationResult result
    ) {
        display(result, System.out);
    }

    /**
     * Displays exploration results using a supplied output stream.
     * This also allows console output to be tested.
     */
    public static void display(
            CourseExplorationService.ExplorationResult result,
            PrintStream output
    ) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "Exploration result cannot be null."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println(
                "========== EXPLORE POSSIBLE COURSES =========="
        );

        output.println("Student: " + result.studentName());
        output.println("Profile ID: " + result.profileId());
        output.println("Campus: " + result.campus());
        output.println("Catalogue year: " + result.academicYear());

        output.println();
        output.println(
                "Recorded completed courses: "
                + result.completedCourseCount()
        );

        output.println(
                "Courses in local catalogue: "
                + result.courses().size()
        );

        if (result.courses().isEmpty()) {

            output.println();
            output.println(
                    "No courses are currently loaded for this "
                    + "campus and catalogue year."
            );

            output.println(
                    "This does not mean UNB offers no courses "
                    + "there. Our local catalogue coverage is limited."
            );

        } else {

            printSummary(result.courses(), output);
            printCourses(result.courses(), output);
        }

        output.println();
        output.println("IMPORTANT:");
        output.println(
                "These results use self-reported completed courses "
                + "and limited local catalogue data."
        );

        output.println(
                "A matching prerequisite record does not verify "
                + "minimum grades or official eligibility."
        );

        output.println(
                "Course availability, co-requisites, permissions, "
                + "and other restrictions are not fully checked."
        );

        output.println(
                "Confirm all academic decisions using "
                + "official UNB information."
        );

        output.println(
                "=============================================="
        );
    }

    /**
     * Prints how many loaded courses belong to each category.
     */
    private static void printSummary(
            List<CourseExplorationService.ExploredCourse> courses,
            PrintStream output
    ) {

        Map<CourseExplorationService.ExplorationStatus, Integer>
                counts = new EnumMap<>(
                        CourseExplorationService.ExplorationStatus.class
                );

        for (CourseExplorationService.ExplorationStatus status
                : CourseExplorationService.ExplorationStatus.values()) {

            counts.put(status, 0);
        }

        for (CourseExplorationService.ExploredCourse course
                : courses) {

            counts.merge(course.status(), 1, Integer::sum);
        }

        output.println();
        output.println("----- EXPLORATION SUMMARY -----");

        for (CourseExplorationService.ExplorationStatus status
                : CourseExplorationService.ExplorationStatus.values()) {

            output.println(
                    status + ": " + counts.get(status)
            );
        }
    }

    /**
     * Prints every loaded course with its exploration status.
     */
    private static void printCourses(
            List<CourseExplorationService.ExploredCourse> courses,
            PrintStream output
    ) {

        output.println();
        output.println("----- COURSE EXPLORATION RESULTS -----");

        for (CourseExplorationService.ExploredCourse explored
                : courses) {

            CourseRepository.Course course = explored.course();

            output.println();
            output.println(
                    course.code() + " - " + course.title()
            );

            output.println(
                    "Credit hours: " + course.creditHours()
            );

            output.println(
                    "Exploration status: " + explored.status()
            );

            output.println(
                    describeStatus(explored.status())
            );

            if (explored.evaluation() != null) {

                PrerequisiteEvaluator.Evaluation evaluation =
                        explored.evaluation();

                if (!evaluation.groups().isEmpty()) {

                    for (PrerequisiteEvaluator.GroupResult group
                            : evaluation.groups()) {

                        output.println(
                                "Prerequisite group "
                                + group.groupNumber()
                                + ": "
                                + String.join(
                                        " OR ",
                                        group.acceptedCourseCodes()
                                )
                        );

                        output.println(
                                group.hasRecordedMatch()
                                        ? "Recorded match: "
                                        + String.join(
                                                ", ",
                                                group.recordedCourseCodes()
                                        )
                                        : "Recorded match: NONE"
                        );
                    }

                    output.println(
                            "Displayed prerequisite groups "
                            + "are connected by AND."
                    );
                }

                if (evaluation.notes() != null
                        && !evaluation.notes().isBlank()) {

                    output.println(
                            "Academic notes: "
                            + evaluation.notes()
                    );
                }
            }

            output.println(
                    "Offering status: " + course.offeringStatus()
            );
        }
    }

    /**
     * Returns a cautious plain-English explanation.
     */
    private static String describeStatus(
            CourseExplorationService.ExplorationStatus status
    ) {

        return switch (status) {

            case ALREADY_COMPLETED ->
                    "This course is already recorded as completed "
                    + "for this student.";

            case RECORDED_REQUIREMENTS_MET ->
                    "All modeled prerequisite groups have recorded "
                    + "matches. Grades and official eligibility "
                    + "have not been verified.";

            case MISSING_REQUIREMENTS ->
                    "At least one modeled prerequisite group "
                    + "has no matching completed-course record.";

            case PARTIAL_DATA ->
                    "Prerequisite data is incomplete. "
                    + "No definitive eligibility conclusion "
                    + "can be made.";

            case UNKNOWN ->
                    "Prerequisite information is unknown "
                    + "or cannot be fully evaluated.";

            case NO_LISTED_PREREQUISITES ->
                    "No prerequisites are listed in the local "
                    + "source, but other restrictions may apply.";
        };
    }
}
