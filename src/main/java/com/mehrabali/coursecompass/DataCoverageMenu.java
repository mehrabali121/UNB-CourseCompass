
package com.mehrabali.coursecompass;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Interactive console menu for viewing academic data
 * coverage, verification sources, and limitations.
 *
 * This menu does not modify stored academic data.
 */
public final class DataCoverageMenu {

    private final Scanner scanner;
    private final DataCoverageService service;

    /**
     * Normal application constructor.
     */
    public DataCoverageMenu(Scanner scanner) {
        this(scanner, new DataCoverageService());
    }

    /**
     * Injectable constructor for isolated testing.
     */
    public DataCoverageMenu(
            Scanner scanner,
            DataCoverageService service
    ) {
        this.scanner = Objects.requireNonNull(
                scanner,
                "Scanner cannot be null."
        );

        this.service = Objects.requireNonNull(
                service,
                "Data coverage service cannot be null."
        );
    }

    /**
     * Runs the data coverage submenu until the user
     * chooses to return to the main menu.
     */
    public void run() {

        while (true) {

            printMenu();

            if (!scanner.hasNextLine()) {
                System.out.println(
                        "Input ended. Returning to the main menu."
                );
                return;
            }

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1" -> displayCoverageReport();

                case "0" -> {
                    System.out.println(
                            "Returning to the main menu."
                    );
                    return;
                }

                default -> System.out.println(
                        "Invalid option. Please enter 1 or 0."
                );
            }
        }
    }

    /**
     * Displays the available coverage-menu actions.
     */
    private void printMenu() {

        System.out.println();
        System.out.println(
                "========== DATA COVERAGE AND SOURCES =========="
        );

        System.out.println(
                "1. View Academic Data Coverage Report"
        );

        System.out.println("0. Return to Main Menu");

        System.out.print("Enter your choice: ");
    }

    /**
     * Retrieves a fresh read-only coverage report and
     * sends it to the presentation layer.
     */
    private void displayCoverageReport() {

        try {

            DataCoverageService.CoverageReport report =
                    service.assess();

            DataCoverageViewer.display(report);

        } catch (SQLException | IOException exception) {

            System.out.println();
            System.out.println(
                    "Unable to load the academic coverage report."
            );

            System.out.println(
                    "Database error: " + exception.getMessage()
            );

            System.out.println(
                    "No academic records were changed."
            );
        }
    }
}
