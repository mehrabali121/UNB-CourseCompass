
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the student-facing course exploration output.
 *
 * Uses constructed records in memory, so these tests
 * never modify SQLite databases or student profiles.
 */
public class CourseExplorationViewerTest {

    private static final String YEAR = "2026-2027";
    private static final String CAMPUS = "Fredericton";

    /**
     * Constructs a sample catalogue course.
     */
    private CourseRepository.Course course(
            String code,
            String title
    ) {

        return new CourseRepository.Course(
                1,
                code,
                title,
                4.0,
                YEAR,
                CAMPUS,
                "STRUCTURED",
                "UNKNOWN",
                "UNB Undergraduate Academic Calendar",
                "https://www.unb.ca/academics/calendar/",
                "2026-10-08"
        );
    }

    /**
     * Constructs a modeled prerequisite group.
     */
    private PrerequisiteEvaluator.GroupResult group(
            int number,
            List<String> accepted,
            List<String> recorded
    ) {

        return new PrerequisiteEvaluator.GroupResult(
                number,
                accepted,
                recorded,
                !recorded.isEmpty()
        );
    }

    /**
     * Constructs an advisory prerequisite evaluation.
     */
    private PrerequisiteEvaluator.Evaluation evaluation(
            String code,
            PrerequisiteEvaluator.Status status,
            List<PrerequisiteEvaluator.GroupResult> groups,
            String notes
    ) {

        return new PrerequisiteEvaluator.Evaluation(
                2,
                code,
                "Test Course",
                CAMPUS,
                YEAR,
                status,
                groups,
                notes,
                "https://www.unb.ca/academics/calendar/",
                "Advisory result. Official eligibility is unverified."
        );
    }

    /**
     * Constructs an exploration record.
     */
    private CourseExplorationService.ExploredCourse explored(
            String code,
            String title,
            CourseExplorationService.ExplorationStatus status,
            PrerequisiteEvaluator.Evaluation evaluation
    ) {

        return new CourseExplorationService.ExploredCourse(
                course(code, title),
                status,
                evaluation
        );
    }

    /**
     * Constructs an exploration result for a fictional student.
     */
    private CourseExplorationService.ExplorationResult result(
            int completedCount,
            List<CourseExplorationService.ExploredCourse> courses
    ) {

        return new CourseExplorationService.ExplorationResult(
                2,
                "CourseCompass Test Student",
                CAMPUS,
                YEAR,
                completedCount,
                courses
        );
    }

