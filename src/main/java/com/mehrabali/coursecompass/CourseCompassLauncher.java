package com.mehrabali.coursecompass;

/**
 * Launches the existing SQLite planner or the unified read-only MySQL dashboard.
 *
 * The two modes remain deliberately separate until every planner repository
 * supports MySQL. They do not share live updates to student data.
 * Independent student project; not affiliated with or endorsed by UNB.
 */
public final class CourseCompassLauncher {

    private CourseCompassLauncher() { }

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
                System.out.println("Mode: MySQL dashboard (read only)");
                MySQLDashboardConsole.main(new String[0]);
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
        System.out.println("  mysql  - read-only MySQL course catalogue and student profiles");
        System.out.println("These modes do not share live updates to student data.");
    }
}
