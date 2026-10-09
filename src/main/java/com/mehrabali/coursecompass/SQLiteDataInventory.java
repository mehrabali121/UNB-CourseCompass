
package com.mehrabali.coursecompass;

import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SQLiteDataInventory {

    private static final String[] TABLES = {
            "academic_sources",
            "campuses",
            "programs",
            "courses",
            "course_campuses",
            "profiles",
            "completed_courses",
            "prerequisite_groups",
            "prerequisite_options",
            "term_plans",
            "planned_courses",
            "program_requirements",
            "requirement_courses"
    };

    private SQLiteDataInventory() {
    }

    public static void main(String[] args) {

        Path databasePath = Path.of(
                "data", "coursecompass.db"
        ).toAbsolutePath();

        if (!Files.isRegularFile(databasePath)) {
            System.err.println(
                    "SQLite database not found: " + databasePath
            );
            System.exit(1);
        }

        String url = "jdbc:sqlite:" + databasePath;

        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);

        try (Connection connection =
                     config.createConnection(url)) {

            System.out.println("UNB CourseCompass");
            System.out.println("SQLite Data Inventory");
            System.out.println("--------------------------");

            int totalRecords = 0;

            try (Statement statement =
                         connection.createStatement()) {

                for (String table : TABLES) {

                    try (ResultSet result =
                                 statement.executeQuery(
                                         "SELECT COUNT(*) FROM " + table
                                 )) {

                        result.next();

                        int count = result.getInt(1);
                        totalRecords += count;

                        System.out.printf(
                                "%-25s %d%n",
                                table,
                                count
                        );
                    }
                }

                System.out.println("--------------------------");
                System.out.println(
                        "Total records: " + totalRecords
                );

                try (ResultSet result =
                             statement.executeQuery(
                                     "PRAGMA integrity_check"
                             )) {

                    if (!result.next()
                            || !"ok".equalsIgnoreCase(
                                    result.getString(1))) {

                        throw new SQLException(
                                "SQLite integrity check failed."
                        );
                    }

                    System.out.println("Integrity check: ok");
                }

                try (ResultSet result =
                             statement.executeQuery(
                                     "PRAGMA foreign_key_check"
                             )) {

                    if (result.next()) {
                        throw new SQLException(
                                "Foreign key violations detected."
                        );
                    }

                    System.out.println(
                            "Foreign key violations: NONE"
                    );
                }
            }

        } catch (Exception exception) {
            System.err.println(
                    "Inventory failed: " + exception.getMessage()
            );
            System.exit(1);
        }
    }
}
