
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.List;

/**
 * Displays prerequisite evaluation results in the console.
 *
 * All evaluations are advisory. A recorded completion does
 * not establish that university grade requirements are met.
 */
public final class PrerequisiteEvaluationViewer {

    private PrerequisiteEvaluationViewer() {
        // Utility class.
    }

    /**
     * Displays an evaluation using standard output.
     */
    public static void display(
            PrerequisiteEvaluator.Evaluation evaluation
    ) {
        display(evaluation, System.out);
    }

    /**
     * Displays an evaluation using the supplied output stream.
     * The separate stream also supports automated tests.
     */
    public static void display(
            PrerequisiteEvaluator.Evaluation evaluation,
            PrintStream output
    ) {

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println("======= PREREQUISITE EVALUATION =======");

        if (evaluation == null) {

            output.println(
                    "No matching course was found in the "
                    + "selected campus and catalogue year."
            );

            output.println(
                    "This does not mean the course is unavailable "
                    + "at UNB."
            );

            return;
        }

        output.println(
                "Profile ID: " + evaluation.profileId()
        );

        output.println(
                "Course: " + evaluation.courseCode()
                + " - " + evaluation.courseTitle()
        );

        output.println(
                "Campus: " + evaluation.campus()
        );

        output.println(
                "Academic year: " + evaluation.academicYear()
        );

        output.println();
        output.println(
                "Evaluation status: " + evaluation.status()
        );

        output.println();

        List<PrerequisiteEvaluator.GroupResult> groups =
                evaluation.groups();

        if (groups.isEmpty()) {

            output.println(
                    "No structured prerequisite groups "
                    + "are available for this course."
            );

        } else {

            output.println("Modeled prerequisite groups:");

            for (PrerequisiteEvaluator.GroupResult group
                    : groups) {

                output.println();
                output.println(
                        "Group " + group.groupNumber()
                );

                output.println(
                        "Accepted options: "
                        + String.join(
                                " OR ",
                                group.acceptedCourseCodes()
                        )
                );

                if (group.hasRecordedMatch()) {

                    output.println(
                            "Recorded match: "
                            + String.join(
                                    ", ",
                                    group.recordedCourseCodes()
                            )
                    );

                } else {

                    output.println(
                            "Recorded match: NONE"
                    );

                    output.println(
                            "No matching completion is saved "
                            + "for this modeled group."
                    );
                }
            }

            output.println();
            output.println(
                    "All displayed groups are connected "
                    + "by AND."
            );
        }

        if (evaluation.notes() != null
                && !evaluation.notes().isBlank()) {

            output.println();
            output.println("Academic notes:");
            output.println(evaluation.notes());
        }

        output.println();
        output.println("Interpretation:");
        output.println(evaluation.explanation());

        if (evaluation.status()
                == PrerequisiteEvaluator.Status.PARTIAL_DATA) {

            output.println();
            output.println(
                    "WARNING: Some prerequisite alternatives "
                    + "or restrictions are not modeled."
            );
        }

        output.println();
        output.println("Official academic source:");
        output.println(evaluation.sourceUrl());

        output.println();
        output.println(
                "IMPORTANT: This is an advisory comparison "
                + "using self-reported course records."
        );

        output.println(
                "It is NOT an official registration "
                + "eligibility decision."
        );

        output.println(
                "Confirm grades, co-requisites, permissions, "
                + "and course availability with UNB."
        );

        output.println("=======================================");
    }
}
