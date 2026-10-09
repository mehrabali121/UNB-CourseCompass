
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive console menu for advisory degree progress.
 *
 * Completed courses are self-reported.
 * No academic records are modified by this menu.
 * This is not an official UNB degree audit.
 */
public final class DegreeProgressMenu {

    private final Scanner scanner;
    private final ProfileRepository profileRepository;
    private final DegreeProgressService progressService;

    /**
     * Creates the menu using the application's shared Scanner.
     */
    public DegreeProgressMenu(Scanner scanner) {

        if (scanner == null) {
            throw new IllegalArgumentException(
                    "Scanner cannot be null."
            );
        }

        this.scanner = scanner;
        this.profileRepository = new ProfileRepository();
        this.progressService = new DegreeProgressService();
    }

    /**
     * Runs the Degree Progress submenu until the user
     * chooses to return to the main menu.
     */
    public void run() {

        boolean running = true;

        while (running) {

            printMenu();

            String choice = readLine("Choice (0-1): ");

            switch (choice) {

                case "1" -> {

                    try {

                        viewDegreeProgress();

                    } catch (SQLException | IOException exception) {

                        System.out.println();
                        System.out.println(
                                "Unable to load degree progress: "
                                + exception.getMessage()
                        );

                    } catch (IllegalArgumentException
                            | IllegalStateException exception) {

                        System.out.println();
                        System.out.println(
                                "Unable to assess degree progress: "
                                + exception.getMessage()
                        );
                    }
                }

                case "0" -> running = false;

                default -> System.out.println(
                        "Invalid choice. Enter 0 or 1."
                );
            }
        }
    }

    /**
     * Displays the available Degree Progress actions.
     */
    private static void printMenu() {

        System.out.println();
        System.out.println("========== DEGREE PROGRESS ==========");
        System.out.println("1. View My Degree Progress");
        System.out.println("0. Back to Main Menu");
        System.out.println("=====================================");
        System.out.println(
                "Completed courses are self-reported."
        );
        System.out.println(
                "Degree progress is advisory, not an official audit."
        );
    }

    /**
     * Selects a student and displays the read-only report.
     */
    private void viewDegreeProgress()
            throws SQLException, IOException {

        ProfileRepository.Profile selectedProfile =
                selectProfile();

        if (selectedProfile == null) {
            return;
        }

        System.out.println();
        System.out.println("----- PROGRESS REQUEST -----");

        System.out.println(
                "Student: " + selectedProfile.name()
        );

        System.out.println(
                "Campus: " + selectedProfile.campusName()
        );

        System.out.println(
                "Catalogue year: "
                + displayOptional(selectedProfile.academicYear())
        );

        System.out.println(
                "Selected program: "
                + displayOptional(selectedProfile.programName())
        );

        System.out.println();

        System.out.println(
                "Only recorded courses and stored program "
                + "requirements can be assessed."
        );

        System.out.println(
                "No official graduation eligibility or overall "
                + "degree completion percentage will be calculated."
        );

        DegreeProgressService.ProgressReport report =
                progressService.assess(selectedProfile.id());

        DegreeProgressViewer.display(report);
    }

    /**
     * Lists existing student profiles and reads a selection.
     */
    private ProfileRepository.Profile selectProfile()
            throws SQLException, IOException {

        List<ProfileRepository.Profile> profiles =
                profileRepository.findAll();

        System.out.println();
        System.out.println("----- SELECT STUDENT -----");

        if (profiles.isEmpty()) {

            System.out.println(
                    "No student profiles found."
            );

            System.out.println(
                    "Create a student profile from the main "
                    + "menu before viewing degree progress."
            );

            return null;
        }

        for (ProfileRepository.Profile profile : profiles) {

            System.out.println(
                    profile.id()
                    + ". "
                    + profile.name()
                    + " | "
                    + profile.campusName()
                    + " | Year: "
                    + displayOptional(profile.academicYear())
                    + " | Program: "
                    + displayOptional(profile.programName())
            );
        }

        while (true) {

            String input = readLine(
                    "Profile ID (0 to cancel): "
            );

            if ("0".equals(input)) {

                System.out.println(
                        "Degree progress request cancelled."
                );

                return null;
            }

            Integer profileId = parsePositiveInteger(input);

            if (profileId == null) {

                System.out.println(
                        "Enter a valid positive profile ID, "
                        + "or 0 to cancel."
                );

                continue;
            }

            for (ProfileRepository.Profile profile : profiles) {

                if (profile.id() == profileId) {
                    return profile;
                }
            }

            System.out.println(
                    "Choose an existing profile ID from the list."
            );
        }
    }

    /**
     * Parses a positive whole number without throwing on
     * malformed console input.
     */
    private static Integer parsePositiveInteger(String input) {

        if (input == null || input.isBlank()) {
            return null;
        }

        try {

            int value = Integer.parseInt(input);

            return value > 0 ? value : null;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Reads a single line using the shared Scanner.
     */
    private String readLine(String prompt) {

        System.out.print(prompt);

        return scanner.nextLine().trim();
    }

    /**
     * Displays missing optional profile information.
     */
    private static String displayOptional(String value) {

        return value == null || value.isBlank()
                ? "Not selected"
                : value;
    }
}
