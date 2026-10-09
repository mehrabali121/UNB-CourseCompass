
package com.mehrabali.coursecompass;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays the academic data and source coverage actually
 * present in the local CourseCompass database.
 *
 * Does not claim complete coverage of UNB's catalogue.
 */
public final class DataCoverageViewer {

    private DataCoverageViewer() {
        // Utility class.
    }

    /**
     * Displays a report on the normal console.
     */
    public static void display(
            DataCoverageService.CoverageReport report
    ) {
        display(report, System.out);
    }

    /**
     * Displays a report to the given stream for testing.
     */
    public static void display(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        if (report == null) {
            throw new IllegalArgumentException(
                    "Coverage report cannot be null."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output stream cannot be null."
            );
        }

        output.println();
        output.println("========== DATA COVERAGE AND SOURCES ==========");
        output.println("UNB CourseCompass - Local Academic Data Report");

        printCatalogue(report, output);
        printPrerequisites(report, output);
        printOfferings(report, output);
        printPrograms(report, output);
        printSources(report, output);
        printCourseDetails(report.courses(), output);
        printDisclaimer(output);
    }

    /**
     * Summarizes loaded campus-course records and years.
     */
    private static void printCatalogue(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- LOCAL COURSE CATALOGUE -----");

        output.println(
                "Loaded campus-course records: "
                + report.courseRecordCount()
        );

        output.println(
                "Academic years represented: "
                + (report.academicYears().isEmpty()
                        ? "None"
                        : String.join(", ", report.academicYears()))
        );

        Map<String, Integer> campusCounts = new LinkedHashMap<>();

        for (CourseRepository.Course course : report.courses()) {

            String campus = course.campusName();

            campusCounts.merge(campus, 1, Integer::sum);
        }

        if (campusCounts.isEmpty()) {

            output.println(
                    "No campus-course records are available."
            );

        } else {

            output.println("Records by campus:");

            for (Map.Entry<String, Integer> entry
                    : campusCounts.entrySet()) {

                output.println(
                        "- "
                        + entry.getKey()
                        + ": "
                        + entry.getValue()
                );
            }
        }

        output.println(
                "A course associated with multiple campuses "
                + "may appear more than once."
        );

        output.println(
                "These counts do not measure how much of "
                + "the entire UNB catalogue has been imported."
        );
    }

    /**
     * Summarizes stored prerequisite data classifications.
     */
    private static void printPrerequisites(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- PREREQUISITE COVERAGE -----");

        output.println(
                "NONE (none listed in source): "
                + report.noPrerequisites()
        );

        output.println(
                "STRUCTURED (stored course relationships): "
                + report.structuredPrerequisites()
        );

        output.println(
                "PARTIAL (incomplete modeled requirements): "
                + report.partialPrerequisites()
        );

        output.println(
                "UNKNOWN (not established): "
                + report.unknownPrerequisites()
        );

        int total = report.courseRecordCount();

        output.println(
                "NONE or STRUCTURED among loaded records: "
                + report.fullyDescribedPrerequisiteCount()
                + " / "
                + total
                + " ("
                + percentage(
                        report.fullyDescribedPrerequisiteCount(),
                        total
                )
                + ")"
        );

        output.println(
                "Stored relationships may omit grade minimums, "
                + "alternatives, restrictions, or approvals."
        );

        output.println(
                "These statuses do not confirm a student's "
                + "registration eligibility."
        );
    }

    /**
     * Shows the actual stored offering classifications.
     */
    private static void printOfferings(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- OFFERING INFORMATION -----");

        output.println(
                "VERIFIED_OFFERED: " + report.verifiedOffered()
        );

        output.println(
                "VERIFIED_NOT_OFFERED: "
                + report.verifiedNotOffered()
        );

        output.println(
                "UNKNOWN: " + report.unknownOfferings()
        );

        output.println(
                "Catalogue inclusion does not mean that a "
                + "course is scheduled in a specific term."
        );

        output.println(
                "Check official UNB registration information "
                + "for current offerings and availability."
        );
    }

