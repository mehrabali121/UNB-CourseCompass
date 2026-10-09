
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the console presentation of academic data coverage.
 *
 * All reports contain fictional data assembled in memory.
 * No SQLite database or student records are modified.
 */
public class DataCoverageViewerTest {

    private static final String YEAR = "2026-2027";

    private CourseRepository.Course course(
            int id,
            String code,
            String campus,
            String prerequisiteStatus,
            String offeringStatus
    ) {

        return new CourseRepository.Course(
                id,
                code,
                "Fictional " + code + " Course",
                4.0,
                YEAR,
                campus,
                prerequisiteStatus,
                offeringStatus,
                "Fictional Course Catalogue",
                "https://example.org/course-calendar",
                "2026-10-08"
        );
    }

    private DataCoverageService.AcademicSource source() {

        return new DataCoverageService.AcademicSource(
                1,
                "Fictional Course Catalogue",
                "https://example.org/course-calendar",
                YEAR,
                "2026-10-08",
                "Test source only"
        );
    }

    private DataCoverageService.CoverageReport populatedReport() {

        return new DataCoverageService.CoverageReport(
                List.of(
                        course(
                                1,
                                "CS1073",
                                "Fredericton",
                                "NONE",
                                "UNKNOWN"
                        ),
                        course(
                                2,
                                "CS1083",
                                "Fredericton",
                                "STRUCTURED",
                                "VERIFIED_OFFERED"
                        ),
                        course(
                                3,
                                "CS2383",
                                "Fredericton",
                                "PARTIAL",
                                "VERIFIED_NOT_OFFERED"
                        ),
                        course(
                                4,
                                "CS2413",
                                "Saint John",
                                "UNKNOWN",
                                "UNKNOWN"
                        )
                ),
                List.of(source()),
                List.of(YEAR),
                1,
                1,
                1,
                1,
                1,
                1,
                2,
                3,
                1,
                1,
                1,
                2
        );
    }

    private DataCoverageService.CoverageReport emptyReport() {

        return new DataCoverageService.CoverageReport(
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private String render(
            DataCoverageService.CoverageReport report
    ) {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                buffer,
                true,
                StandardCharsets.UTF_8
        )) {

            DataCoverageViewer.display(report, output);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void displaysCourseCountsCampusesAndAcademicYears() {

        String output = render(populatedReport());

        assertTrue(
                output.contains("DATA COVERAGE AND SOURCES")
        );

        assertTrue(
                output.contains("Loaded campus-course records: 4")
        );

        assertTrue(
                output.contains(
                        "Academic years represented: 2026-2027"
                )
        );

        assertTrue(output.contains("Fredericton: 3"));
        assertTrue(output.contains("Saint John: 1"));

        assertTrue(
                output.contains(
                        "may appear more than once"
                )
        );

        assertTrue(
                output.contains(
                        "entire UNB catalogue"
                )
        );
    }

    @Test
    void displaysPrerequisiteCoverageAndPercentages() {

        String output = render(populatedReport());

        assertTrue(
                output.contains("PREREQUISITE COVERAGE")
        );

        assertTrue(
                output.contains(
                        "NONE (none listed in source): 1"
                )
        );

        assertTrue(
                output.contains(
                        "STRUCTURED (stored course relationships): 1"
                )
        );

        assertTrue(
                output.contains(
                        "PARTIAL (incomplete modeled requirements): 1"
                )
        );

        assertTrue(
                output.contains(
                        "UNKNOWN (not established): 1"
                )
        );

        assertTrue(
                output.contains(
                        "2 / 4 (50.0%)"
                )
        );

        assertTrue(
                output.contains(
                        "registration eligibility"
                )
        );
    }

    @Test
    void displaysOfferingAndProgramCoverage() {

        String output = render(populatedReport());

        assertTrue(
                output.contains("OFFERING INFORMATION")
        );

        assertTrue(
                output.contains("VERIFIED_OFFERED: 1")
        );

        assertTrue(
                output.contains("VERIFIED_NOT_OFFERED: 1")
        );

        assertTrue(
                output.contains("UNKNOWN: 2")
        );

        assertTrue(
                output.contains("Stored programs: 3")
        );

        assertTrue(output.contains("SUPPORTED: 1"));
        assertTrue(output.contains("PARTIAL: 1"));
        assertTrue(output.contains("UNSUPPORTED: 1"));

        assertTrue(
                output.contains(
                        "Stored program requirement rows: 2"
                )
        );
    }

    @Test
    void displaysAcademicSourceDetailsAndCourseStatuses() {

        String output = render(populatedReport());

        assertTrue(
                output.contains("ACADEMIC SOURCES")
        );

        assertTrue(
                output.contains("Stored sources: 1")
        );

        assertTrue(
                output.contains(
                        "[1] Fictional Course Catalogue"
                )
        );

        assertTrue(
                output.contains(
                        "Recorded verification date: 2026-10-08"
                )
        );

        assertTrue(
                output.contains(
                        "Source URL: https://example.org/course-calendar"
                )
        );

        assertTrue(
                output.contains("Notes: Test source only")
        );

        assertTrue(
                output.contains(
                        "CS2383 | Fredericton | 2026-2027"
                )
        );

        assertTrue(
                output.contains(
                        "Prerequisites: PARTIAL "
                        + "| Offering: VERIFIED_NOT_OFFERED"
                )
        );
    }

    @Test
    void handlesEmptyDataWithoutInventingCoverage() {

        String output = render(emptyReport());

        assertTrue(
                output.contains("Loaded campus-course records: 0")
        );

        assertTrue(
                output.contains("Academic years represented: None")
        );

        assertTrue(
                output.contains(
                        "No campus-course records are available."
                )
        );

        assertTrue(output.contains("0 / 0 (N/A)"));

        assertTrue(
                output.contains(
                        "No programs have been loaded"
                )
        );

        assertTrue(
                output.contains(
                        "No academic source records are available."
                )
        );

        assertTrue(
                output.contains(
                        "No courses are currently loaded."
                )
        );
    }

    @Test
    void displaysDisclaimersAndRejectsNullArguments() {

        String output = render(populatedReport());

        assertTrue(
                output.contains("IMPORTANT LIMITATIONS")
        );

        assertTrue(
                output.contains(
                        "Missing records do not mean that a UNB"
                )
        );

        assertTrue(
                output.contains(
                        "historical class averages"
                )
        );

        assertTrue(
                output.contains(
                        "not affiliated with or endorsed by UNB"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DataCoverageViewer.display(
                        null,
                        System.out
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DataCoverageViewer.display(
                        populatedReport(),
                        null
                )
        );
    }
}
