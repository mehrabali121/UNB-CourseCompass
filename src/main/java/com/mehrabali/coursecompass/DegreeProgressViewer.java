
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/**
 * Displays advisory degree progress reports.
 *
 * All completed coursework is self-reported.
 * This viewer never determines graduation eligibility.
 */
public final class DegreeProgressViewer {

    private DegreeProgressViewer() {
        // Utility class.
    }

    /**
     * Displays a report on the normal console.
     */
    public static void display(
            DegreeProgressService.ProgressReport report
    ) {
        display(report, System.out);
    }

    /**
     * Displays a report using a supplied output stream.
     * This overload supports isolated viewer tests.
     */
    public static void display(
            DegreeProgressService.ProgressReport report,
            PrintStream output
    ) {

        if (report == null) {
            throw new IllegalArgumentException(
                    "Degree progress report cannot be null."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println("========== DEGREE PROGRESS ==========");
        output.println("Student: " + report.studentName());
        output.println("Profile ID: " + report.profileId());
        output.println("Campus: " + report.campus());

        output.println(
                "Catalogue year: "
                + optional(report.academicYear())
        );

        output.println(
                "Selected program: "
                + optional(report.programName())
        );

        output.println(
                "Program audit coverage: "
                + report.auditStatus()
        );

        output.println();
        output.println("----- RECORDED COURSEWORK -----");

        output.println(
                "Self-reported completed courses: "
                + report.completedCourseCount()
        );

        output.println(
                "Total self-reported course credits: "
                + credits(report.recordedCreditHours())
        );

        printCompletedCourses(
                report.completedCourses(),
                output
        );

        output.println();
        output.println("----- PROGRAM REQUIREMENTS -----");

        if (report.programId() == null) {

            output.println(
                    "No academic program is selected "
                    + "for this student profile."
            );

            output.println(
                    "You can review recorded coursework, "
                    + "but a program-specific audit "
                    + "is not available."
            );

        } else if (report.requirements().isEmpty()) {

            output.println(
                    "No program requirements are currently "
                    + "stored for this selected program."
            );

            output.println(
                    "This is missing data, not confirmation "
                    + "that the program has no requirements."
            );

        } else {

            for (DegreeProgressService.RequirementProgress requirement
                    : report.requirements()) {

                printRequirement(requirement, output);
            }
        }

        printCoverageWarning(report, output);
        printDisclaimer(output);
    }

    /**
     * Shows completed courses without claiming verified grades.
     */
    private static void printCompletedCourses(
            List<CompletedCourseRepository.CompletedCourse> courses,
            PrintStream output
    ) {

        if (courses.isEmpty()) {
            output.println(
                    "No completed courses have been recorded."
            );
            return;
        }

        for (CompletedCourseRepository.CompletedCourse course
                : courses) {

            output.println(
                    course.courseCode()
                    + " - "
                    + course.courseTitle()
                    + " | Credits: "
                    + credits(course.creditHours())
            );
        }
    }

    /**
     * Displays progress against one stored requirement.
     */
    private static void printRequirement(
            DegreeProgressService.RequirementProgress requirement,
            PrintStream output
    ) {

        output.println();
        output.println(
                requirement.requirementName()
                + " [ID: "
                + requirement.requirementId()
                + "]"
        );

        output.println(
                "Requirement type: "
                + requirement.requirementType()
        );

        if ("REQUIRED_COURSE".equals(
                requirement.requirementType())) {

            output.println(
                    "Required course: "
                    + optional(requirement.requiredCourseCode())
            );

        } else if ("MINIMUM_CREDITS".equals(
                requirement.requirementType())) {

            output.println(
                    "Minimum mapped credits: "
                    + (requirement.minimumCredits() == null
                            ? "Unknown"
                            : credits(requirement.minimumCredits()))
            );
        }

        output.println(
                "Matching self-reported credits: "
                + credits(requirement.matchedCredits())
        );

        output.println(
                "Matching courses: "
                + (requirement.matchedCourseCodes().isEmpty()
                        ? "None recorded or mapped"
                        : String.join(
                                ", ",
                                requirement.matchedCourseCodes()
                        ))
        );

        output.println(
                "Advisory status: "
                + describeStatus(requirement.status())
        );

        if (requirement.notes() != null
                && !requirement.notes().isBlank()) {

            output.println(
                    "Academic notes: "
                    + requirement.notes().trim()
            );
        }
    }

    /**
     * Converts internal statuses into readable explanations.
     */
    private static String describeStatus(
            DegreeProgressService.RequirementStatus status
    ) {

        return switch (status) {

            case RECORDED_MATCH ->
                    "RECORDED_MATCH "
                    + "(required course appears in records)";

            case NOT_RECORDED ->
                    "NOT_RECORDED "
                    + "(required course not recorded)";

            case REPORTED_CREDITS_AT_LEAST_MINIMUM ->
                    "REPORTED_CREDITS_AT_LEAST_MINIMUM "
                    + "(mapped recorded credits reach "
                    + "the stored minimum)";

            case REPORTED_CREDITS_BELOW_MINIMUM ->
                    "REPORTED_CREDITS_BELOW_MINIMUM "
                    + "(mapped recorded credits are below "
                    + "the stored minimum)";

            case NO_ELIGIBLE_COURSE_MAPPING ->
                    "NO_ELIGIBLE_COURSE_MAPPING "
                    + "(eligible courses have not "
                    + "been mapped)";
        };
    }

    /**
     * Makes limitations clear even when stored requirements
     * have apparent matches.
     */
    private static void printCoverageWarning(
            DegreeProgressService.ProgressReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- COVERAGE ASSESSMENT -----");

        String coverage = report.auditStatus();

        if ("NO_PROGRAM_SELECTED".equals(coverage)) {

            output.println(
                    "Degree audit unavailable: "
                    + "no program selected."
            );

        } else if ("UNSUPPORTED".equals(coverage)) {

            output.println(
                    "Full degree audit is unsupported "
                    + "for this program."
            );

        } else if ("PARTIAL".equals(coverage)) {

            output.println(
                    "Only part of this program's "
                    + "requirements is represented."
            );

        } else if ("SUPPORTED".equals(coverage)) {

            output.println(
                    "The stored program is marked SUPPORTED, "
                    + "but these results are still advisory."
            );

        } else {

            output.println(
                    "Degree audit coverage is unknown."
            );
        }

        output.println(
                "No overall degree completion percentage "
                + "has been calculated."
        );
    }

    /**
     * Displays the academic safety disclaimer.
     */
    private static void printDisclaimer(PrintStream output) {

        output.println();
        output.println("----- IMPORTANT INFORMATION -----");

        output.println(
                "Completed-course records are self-reported."
        );

        output.println(
                "Grades, transfer credits, substitutions, "
                + "residency rules and approvals "
                + "have not been verified."
        );

        output.println(
                "A recorded requirement match does not "
                + "confirm official program completion."
        );

        output.println(
                "Credit-category totals only use explicitly "
                + "mapped eligible courses."
        );

        output.println(
                "This is not an official degree audit "
                + "or a determination of graduation eligibility."
        );

        output.println(
                "Verify requirements using official "
                + "UNB academic information and advising."
        );

        output.println(
                "UNB CourseCompass is an independent, "
                + "unofficial student project."
        );

        output.println("=====================================");
    }

    private static String optional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }

    private static String credits(double value) {

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }
}
