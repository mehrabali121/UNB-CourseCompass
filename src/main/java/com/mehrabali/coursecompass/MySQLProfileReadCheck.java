package com.mehrabali.coursecompass;

import java.sql.SQLException;
import java.util.List;

/** Read-only validation of the migrated MySQL profile data. */
public final class MySQLProfileReadCheck {
    private MySQLProfileReadCheck() { }

    public static void main(String[] args) {
        try {
            MySQLProfileRepository repository = new MySQLProfileRepository();
            List<MySQLProfileRepository.Campus> campuses = repository.findCampuses();
            List<MySQLProfileRepository.Profile> profiles = repository.findAll();
            List<MySQLProfileRepository.Program> programs =
                    repository.findPrograms(1, "2026-2027");

            System.out.println("UNB CourseCompass - MySQL Profile Read Check");
            System.out.println("Campuses: " + campuses.size());
            System.out.println("Profiles: " + profiles.size());
            System.out.println("Sourced Fredericton 2026-2027 programs: " + programs.size());

            if (campuses.size() != 2 || profiles.size() != 1 || !programs.isEmpty()) {
                throw new SQLException("Unexpected migrated profile or program counts.");
            }

            MySQLProfileRepository.Profile profile = profiles.get(0);
            int completions = repository.countCompletedCourses(profile.id());
            System.out.println("Migrated profile ID: " + profile.id());
            System.out.println("Profile campus: " + profile.campusName());
            System.out.println("Completed courses: " + completions);

            if (profile.id() != 2 || profile.campusId() != 1 || completions != 1) {
                throw new SQLException("Migrated profile relationships differ from verified inventory.");
            }

            System.out.println("MYSQL PROFILE READ CHECK PASSED (READ ONLY)");
        } catch (SQLException exception) {
            System.err.println("MYSQL PROFILE READ CHECK FAILED: " + exception.getMessage());
            System.exit(1);
        }
    }
}
