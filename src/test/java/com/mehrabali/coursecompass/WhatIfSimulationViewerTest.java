
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the text displayed by the What-If Results Viewer.
 *
 * These tests use constructed results rather than SQLite.
 * No student data is read or modified.
 */
public class WhatIfSimulationViewerTest {

    private static final String YEAR = "2026-2027";

    /**
     * Creates a comparison with a changed prerequisite status.
     */
    private WhatIfSimulationService.CourseComparison
            changedCourse() {

        return new WhatIfSimulationService.CourseComparison(
                "CS2043",
                "Introduction to Software Engineering",
                4.0,
                CourseExplorationService.ExplorationStatus
                        .MISSING_REQUIREMENTS,
                CourseExplorationService.ExplorationStatus
                        .RECORDED_REQUIREMENTS_MET,
                List.of("CS1083"),
                true,
                "Additional academic restrictions may apply."
        );
    }

    /**
     * Creates a course whose coverage stays partial.
     */
    private WhatIfSimulationService.CourseComparison
            partialCourse() {

        return new WhatIfSimulationService.CourseComparison(
                "CS2263",
                "Systems Software Development",
                4.0,
                CourseExplorationService.ExplorationStatus
                        .PARTIAL_DATA,
                CourseExplorationService.ExplorationStatus
                        .PARTIAL_DATA,
                List.of(),
                false,
                null
        );
    }

    /**
     * Creates a course already recorded as completed.
     */
    private WhatIfSimulationService.CourseComparison
            completedCourse() {

        return new WhatIfSimulationService.CourseComparison(
                "CS1073",
                "Introduction to Computer Programming I",
                4.0,
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                CourseExplorationService.ExplorationStatus
                        .ALREADY_COMPLETED,
                List.of(),
                false,
                null
        );
    }

    /**
     * Builds one realistic simulation result.
     */
    private WhatIfSimulationService.SimulationResult
            sampleResult() {

        return new WhatIfSimulationService.SimulationResult(
                2,
                "CourseCompass Test Student",
                "Fredericton",
                YEAR,
                List.of("CS1083"),
                1,
                1,
                List.of(
                        changedCourse(),
                        partialCourse(),
                        completedCourse()
                )
        );
    }

    /**
     * Captures output without printing to the normal console.
     */
    private String render(
            WhatIfSimulationService.SimulationResult result
    ) {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (PrintStream stream = new PrintStream(
                buffer,
                true,
                StandardCharsets.UTF_8
        )) {

            WhatIfSimulationViewer.display(result, stream);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void displaysStudentAndScenarioInformation() {

        String output = render(sampleResult());

        assertTrue(output.contains("WHAT-IF PLANNER"));
        assertTrue(output.contains("CourseCompass Test Student"));
        assertTrue(output.contains("Profile ID: 2"));
        assertTrue(output.contains("Campus: Fredericton"));

        assertTrue(
                output.contains("Catalogue year: " + YEAR)
        );

        assertTrue(
                output.contains("Actual recorded completions: 1")
        );

        assertTrue(output.contains("HYPOTHETICAL COMPLETIONS"));
        assertTrue(output.contains("+ CS1083"));

        assertTrue(
                output.contains(
                        "Courses with changed advisory status: 1"
                )
        );

        assertTrue(output.contains("Courses compared: 3"));
    }

    @Test
    void displaysChangedPrerequisiteResults() {

        String output = render(sampleResult());

        assertTrue(
                output.contains("CHANGED COURSE RESULTS")
        );

        assertTrue(
                output.contains(
                        "CS2043 - Introduction to Software Engineering"
                )
        );

        assertTrue(
                output.contains(
                        "Current: MISSING_REQUIREMENTS"
                )
        );

        assertTrue(
                output.contains(
                        "Simulated: RECORDED_REQUIREMENTS_MET"
                )
        );

        assertTrue(
                output.contains("Credit hours: 4.0")
        );

        assertTrue(
                output.contains(
                        "Hypothetical prerequisite matches: CS1083"
                )
        );

        assertTrue(
                output.contains(
                        "Additional academic restrictions may apply."
                )
        );
    }

    @Test
    void displaysUnchangedAndCompletedCourses() {

        String output = render(sampleResult());

        assertTrue(
                output.contains("UNCHANGED COURSE RESULTS")
        );

        assertTrue(output.contains("CS2263 | PARTIAL_DATA"));

        assertTrue(
                output.contains("CS1073 | ALREADY_COMPLETED")
        );

        assertTrue(
                output.contains(
                        "actually recorded as completed"
                )
        );
    }

    @Test
    void displaysNoChangesMessageWhenStatusesStaySame() {

        WhatIfSimulationService.SimulationResult result =
                new WhatIfSimulationService.SimulationResult(
                        2,
                        "Test Student",
                        "Fredericton",
                        YEAR,
                        List.of("CS1083"),
                        1,
                        0,
                        List.of(
                                partialCourse(),
                                completedCourse()
                        )
                );

        String output = render(result);

        assertTrue(
                output.contains(
                        "No course classifications changed"
                )
        );

        assertTrue(
                output.contains(
                        "This does not mean the hypothetical "
                        + "courses have no academic value."
                )
        );

        assertTrue(
                output.contains("CS2263 | PARTIAL_DATA")
        );

        assertTrue(
                output.contains("CS1073 | ALREADY_COMPLETED")
        );
    }

    @Test
    void displaysAcademicSafetyDisclaimers() {

        String output = render(sampleResult());

        assertTrue(
                output.contains(
                        "No completed-course records were added"
                )
        );

        assertTrue(
                output.contains(
                        "A hypothetical course is not an actual "
                        + "completed course."
                )
        );

        assertTrue(
                output.contains("registration eligibility")
        );

        assertTrue(output.contains("course offerings"));

        assertTrue(
                output.contains(
                        "PARTIAL_DATA and UNKNOWN classifications"
                )
        );

        assertTrue(
                output.contains("official UNB information")
        );

        assertTrue(
                output.contains(
                        "independent, unofficial student project"
                )
        );
    }

    @Test
    void rejectsNullArgumentsAndHandlesEmptyResults() {

        assertThrows(
                IllegalArgumentException.class,
                () -> WhatIfSimulationViewer.display(
                        null,
                        System.out
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> WhatIfSimulationViewer.display(
                        sampleResult(),
                        null
                )
        );

        WhatIfSimulationService.SimulationResult empty =
                new WhatIfSimulationService.SimulationResult(
                        2,
                        "Test Student",
                        "Fredericton",
                        YEAR,
                        List.of("CS1083"),
                        0,
                        0,
                        List.of()
                );

        String output = render(empty);

        assertTrue(
                output.contains("Courses compared: 0")
        );

        assertTrue(
                output.contains(
                        "No course classifications changed"
                )
        );

        assertTrue(
                output.contains(
                        "Every compared course changed status."
                )
        );
    }
}