    /**
     * Reports the presence and status of local program data.
     */
    private static void printPrograms(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- PROGRAM REQUIREMENT COVERAGE -----");

        output.println(
                "Stored programs: " + report.programCount()
        );

        output.println(
                "SUPPORTED: " + report.supportedPrograms()
        );

        output.println(
                "PARTIAL: " + report.partialPrograms()
        );

        output.println(
                "UNSUPPORTED: " + report.unsupportedPrograms()
        );

        output.println(
                "Stored program requirement rows: "
                + report.programRequirementCount()
        );

        if (report.programCount() == 0) {

            output.println(
                    "No programs have been loaded into "
                    + "the local database."
            );

        } else if (report.programRequirementCount() == 0) {

            output.println(
                    "No program requirements are currently stored."
            );
        }

        output.println(
                "Program audit coverage reflects stored data, "
                + "not official graduation certification."
        );
    }

    /**
     * Prints source metadata with academic year and
     * recorded verification dates.
     */
    private static void printSources(
            DataCoverageService.CoverageReport report,
            PrintStream output
    ) {

        output.println();
        output.println("----- ACADEMIC SOURCES -----");

        output.println(
                "Stored sources: " + report.sourceCount()
        );

        if (report.sources().isEmpty()) {

            output.println(
                    "No academic source records are available."
            );

            return;
        }

        for (DataCoverageService.AcademicSource source
                : report.sources()) {

            output.println();
            output.println(
                    "[" + source.id() + "] " + source.title()
            );

            output.println(
                    "Academic year: " + source.academicYear()
            );

            output.println(
                    "Recorded verification date: "
                    + source.verifiedOn()
            );

            output.println(
                    "Source URL: " + source.url()
            );

            if (source.notes() != null
                    && !source.notes().isBlank()) {

                output.println(
                        "Notes: " + source.notes().trim()
                );
            }
        }

        output.println();
        output.println(
                "A recorded verification date is not proof "
                + "that information is still current."
        );
    }

    /**
     * Lists individual records to make unknown and partial
     * classifications visible to users.
     */
    private static void printCourseDetails(
            List<CourseRepository.Course> courses,
            PrintStream output
    ) {

        output.println();
        output.println("----- LOADED COURSE STATUS DETAILS -----");

        if (courses.isEmpty()) {

            output.println("No courses are currently loaded.");
            return;
        }

        for (CourseRepository.Course course : courses) {

            output.println(
                    course.code()
                    + " | "
                    + course.campusName()
                    + " | "
                    + course.academicYear()
            );

            output.println(
                    "  Prerequisites: "
                    + course.prerequisiteStatus()
                    + " | Offering: "
                    + course.offeringStatus()
            );
        }
    }

    /**
     * Academic data transparency and project disclaimer.
     */
    private static void printDisclaimer(PrintStream output) {

        output.println();
        output.println("----- IMPORTANT LIMITATIONS -----");

        output.println(
                "CourseCompass currently contains only "
                + "a limited selection of academic records."
        );

        output.println(
                "Missing records do not mean that a UNB "
                + "course or program does not exist."
        );

        output.println(
                "Source verification dates indicate when "
                + "records were checked, not guaranteed accuracy."
        );

        output.println(
                "Course reviews, student ratings, historical "
                + "class averages, and current seat availability "
                + "are not provided by this report."
        );

        output.println(
                "Consult official UNB academic calendars, "
                + "registration systems and academic advising "
                + "before making academic decisions."
        );

        output.println(
                "UNB CourseCompass is an independent student "
                + "project, not affiliated with or endorsed by UNB."
        );

        output.println("===============================================");
    }

    private static String percentage(
            int numerator,
            int denominator
    ) {

        if (denominator == 0) {
            return "N/A";
        }

        return String.format(
                Locale.ROOT,
                "%.1f%%",
                numerator * 100.0 / denominator
        );
    }
}
