
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the console presentation of prerequisite evaluations.
 *
 * These tests construct evaluation results in memory.
 * They do not change the real or test SQLite databases.
 */
public class PrerequisiteEvaluationViewerTest {

    private static final String SOURCE_URL =
            "https://www.unb.ca/academics/calendar/"
            + "undergraduate/current/frederictoncourses/"
            + "computer-science/index.html";

    /**
     * Captures the viewer's console output as text.
     */
    private String render(
            PrerequisiteEvaluator.Evaluation evaluation
    ) {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(
                buffer,
                true,
                StandardCharsets.UTF_8
        )) {
            PrerequisiteEvaluationViewer.display(
                    evaluation,
                    output
            );
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Creates one prerequisite group for testing.
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
     * Creates an advisory evaluation for a fictional student.
     */
    private PrerequisiteEvaluator.Evaluation evaluation(
            String courseCode,
            PrerequisiteEvaluator.Status status,
            List<PrerequisiteEvaluator.GroupResult> groups,
            String notes,
            String explanation
    ) {

        return new PrerequisiteEvaluator.Evaluation(
                2,
                courseCode,
                "Test Course",
                "Fredericton",
                "2026-2027",
                status,
                groups,
                notes,
                SOURCE_URL,
                explanation
        );
    }

    @Test
    void displaysRecordedRequirementsWithoutClaimingEligibility() {

        var result = evaluation(
                "CS1083",
                PrerequisiteEvaluator.Status.RECORDED_REQUIREMENTS_MET,
                List.of(
                        group(
                                1,
                                List.of("CS1073"),
                                List.of("CS1073")
                        )
                ),
                "Minimum C or CR grade applies.",
                "Recorded requirements matched; grades unverified."
        );

        String output = render(result);

        assertTrue(output.contains("Profile ID: 2"));
        assertTrue(output.contains("Course: CS1083"));
        assertTrue(output.contains(
                "Evaluation status: RECORDED_REQUIREMENTS_MET"
        ));
        assertTrue(output.contains("Accepted options: CS1073"));
        assertTrue(output.contains("Recorded match: CS1073"));
        assertTrue(output.contains("Minimum C or CR grade"));
        assertTrue(output.contains(SOURCE_URL));
        assertTrue(output.contains(
                "NOT an official registration eligibility decision"
        ));
    }

    @Test
    void displaysMissingRequirementsClearly() {

        var result = evaluation(
                "CS1083",
                PrerequisiteEvaluator.Status.MISSING_REQUIREMENTS,
                List.of(
                        group(
                                1,
                                List.of("CS1073"),
                                List.of()
                        )
                ),
                "Requires CS1073.",
                "No matching recorded prerequisite."
        );

        String output = render(result);

        assertTrue(output.contains(
                "Evaluation status: MISSING_REQUIREMENTS"
        ));
        assertTrue(output.contains("Recorded match: NONE"));
        assertTrue(output.contains(
                "No matching completion is saved"
        ));
        assertTrue(output.contains(
                "No matching recorded prerequisite."
        ));
    }

    @Test
    void warnsAboutPartialPrerequisiteInformation() {

        var result = evaluation(
                "CS2413",
                PrerequisiteEvaluator.Status.PARTIAL_DATA,
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
                        ),
                        group(
                                3,
                                List.of("CS1303"),
                                List.of("CS1303")
                        )
                ),
                "CS1303 OR MATH2203. Alternative not modeled.",
                "Academic prerequisite coverage is incomplete."
        );

        String output = render(result);

        assertTrue(output.contains(
                "Evaluation status: PARTIAL_DATA"
        ));

        assertTrue(output.contains("Group 1"));
        assertTrue(output.contains("Group 2"));
        assertTrue(output.contains("Group 3"));

        assertTrue(output.contains("Recorded match: CS1083"));
        assertTrue(output.contains("Recorded match: NONE"));
        assertTrue(output.contains("Recorded match: CS1303"));

        assertTrue(output.contains(
                "All displayed groups are connected by AND"
        ));

        assertTrue(output.contains("MATH2203"));

        assertTrue(output.contains(
                "Some prerequisite alternatives"
        ));

        assertTrue(output.contains(
                "NOT an official registration eligibility decision"
        ));
    }

    @Test
    void showsCorequisiteNoteWhenNoPrerequisitesListed() {

        var result = evaluation(
                "CS2253",
                PrerequisiteEvaluator.Status.NO_LISTED_PREREQUISITES,
                List.of(),
                "CS2263 is a co-requisite.",
                "Other restrictions may still apply."
        );

        String output = render(result);

        assertTrue(output.contains(
                "Evaluation status: NO_LISTED_PREREQUISITES"
        ));

        assertTrue(output.contains(
                "No structured prerequisite groups are available"
        ));

        assertTrue(output.contains(
                "CS2263 is a co-requisite"
        ));

        assertTrue(output.contains(
                "Other restrictions may still apply"
        ));

        assertTrue(output.contains(
                "NOT an official registration eligibility decision"
        ));
    }

    @Test
    void handlesUnknownCourseResult() {

        String output = render(null);

        assertTrue(output.contains(
                "No matching course was found"
        ));

        assertTrue(output.contains(
                "This does not mean the course is unavailable"
        ));
    }

    @Test
    void rejectsNullOutputStream() {

        var result = evaluation(
                "CS1083",
                PrerequisiteEvaluator.Status.UNKNOWN,
                List.of(),
                null,
                "Prerequisites unknown."
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> PrerequisiteEvaluationViewer.display(
                        result,
                        null
                )
        );
    }
}
