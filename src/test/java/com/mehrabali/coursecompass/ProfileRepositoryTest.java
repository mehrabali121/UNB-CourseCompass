
package com.mehrabali.coursecompass;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Automated tests for student profile persistence and validation.
 *
 * Uses a separate SQLite database for every test.
 * Never accesses the application's real student database.
 */
public class ProfileRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private ProfileRepository repository;

    /**
     * Creates a new temporary SQLite database before each test.
     */
    @BeforeEach
    void setUp() throws Exception {

        databasePath = temporaryDirectory.resolve("profiles.db");

        String schema;

        try (InputStream input = getClass()
                .getResourceAsStream("/db/schema.sql")) {

            assertNotNull(input, "schema.sql must exist");

            schema = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        StringBuilder cleaned = new StringBuilder();

        for (String line : schema.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");

            for (String command : cleaned.toString().split(";")) {

                String sql = command.trim();

                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }

        repository = new ProfileRepository(databasePath);
        repository.initializeCampuses();
    }

    @Test
    void campusesAreInitializedCorrectly() throws Exception {

        List<ProfileRepository.Campus> campuses =
                repository.findCampuses();

        assertEquals(2, campuses.size());
        assertEquals("Fredericton", campuses.get(0).name());
        assertEquals("Saint John", campuses.get(1).name());

        // Initialization must be safe to repeat.
        repository.initializeCampuses();

        assertEquals(2, repository.findCampuses().size());
    }

    @Test
    void createsAndRetrievesProfile() throws Exception {

        int profileId = repository.createProfile(
                "Fictional Student",
                1,
                null,
                "2026-2027"
        );

        assertTrue(profileId > 0);

        List<ProfileRepository.Profile> profiles =
                repository.findAll();

        assertEquals(1, profiles.size());

        ProfileRepository.Profile profile = profiles.get(0);

        assertEquals(profileId, profile.id());
        assertEquals("Fictional Student", profile.name());
        assertEquals(1, profile.campusId());
        assertEquals("Fredericton", profile.campusName());
        assertNull(profile.programId());
        assertEquals("2026-2027", profile.academicYear());
    }

    @Test
    void rejectsEmptyProfileName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createProfile(
                        "   ",
                        1,
                        null,
                        "2026-2027"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createProfile(
                        null,
                        1,
                        null,
                        "2026-2027"
                )
        );
    }

    @Test
    void rejectsInvalidCampus() {

        assertThrows(
                java.sql.SQLException.class,
                () -> repository.createProfile(
                        "Test Student",
                        999,
                        null,
                        "2026-2027"
                )
        );
    }

    @Test
    void updatesExistingProfile() throws Exception {

        int profileId = repository.createProfile(
                "Original Name",
                1,
                null,
                "2026-2027"
        );

        boolean updated = repository.updateProfile(
                profileId,
                "Updated Name",
                2,
                null,
                "2027-2028"
        );

        assertTrue(updated);

        ProfileRepository.Profile profile =
                repository.findAll().get(0);

        assertEquals("Updated Name", profile.name());
        assertEquals("Saint John", profile.campusName());
        assertEquals("2027-2028", profile.academicYear());
    }

    @Test
    void deletesExistingProfile() throws Exception {

        int profileId = repository.createProfile(
                "Temporary Student",
                1,
                null,
                null
        );

        assertEquals(1, repository.findAll().size());

        assertTrue(repository.deleteProfile(profileId));

        assertTrue(repository.findAll().isEmpty());
        assertFalse(repository.deleteProfile(profileId));
    }

    @Test
    void profilesPersistAcrossRepositoryInstances()
            throws Exception {

        int profileId = repository.createProfile(
                "Persistent Student",
                1,
                null,
                "2026-2027"
        );

        ProfileRepository reopenedRepository =
                new ProfileRepository(databasePath);

        List<ProfileRepository.Profile> profiles =
                reopenedRepository.findAll();

        assertEquals(1, profiles.size());
        assertEquals(profileId, profiles.get(0).id());
        assertEquals("Persistent Student", profiles.get(0).name());
    }

    @Test
    void rejectsUnverifiedProgramSelection() {

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createProfile(
                        "Test Student",
                        1,
                        999,
                        "2026-2027"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createProfile(
                        "Test Student",
                        1,
                        999,
                        null
                )
        );
    }

    @Test
    void rejectsBlankAcademicYear() {

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.createProfile(
                        "Test Student",
                        1,
                        null,
                        "   "
                )
        );
    }
}
