package com.mehrabali.coursecompass;

/**
 * Launches the existing CourseCompass SQLite planner or the MySQL catalogue.
 *
 * The two modes are deliberately separate until the remaining repositories
 * and their SQL statements are compatible with MySQL.
 * Independent student project; not affiliated with or endorsed by UNB.
 */
public final class CourseCompassLauncher {

    private CourseCompassLauncher() {
        // Utility entry point.
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            showUsage();
            return;
        }

        switch (args[0].trim().toLowerCase(java.util.Locale.ROOT)) {
            case "sqlite" -> {
                System.out.println("Mode: SQLite planner (local student data)");
                Main.main(new String[0]);
            }
            case "mysql" -> {
                System.out.println("Mode: MySQL course catalogue (read only)");
                MySQLCourseConsole.main(new String[0]);
            }
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                showUsage();
            }
        }
    }

    private static void showUsage() {
        System.out.println("UNB CourseCompass - Choose a database mode");
        System.out.println("Usage: CourseCompassLauncher sqlite|mysql");
        System.out.println("  sqlite - existing local planner and student profiles");
        System.out.println("  mysql  - migrated read-only academic course catalogue");
        System.out.println("These modes do not share live updates to student data.");
    }
}
