package com.mehrabali.coursecompass;

import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * One-time, carefully validated migration from local SQLite to MySQL.
 *
 * Run without arguments for a read-only preflight check.
 * Run with --migrate only after reviewing the preflight results.
 * MySQL password must be provided via COURSECOMPASS_MYSQL_PASSWORD.
 * This does not alter the existing SQLite file.
 */
public final class CourseCompassMySQLMigration {

    // Parent tables always come before referencing child tables.
    private static final String[] TABLES = {
            "academic_sources", "campuses", "programs", "courses",
            "course_campuses", "profiles", "completed_courses",
            "prerequisite_groups", "prerequisite_options", "term_plans",
            "planned_courses", "program_requirements", "requirement_courses"
    };

    private static final Path SQLITE_FILE =
            Path.of("data", "coursecompass.db").toAbsolutePath();
    private static final String MYSQL_URL =
            "jdbc:mysql://127.0.0.1:3306/coursecompass"
            + "?connectionTimeZone=UTC&sslMode=PREFERRED";

    private CourseCompassMySQLMigration() {
    }

    public static void main(String[] args) {
        boolean migrate = args.length == 1 && "--migrate".equals(args[0]);
        if (args.length != 0 && !migrate) {
            System.err.println("Usage: CourseCompassMySQLMigration [--migrate]");
            System.exit(1);
        }

        String password = System.getenv("COURSECOMPASS_MYSQL_PASSWORD");
        if (password == null || password.isEmpty()) {
            System.err.println("Missing COURSECOMPASS_MYSQL_PASSWORD environment variable.");
            System.exit(1);
        }
        if (!Files.isRegularFile(SQLITE_FILE)) {
            System.err.println("SQLite file not found: " + SQLITE_FILE);
            System.exit(1);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection source = config.createConnection("jdbc:sqlite:" + SQLITE_FILE);
             Connection target = DriverManager.getConnection(
                     MYSQL_URL, "coursecompass_app", password)) {
            validateSource(source);
            int total = 0;
            System.out.println(migrate ? "MIGRATION MODE" : "READ-ONLY PREFLIGHT MODE");
            System.out.println("Table                          SQLite  MySQL");
            for (String table : TABLES) {
                int sourceCount = count(source, table);
                int targetCount = count(target, table);
                System.out.printf("%-30s %5d %6d%n", table, sourceCount, targetCount);
                total += sourceCount;
                if (targetCount != 0) {
                    throw new SQLException(
                            "MySQL table is not empty: " + table
                            + ". Refusing to overwrite or duplicate records.");
                }
                validateColumns(source, target, table);
            }
            System.out.println("SQLite total records: " + total);
            if (!migrate) {
                System.out.println("PREFLIGHT PASSED. Nothing was copied or changed.");
                System.out.println("Review results before using --migrate.");
                return;
            }

            target.setAutoCommit(false);
            try {
                for (String table : TABLES) {
                    int inserted = copyTable(source, target, table);
                    if (inserted != count(source, table)
                            || inserted != count(target, table)) {
                        throw new SQLException("Count mismatch in " + table);
                    }
                    System.out.println("Copied " + table + ": " + inserted);
                }
                // InnoDB checks all foreign-key references during inserts.
                target.commit();
                System.out.println("MIGRATION COMMITTED: " + total + " records.");
            } catch (Exception error) {
                target.rollback();
                throw error;
            } finally {
                target.setAutoCommit(true);
            }
        } catch (Exception error) {
            System.err.println("STOPPED: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void validateSource(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
            if (!integrity.next() || !"ok".equalsIgnoreCase(integrity.getString(1))) {
                throw new SQLException("SQLite integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet keys = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (keys.next()) {
                throw new SQLException("SQLite foreign key violations detected");
            }
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            results.next();
            return results.getInt(1);
        }
    }

    private static void validateColumns(Connection source, Connection target, String table)
            throws SQLException {
        String sourceColumns = columns(source, table);
        String targetColumns = columns(target, table);
        if (!sourceColumns.equalsIgnoreCase(targetColumns)) {
            throw new SQLException("Column mismatch for " + table + ": SQLite ["
                    + sourceColumns + "] / MySQL [" + targetColumns + "]");
        }
    }

    private static String columns(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            ResultSetMetaData metadata = results.getMetaData();
            StringBuilder names = new StringBuilder();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                if (i > 1) {
                    names.append(",");
                }
                names.append(metadata.getColumnName(i));
            }
            return names.toString();
        }
    }

    private static int copyTable(Connection source, Connection target, String table)
            throws SQLException {
        try (Statement reader = source.createStatement();
             ResultSet rows = reader.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData metadata = rows.getMetaData();
            int columnCount = metadata.getColumnCount();
            String[] names = new String[columnCount];
            Arrays.setAll(names, i -> {
                try {
                    return "`" + metadata.getColumnName(i + 1) + "`";
                } catch (SQLException error) {
                    throw new IllegalStateException(error);
                }
            });
            String[] placeholders = new String[columnCount];
            Arrays.fill(placeholders, "?");
            String sql = "INSERT INTO `" + table + "` ("
                    + String.join(",", names) + ") VALUES ("
                    + String.join(",", placeholders) + ")";
            try (PreparedStatement insert = target.prepareStatement(sql)) {
                int copied = 0;
                while (rows.next()) {
                    for (int index = 1; index <= columnCount; index++) {
                        // SQLite date/time values are stored as ISO strings.
                        // MySQL DATE/TIMESTAMP columns accept those strings.
                        insert.setObject(index, rows.getObject(index));
                    }
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("Unexpected insert count in " + table);
                    }
                    copied++;
                }
                return copied;
            }
        }
    }
}
