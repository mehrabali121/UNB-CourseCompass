
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests advisory degree progress console output.
 *
 * Uses fictional in-memory reports.
 * No real academic records or SQLite databases are changed.
 */
public class DegreeProgressViewerTest {

    private static final String YEAR = "2026-2027";

    private CompletedCourseRepository.CompletedCourse
            completedCourse() {

        return new CompletedCourseRepository.CompletedCourse(
                1,
                2,
                10,
                "CS1073",
                "Introduction to Computer Programming I",
                4.0,
                YEAR,
                "2026-04-20"
        );
    }

    private DegreeProgressService.RequirementProgress
            matchedRequirement() {

        return new DegreeProgressService.RequirementProgress(
                1,
                "Programming I",
                "REQUIRED_COURSE",
                "CS1073",
                null,
                4.0,
                List.of("CS1073"),
                DegreeProgressService.RequirementStatus.RECORDED_MATCH,
                "Fictional test requirement."
        );
    }

    private DegreeProgressService.RequirementProgress
            missingRequirement() {

        return new DegreeProgressService.RequirementProgress(
                2,
                "Programming II",
                "REQUIRED_COURSE",
                "CS1083",
                null,
                0.0,
                List.of(),
                DegreeProgressService.RequirementStatus.NOT_RECORDED,
                null
        );
    }

    private DegreeProgressService.RequirementProgress
            minimumCreditsRequirement() {

        return new DegreeProgressService.RequirementProgress(
                3,
                "Mapped Computing Credits",
                "MINIMUM_CREDITS",
                null,
                7.0,
                4.0,
                List.of("CS1073"),
                DegreeProgressService.RequirementStatus
                        .REPORTED_CREDITS_BELOW_MINIMUM,
                "Only explicitly mapped courses count."
        );
    }

    private DegreeProgressService.RequirementProgress
            unmappedRequirement() {

        return new DegreeProgressService.RequirementProgress(
                4,
                "Unmapped Credit Category",
                "MINIMUM_CREDITS",
                null,
                3.0,
                0.0,
                List.of(),
                DegreeProgressService.RequirementStatus
                        .NO_ELIGIBLE_COURSE_MAPPING,
                null
        );
    }

    private DegreeProgressService.ProgressReport
            reportWithPartialProgram() {

        return new DegreeProgressService.ProgressReport(
                2,
                "CourseCompass Test Student",
                "Fredericton",
                YEAR,
                1,
                "Fictional Test Program",
                "PARTIAL",
                1,
                4.0,
                List.of(completedCourse()),
                List.of(
                        matchedRequirement(),
                        missingRequirement(),
                        minimumCreditsRequirement(),
                        unmappedRequirement()
                )
        );
    }

    private String render(
            DegreeProgressService.ProgressReport report
    ) {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                buffer,
                true,
                StandardCharsets.UTF_8
        )) {

            DegreeProgressViewer.display(report, output);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void displaysStudentAndRecordedCoursework() {

        String output = render(reportWithPartialProgram());

        assertTrue(output.contains("DEGREE PROGRESS"));
        assertTrue(output.contains("CourseCompass Test Student"));
        assertTrue(output.contains("Profile ID: 2"));
        assertTrue(output.contains("Campus: Fredericton"));
        assertTrue(output.contains("Catalogue year: " + YEAR));
        assertTrue(output.contains("Fictional Test Program"));
        assertTrue(output.contains("Program audit coverage: PARTIAL"));

        assertTrue(
                output.contains("Self-reported completed courses: 1")
        );

        assertTrue(
                output.contains("Total self-reported course credits: 4.0")
        );

        assertTrue(
                output.contains(
                        "CS1073 - Introduction to Computer Programming I"
                )
        );
    }

    @Test
    void displaysMatchedAndMissingRequiredCourses() {

        String output = render(reportWithPartialProgram());

        assertTrue(output.contains("PROGRAM REQUIREMENTS"));
        assertTrue(output.contains("Programming I [ID: 1]"));
        assertTrue(output.contains("Programming II [ID: 2]"));

        assertTrue(output.contains("Required course: CS1073"));
        assertTrue(output.contains("Required course: CS1083"));
        assertTrue(output.contains("Advisory status: RECORDED_MATCH"));
        assertTrue(output.contains("Advisory status: NOT_RECORDED"));
        assertTrue(output.contains("Academic notes: Fictional test requirement."));
    }

    @Test
    void displaysMappedCreditTotalsAndMissingMappings() {

        String output = render(reportWithPartialProgram());

        assertTrue(output.contains("Mapped Computing Credits"));
        assertTrue(output.contains("Minimum mapped credits: 7.0"));
        assertTrue(output.contains("Matching self-reported credits: 4.0"));

        assertTrue(
                output.contains("REPORTED_CREDITS_BELOW_MINIMUM")
        );

        assertTrue(output.contains("Unmapped Credit Category"));
        assertTrue(output.contains("NO_ELIGIBLE_COURSE_MAPPING"));
        assertTrue(output.contains("Only explicitly mapped courses count."));
    }

    @Test
    void handlesMissingProgramAndEmptyCoursework() {

        var report = new DegreeProgressService.ProgressReport(
                3,
                "Student Without Program",
                "Fredericton",
                YEAR,
                null,
                null,
                "NO_PROGRAM_SELECTED",
                0,
                0.0,
                List.of(),
                List.of()
        );

        String output = render(report);

        assertTrue(output.contains("Student Without Program"));
        assertTrue(output.contains("Selected program: Not selected"));
        assertTrue(output.contains("No completed courses have been recorded."));

        assertTrue(
                output.contains(
                        "No academic program is selected"
                )
        );

        assertTrue(
                output.contains("Degree audit unavailable")
        );
    }

    @Test
    void handlesSelectedProgramWithoutStoredRequirements() {

        var report = new DegreeProgressService.ProgressReport(
                4,
                "Student With Unsupported Program",
                "Fredericton",
                YEAR,
                7,
                "Fictional Unsupported Program",
                "UNSUPPORTED",
                0,
                0.0,
                List.of(),
                List.of()
        );

        String output = render(report);

        assertTrue(
                output.contains(
                        "No program requirements are currently stored"
                )
        );

        assertTrue(
                output.contains(
                        "missing data, not confirmation"
                )
        );

        assertTrue(
                output.contains(
                        "Full degree audit is unsupported"
                )
        );
    }

    @Test
    void displaysDisclaimersAndRejectsNullArguments() {

        String output = render(reportWithPartialProgram());

        assertTrue(
                output.contains(
                        "No overall degree completion percentage"
                )
        );

        assertTrue(
                output.contains(
                        "Completed-course records are self-reported."
                )
        );

        assertTrue(output.contains("transfer credits"));
        assertTrue(output.contains("graduation eligibility"));
        assertTrue(output.contains("official UNB academic information"));
        assertTrue(output.contains("independent, unofficial student project"));

        assertThrows(
                IllegalArgumentException.class,
                () -> DegreeProgressViewer.display(
                        null,
                        System.out
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DegreeProgressViewer.display(
                        reportWithPartialProgram(),
                        null
                )
        );
    }
}
