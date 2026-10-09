
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.List;

/**
 * Displays academic prerequisite information in the console.
 *
 * The viewer does not calculate registration eligibility.
 * It clearly identifies incomplete or unknown information.
 */
public final class PrerequisiteViewer {

    private PrerequisiteViewer() {
        // Utility class.
    }

    /**
     * Displays a prerequisite record on the console.
     */
    public static void display(
            PrerequisiteRepository.PrerequisiteInfo info
    ) {
        display(info, System.out);
    }

    /**
     * Displays a prerequisite record using a supplied output
     * stream. This also supports automated testing.
     */
    public static void display(
            PrerequisiteRepository.PrerequisiteInfo info,
            PrintStream output
    ) {

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println("======= COURSE PREREQUISITES =======");

        if (info == null) {

            output.println(
                    "No matching course was found in the "
                    + "local catalogue."
            );

            output.println(
                    "This does not mean the course is absent "
                    + "from UNB."
            );

            return;
        }

        output.println(
                "Course: " + info.courseCode()
                + " - " + info.courseTitle()
        );

        output.println("Campus: " + info.campus());

        output.println(
                "Academic year: " + info.academicYear()
        );

        output.println(
                "Prerequisite coverage: " + info.status()
        );

        output.println();

        List<PrerequisiteRepository.PrerequisiteGroup> groups =
                info.groups();

        switch (info.status()) {

            case "NONE" -> output.println(
                    "No course prerequisites are listed "
                    + "in the stored academic source."
            );

            case "UNKNOWN" -> output.println(
                    "Prerequisite information has not "
                    + "been verified."
            );

            case "STRUCTURED", "PARTIAL" -> {

                if (groups.isEmpty()) {

                    output.println(
                            "No structured prerequisite groups "
                            + "are currently stored."
                    );

                } else {

                    output.println(
                            "Stored prerequisite requirements:"
                    );

                    for (int index = 0;
                            index < groups.size();
                            index++) {

                        if (index > 0) {
                            output.println("AND");
                        }

                        printGroup(groups.get(index), output);
                    }
                }
            }

            default -> output.println(
                    "Unrecognized prerequisite coverage status."
            );
        }

        if (info.notes() != null
                && !info.notes().isBlank()) {

            output.println();
            output.println("Important academic notes:");
            output.println(info.notes());
        }

        if ("PARTIAL".equals(info.status())) {

            output.println();
            output.println(
                    "WARNING: The structured requirements "
                    + "shown above are incomplete."
            );

            output.println(
                    "Additional alternatives or restrictions "
                    + "may apply."
            );
        }

        output.println();
        output.println("Official academic source:");
        output.println(info.sourceUrl());

        output.println();
        output.println(
                "CourseCompass cannot confirm registration "
                + "eligibility from this information."
        );

        output.println(
                "Verify prerequisites, minimum grades, "
                + "co-requisites and course availability "
                + "with official UNB sources."
        );
    }

    /**
     * Displays one group containing OR alternatives.
     */
    private static void printGroup(
            PrerequisiteRepository.PrerequisiteGroup group,
            PrintStream output
    ) {

        List<PrerequisiteRepository.PrerequisiteOption> options =
                group.options();

        output.print("Group " + group.groupNumber() + ": ");

        if (options.isEmpty()) {

            output.println("No stored options");
            return;
        }

        for (int index = 0;
                index < options.size();
                index++) {

            if (index > 0) {
                output.print(" OR ");
            }

            output.print(options.get(index).courseCode());
        }

        output.println();
    }
}