    /**
     * Captures console output for assertions.
     */
    private String render(
            CourseExplorationService.ExplorationResult result
    ) {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                buffer,
                true,
                StandardCharsets.UTF_8
        )) {

            CourseExplorationViewer.display(
                    result,
                    output
            );
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void displaysStudentDetailsAndCourseCounts() {

        var exploration = result(
                1,
                List.of(
                        explored(
                                "CS1073",
                                "Introduction to Computer Programming I",
                                CourseExplorationService.ExplorationStatus
                                        .ALREADY_COMPLETED,
                                null
                        ),
                        explored(
                                "CS1083",
                                "Introduction to Computer Programming II",
                                CourseExplorationService.ExplorationStatus
                                        .RECORDED_REQUIREMENTS_MET,
                                null
                        )
                )
        );

        String output = render(exploration);

        assertTrue(output.contains(
                "Student: CourseCompass Test Student"
        ));

        assertTrue(output.contains("Profile ID: 2"));
        assertTrue(output.contains("Campus: Fredericton"));

        assertTrue(output.contains(
                "Catalogue year: 2026-2027"
        ));

        assertTrue(output.contains(
                "Recorded completed courses: 1"
        ));

        assertTrue(output.contains(
                "Courses in local catalogue: 2"
        ));
    }

    @Test
    void displaysCompletedAndMatchedCourseCategories() {

        var exploration = result(
                1,
                List.of(
                        explored(
                                "CS1073",
                                "Introduction to Computer Programming I",
                                CourseExplorationService.ExplorationStatus
                                        .ALREADY_COMPLETED,
                                null
                        ),
                        explored(
                                "CS1083",
                                "Introduction to Computer Programming II",
                                CourseExplorationService.ExplorationStatus
                                        .RECORDED_REQUIREMENTS_MET,
                                evaluation(
                                        "CS1083",
                                        PrerequisiteEvaluator.Status
                                                .RECORDED_REQUIREMENTS_MET,
                                        List.of(
                                                group(
                                                        1,
                                                        List.of("CS1073"),
                                                        List.of("CS1073")
                                                )
                                        ),
                                        "Minimum grade must be checked."
                                )
                        )
                )
        );

        String output = render(exploration);

        assertTrue(output.contains("ALREADY_COMPLETED: 1"));

        assertTrue(output.contains(
                "RECORDED_REQUIREMENTS_MET: 1"
        ));

        assertTrue(output.contains(
                "Exploration status: ALREADY_COMPLETED"
        ));

        assertTrue(output.contains(
                "Exploration status: RECORDED_REQUIREMENTS_MET"
        ));

        assertTrue(output.contains(
                "Recorded match: CS1073"
        ));

        assertTrue(output.contains(
                "Grades and official eligibility"
        ));

        assertTrue(output.contains(
                "minimum grades or official eligibility"
        ));
    }

    @Test
    void displaysMissingPrerequisiteGroups() {

        var exploration = result(
                0,
                List.of(
                        explored(
                                "CS1083",
                                "Introduction to Computer Programming II",
                                CourseExplorationService.ExplorationStatus
                                        .MISSING_REQUIREMENTS,
                                evaluation(
                                        "CS1083",
                                        PrerequisiteEvaluator.Status
                                                .MISSING_REQUIREMENTS,
                                        List.of(
                                                group(
                                                        1,
                                                        List.of("CS1073"),
                                                        List.of()
                                                )
                                        ),
                                        "Requires CS1073."
                                )
                        )
                )
        );

        String output = render(exploration);

        assertTrue(output.contains(
                "MISSING_REQUIREMENTS: 1"
        ));

        assertTrue(output.contains(
                "Prerequisite group 1: CS1073"
        ));

        assertTrue(output.contains(
                "Recorded match: NONE"
        ));

        assertTrue(output.contains(
                "Academic notes: Requires CS1073."
        ));

        assertTrue(output.contains(
                "Displayed prerequisite groups are connected by AND."
        ));
    }

    @Test
    void warnsAboutPartialPrerequisiteInformation() {

        var exploration = result(
                1,
                List.of(
                        explored(
                                "CS2413",
                                "Information Security",
                                CourseExplorationService.ExplorationStatus
                                        .PARTIAL_DATA,
                                evaluation(
                                        "CS2413",
                                        PrerequisiteEvaluator.Status
                                                .PARTIAL_DATA,
                                        List.of(
                                                group(
                                                        1,
                                                        List.of("CS1083"),
                                                        List.of("CS1083")
                                                ),
                                                group(
                                                        2,
                                                        List.of("CS1543"),
                                                        List.of()
                                                )
                                        ),
                                        "MATH2203 alternative is not modeled."
                                )
                        )
                )
        );

        String output = render(exploration);

        assertTrue(output.contains("PARTIAL_DATA: 1"));

        assertTrue(output.contains(
                "No definitive eligibility conclusion"
        ));

        assertTrue(output.contains(
                "Prerequisite group 1: CS1083"
        ));

        assertTrue(output.contains(
                "Prerequisite group 2: CS1543"
        ));

        assertTrue(output.contains("MATH2203"));
        assertTrue(output.contains("Recorded match: NONE"));
    }

    @Test
    void handlesCatalogueWithoutLoadedCourses() {

        String output = render(
                result(0, List.of())
        );

        assertTrue(output.contains(
                "Courses in local catalogue: 0"
        ));

        assertTrue(output.contains(
                "No courses are currently loaded"
        ));

        assertTrue(output.contains(
                "This does not mean UNB offers no courses"
        ));

        assertTrue(output.contains(
                "official UNB information"
        ));
    }

    @Test
    void rejectsNullArguments() {

        assertThrows(
                IllegalArgumentException.class,
                () -> CourseExplorationViewer.display(
                        null,
                        System.out
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> CourseExplorationViewer.display(
                        result(0, List.of()),
                        null
                )
        );
    }
}
