
-- ============================================================
-- UNB COURSECOMPASS
-- Fredericton Computer Science prerequisite seed
--
-- Academic calendar: 2026-2027
-- Verified: 2026-10-08
--
-- Source:
-- https://www.unb.ca/academics/calendar/undergraduate/current/frederictoncourses/computer-science/index.html
--
-- Run AFTER seed_courses.sql.
--
-- IMPORTANT:
-- PARTIAL requirements must never be treated as confirmed
-- eligibility simply because stored groups are satisfied.
--
-- The UNB CS calendar requires a minimum C or CR grade
-- for CS prerequisites. Grades are not yet tracked here.
--
-- Co-requisites are notes only at this stage.
--
-- This SQL is designed to be run more than once.
-- ============================================================


-- 1. Assign prerequisite coverage statuses and notes.

UPDATE course_campuses
SET
    prerequisite_status =
        CASE (
            SELECT c.course_code
            FROM courses c
            WHERE c.course_id = course_campuses.course_id
        )
            WHEN 'CS1073' THEN 'NONE'
            WHEN 'CS1083' THEN 'STRUCTURED'
            WHEN 'CS1203' THEN 'NONE'
            WHEN 'CS1303' THEN 'NONE'
            WHEN 'CS1543' THEN 'STRUCTURED'
            WHEN 'CS2043' THEN 'STRUCTURED'
            WHEN 'CS2253' THEN 'NONE'
            WHEN 'CS2263' THEN 'PARTIAL'
            WHEN 'CS2383' THEN 'PARTIAL'
            WHEN 'CS2413' THEN 'PARTIAL'
            ELSE prerequisite_status
        END,

    prerequisite_notes =
        CASE (
            SELECT c.course_code
            FROM courses c
            WHERE c.course_id = course_campuses.course_id
        )
            WHEN 'CS1073' THEN
                'No course prerequisite listed in the source.'

            WHEN 'CS1083' THEN
                'Requires CS1073. Minimum C or CR grade applies.'

            WHEN 'CS1203' THEN
                'No course prerequisite listed in the source.'

            WHEN 'CS1303' THEN
                'No course prerequisite listed in the source.'

            WHEN 'CS1543' THEN
                'Requires CS1073. Minimum C or CR grade applies.'

            WHEN 'CS2043' THEN
                'Requires CS1083. Minimum C or CR grade applies.'

            WHEN 'CS2253' THEN
                'No prerequisite listed. CS2263 is a co-requisite, '
                || 'which is not yet modeled by the eligibility engine.'

            WHEN 'CS2263' THEN
                'CS1023 OR CS1083. Only CS1083 is currently '
                || 'represented in structured options. '
                || 'Minimum C or CR grade applies.'

            WHEN 'CS2383' THEN
                '(CS1083 OR ECE4403) AND '
                || '(CS1303 OR MATH2203). '
                || 'ECE4403 and MATH2203 are not yet represented. '
                || 'Minimum C or CR grade applies.'

            WHEN 'CS2413' THEN
                'CS1083 AND CS1543 AND '
                || '(CS1303 OR MATH2203). '
                || 'MATH2203 is not yet represented. '
                || 'Minimum C or CR grade applies.'

            ELSE prerequisite_notes
        END

WHERE campus_id = (
    SELECT campus_id
    FROM campuses
    WHERE campus_name = 'Fredericton'
)
AND course_id IN (
    SELECT course_id
    FROM courses
    WHERE academic_year = '2026-2027'
      AND course_code IN (
          'CS1073', 'CS1083', 'CS1203',
          'CS1303', 'CS1543', 'CS2043',
          'CS2253', 'CS2263', 'CS2383',
          'CS2413'
      )
);


-- 2. Create AND groups.
--
-- Every group must be satisfied for eligibility.
-- Each group can eventually have multiple OR options.
--
-- Partial course records intentionally have incomplete
-- groups. Their status must remain PARTIAL.

WITH group_definitions (
    course_code,
    group_number
) AS (
    VALUES
        ('CS1083', 1),
        ('CS1543', 1),
        ('CS2043', 1),
        ('CS2263', 1),
        ('CS2383', 1),
        ('CS2383', 2),
        ('CS2413', 1),
        ('CS2413', 2),
        ('CS2413', 3)
)
INSERT INTO prerequisite_groups (
    course_campus_id,
    group_number
)
SELECT
    cc.course_campus_id,
    g.group_number
FROM group_definitions g
JOIN courses c
    ON c.course_code = g.course_code
   AND c.academic_year = '2026-2027'
JOIN course_campuses cc
    ON cc.course_id = c.course_id
JOIN campuses ca
    ON ca.campus_id = cc.campus_id
   AND ca.campus_name = 'Fredericton'
WHERE NOT EXISTS (
    SELECT 1
    FROM prerequisite_groups existing
    WHERE existing.course_campus_id = cc.course_campus_id
      AND existing.group_number = g.group_number
);


-- 3. Add known prerequisite options.
--
-- This seed intentionally does not invent records for
-- CS1023, ECE4403, or MATH2203.

WITH option_definitions (
    course_code,
    group_number,
    required_code
) AS (
    VALUES
        ('CS1083', 1, 'CS1073'),
        ('CS1543', 1, 'CS1073'),
        ('CS2043', 1, 'CS1083'),
        ('CS2263', 1, 'CS1083'),
        ('CS2383', 1, 'CS1083'),
        ('CS2383', 2, 'CS1303'),
        ('CS2413', 1, 'CS1083'),
        ('CS2413', 2, 'CS1543'),
        ('CS2413', 3, 'CS1303')
)
INSERT INTO prerequisite_options (
    group_id,
    required_course_id
)
SELECT
    pg.group_id,
    required.course_id
FROM option_definitions o
JOIN courses target
    ON target.course_code = o.course_code
   AND target.academic_year = '2026-2027'
JOIN course_campuses cc
    ON cc.course_id = target.course_id
JOIN campuses ca
    ON ca.campus_id = cc.campus_id
   AND ca.campus_name = 'Fredericton'
JOIN prerequisite_groups pg
    ON pg.course_campus_id = cc.course_campus_id
   AND pg.group_number = o.group_number
JOIN courses required
    ON required.course_code = o.required_code
   AND required.academic_year = '2026-2027'
WHERE NOT EXISTS (
    SELECT 1
    FROM prerequisite_options existing
    WHERE existing.group_id = pg.group_id
      AND existing.required_course_id = required.course_id
);
