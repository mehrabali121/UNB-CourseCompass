
-- ============================================================
-- UNB COURSECOMPASS
-- Initial verified Fredericton Computer Science course seed
--
-- Academic calendar: 2026-2027
-- Verified on: 2026-10-08
--
-- Official source:
-- https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html
--
-- This is an independent, unofficial student project.
-- Course offerings are not confirmed by this file.
-- Prerequisite evaluation is not yet enabled.
--
-- The seed is designed to be safe to run more than once.
-- ============================================================

-- 1. Ensure the Fredericton campus exists.

INSERT OR IGNORE INTO campuses (
    campus_id,
    campus_name
)
VALUES (
    1,
    'Fredericton'
);


-- 2. Record the official source.

INSERT INTO academic_sources (
    source_title,
    source_url,
    academic_year,
    verified_on,
    notes
)
SELECT
    'UNB Undergraduate Calendar: Fredericton Computer Science',
    'https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html',
    '2026-2027',
    '2026-10-08',
    'Official UNB calendar. Course listings do not guarantee term availability.'
WHERE NOT EXISTS (
    SELECT 1
    FROM academic_sources
    WHERE source_url =
        'https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html'
      AND academic_year = '2026-2027'
);


-- 3. Add the 10 verified courses.
--
-- Existing course records are preserved.
-- Credit hours and titles come from the official calendar.

WITH verified_courses (
    course_code,
    course_title,
    credit_hours
) AS (
    VALUES
        ('CS1073',
         'Introduction to Computer Programming I (in Java)', 4),

        ('CS1083',
         'Introduction to Computer Programming II (in Java)', 4),

        ('CS1203',
         'Overview of Computer Science', 3),

        ('CS1303',
         'Discrete Structures', 4),

        ('CS1543',
         'Introduction to Databases', 4),

        ('CS2043',
         'Introduction to Software Engineering', 4),

        ('CS2253',
         'Machine Level Programming', 4),

        ('CS2263',
         'Systems Software Development', 4),

        ('CS2383',
         'Data Structures and Algorithms', 4),

        ('CS2413',
         'Information Security', 4)
)
INSERT INTO courses (
    course_code,
    course_title,
    credit_hours,
    academic_year,
    source_id,
    notes
)
SELECT
    v.course_code,
    v.course_title,
    v.credit_hours,
    '2026-2027',
    (
        SELECT source_id
        FROM academic_sources
        WHERE academic_year = '2026-2027'
          AND source_url =
              'https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html'
        ORDER BY source_id
        LIMIT 1
    ),
    'Verified Fredericton CS calendar entry.'
FROM verified_courses AS v
WHERE NOT EXISTS (
    SELECT 1
    FROM courses AS c
    WHERE c.course_code = v.course_code
      AND c.academic_year = '2026-2027'
);


-- 4. Associate the 10 courses with Fredericton.
--
-- Prerequisite and offering statuses use the schema defaults
-- of UNKNOWN.
--
-- This association identifies the catalogue source campus.
-- It does not claim that each course is scheduled this term.

INSERT INTO course_campuses (
    course_id,
    campus_id,
    source_id
)
SELECT
    c.course_id,
    ca.campus_id,
    (
        SELECT source_id
        FROM academic_sources
        WHERE academic_year = '2026-2027'
          AND source_url =
              'https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html'
        ORDER BY source_id
        LIMIT 1
    )
FROM courses AS c
JOIN campuses AS ca
    ON ca.campus_name = 'Fredericton'
WHERE c.academic_year = '2026-2027'
  AND c.course_code IN (
      'CS1073',
      'CS1083',
      'CS1203',
      'CS1303',
      'CS1543',
      'CS2043',
      'CS2253',
      'CS2263',
      'CS2383',
      'CS2413'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM course_campuses AS cc
      WHERE cc.course_id = c.course_id
        AND cc.campus_id = ca.campus_id
  );
