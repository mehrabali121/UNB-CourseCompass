
package com.mehrabali.coursecompass;

import java.util.Scanner;

/**
 * Main entry point for UNB CourseCompass.
 *
 * An unofficial academic planning assistant
 * for University of New Brunswick students.
 */
public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        displayWelcome();

        boolean running = true;

        while (running) {

            displayMenu();

            System.out.print("Enter your choice (0-9): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                    showComingSoon("Student Profiles");
                    break;

                case "2":
                    showComingSoon("Course Search");
                    break;

                case "3":
                    showComingSoon("Completed Courses");
                    break;

                case "4":
                    showComingSoon("Prerequisite Checker");
                    break;

                case "5":
                    showComingSoon("Course Exploration");
                    break;

                case "6":
                    showComingSoon("Term Planner");
                    break;

                case "7":
                    showComingSoon("What-If Planner");
                    break;

                case "8":
                    showComingSoon("Degree Progress");
                    break;

                case "9":
                    showComingSoon("Data Coverage and Sources");
                    break;

                case "0":
                    System.out.println();
                    System.out.println("Thank you for using UNB CourseCompass!");
                    running = false;
                    break;

                default:
                    System.out.println();
                    System.out.println(
                        "Invalid choice. Please enter a number from 0 to 9."
                    );
            }

            System.out.println();
        }

        scanner.close();
    }

    /**
     * Displays the application welcome screen.
     */
    private static void displayWelcome() {

        System.out.println("====================================");
        System.out.println("           UNB COURSECOMPASS        ");
        System.out.println("     Unofficial Academic Planner    ");
        System.out.println("====================================");

        System.out.println();

        System.out.println(
            "This is an independent student project and is not "
            + "affiliated with or endorsed by the University "
            + "of New Brunswick."
        );

        System.out.println(
            "Academic requirements and course information must "
            + "be verified against official UNB sources."
        );

        System.out.println();
    }

    /**
     * Displays the main navigation menu.
     */
    private static void displayMenu() {

        System.out.println("========== MAIN MENU ==========");
        System.out.println("1. Student Profiles");
        System.out.println("2. Search Courses");
        System.out.println("3. My Completed Courses");
        System.out.println("4. Check Prerequisites");
        System.out.println("5. Explore Possible Courses");
        System.out.println("6. My Term Plans");
        System.out.println("7. What-If Planner");
        System.out.println("8. Degree Progress");
        System.out.println("9. Data Coverage and Sources");
        System.out.println("0. Exit");
        System.out.println("===============================");
    }

    /**
     * Displays a placeholder for features
     * that will be implemented later.
     *
     * @param featureName the name of the selected feature
     */
    private static void showComingSoon(String featureName) {

        System.out.println();
        System.out.println("Selected: " + featureName);
        System.out.println("This feature is not implemented yet.");
    }
}
