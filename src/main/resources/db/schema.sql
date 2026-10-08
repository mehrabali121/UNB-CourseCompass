
-- ============================================================
-- UNB CourseCompass
-- SQLite Database Schema
-- Version 1
--
-- Independent student project.
-- Not affiliated with or endorsed by UNB.
--
-- Academic records must come from verified UNB sources.
-- No fabricated university records are inserted here.
-- ============================================================

PRAGMA foreign_keys = ON;

-- ============================================================
-- 1. OFFICIAL ACADEMIC SOURCES
-- ============================================================

CREATE TABLE IF NOT EXISTS academic_sources (
    source_id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    academic_year TEXT NOT NULL,
    verified_on TEXT NOT NULL,
    notes TEXT,

    CHECK (length(trim(source_title)) > 0),
    CHECK (length(trim(source_url)) > 0),
    CHECK (verified_on GLOB '????-??-??')
);

-- ============================================================
-- 2. CAMPUSES
-- ============================================================

CREATE TABLE IF NOT EXISTS campuses (
    campus_id INTEGER PRIMARY KEY,
    campus_name TEXT NOT NULL UNIQUE,

    CHECK (
        campus_name IN ('Fredericton', 'Saint John')
    )
);

-- ============================================================
-- 3. ACADEMIC PROGRAMS
-- ============================================================

CREATE TABLE IF NOT EXISTS programs (
    program_id INTEGER PRIMARY KEY AUTOINCREMENT,
    program_name TEXT NOT NULL,
    campus_id INTEGER NOT NULL,
    academic_year TEXT NOT NULL,
    audit_status TEXT NOT NULL DEFAULT 'UNSUPPORTED',
    source_id INTEGER,
    notes TEXT,

    FOREIGN KEY (campus_id)
        REFERENCES campuses(campus_id),

    FOREIGN KEY (source_id)
        REFERENCES academic_sources(source_id),

    CHECK (
        audit_status IN (
            'SUPPORTED',
            'PARTIAL',
            'UNSUPPORTED'
        )
    ),

    UNIQUE (program_name, campus_id, academic_year)
);

-- ============================================================
-- 4. COURSES
-- ============================================================

CREATE TABLE IF NOT EXISTS courses (
    course_id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_code TEXT NOT NULL,
    course_title TEXT NOT NULL,
    credit_hours REAL NOT NULL,
    academic_year TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    notes TEXT,

    FOREIGN KEY (source_id)
        REFERENCES academic_sources(source_id),

    CHECK (length(trim(course_code)) > 0),
    CHECK (length(trim(course_title)) > 0),
    CHECK (credit_hours >= 0),

    UNIQUE (course_code, academic_year)
);

-- ============================================================
-- 5. COURSE APPLICABILITY BY CAMPUS
-- ============================================================

CREATE TABLE IF NOT EXISTS course_campuses (
    course_campus_id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,

    prerequisite_status TEXT NOT NULL DEFAULT 'UNKNOWN',
    prerequisite_notes TEXT,

    offering_status TEXT NOT NULL DEFAULT 'UNKNOWN',

    source_id INTEGER NOT NULL,

    FOREIGN KEY (course_id)
        REFERENCES courses(course_id),

    FOREIGN KEY (campus_id)
        REFERENCES campuses(campus_id),

    FOREIGN KEY (source_id)
        REFERENCES academic_sources(source_id),

    CHECK (
        prerequisite_status IN (
            'NONE',
            'STRUCTURED',
            'PARTIAL',
            'UNKNOWN'
        )
    ),

    CHECK (
        offering_status IN (
            'UNKNOWN',
            'VERIFIED_OFFERED',
            'VERIFIED_NOT_OFFERED'
        )
    ),

    UNIQUE (course_id, campus_id)
);

-- ============================================================
-- 6. LOCAL STUDENT PROFILES
-- ============================================================

CREATE TABLE IF NOT EXISTS profiles (
    profile_id INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_name TEXT NOT NULL,
    campus_id INTEGER NOT NULL,
    program_id INTEGER,
    academic_year TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (campus_id)
        REFERENCES campuses(campus_id),

    FOREIGN KEY (program_id)
        REFERENCES programs(program_id),

    CHECK (length(trim(profile_name)) > 0)
);

-- ============================================================
-- 7. COMPLETED COURSES
-- ============================================================

CREATE TABLE IF NOT EXISTS completed_courses (
    completion_id INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    completed_on TEXT,

    FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
        ON DELETE CASCADE,

    FOREIGN KEY (course_id)
        REFERENCES courses(course_id),

    UNIQUE (profile_id, course_id)
);

-- ============================================================
-- 8. PREREQUISITE GROUPS
--
-- All groups must be satisfied (AND).
-- Within each group, any listed option is sufficient (OR).
-- ============================================================

CREATE TABLE IF NOT EXISTS prerequisite_groups (
    group_id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_campus_id INTEGER NOT NULL,
    group_number INTEGER NOT NULL,

    FOREIGN KEY (course_campus_id)
        REFERENCES course_campuses(course_campus_id)
        ON DELETE CASCADE,

    CHECK (group_number > 0),

    UNIQUE (course_campus_id, group_number)
);

-- ============================================================
-- 9. PREREQUISITE OPTIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS prerequisite_options (
    option_id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    required_course_id INTEGER NOT NULL,

    FOREIGN KEY (group_id)
        REFERENCES prerequisite_groups(group_id)
        ON DELETE CASCADE,

    FOREIGN KEY (required_course_id)
        REFERENCES courses(course_id),

    UNIQUE (group_id, required_course_id)
);

-- ============================================================
-- 10. TERM PLANS
-- ============================================================

CREATE TABLE IF NOT EXISTS term_plans (
    plan_id INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id INTEGER NOT NULL,
    plan_name TEXT NOT NULL,
    academic_year TEXT NOT NULL,
    term_name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
        ON DELETE CASCADE,

    CHECK (length(trim(plan_name)) > 0),

    CHECK (
        term_name IN (
            'Fall',
            'Winter',
            'Summer',
            'Other'
        )
    )
);

-- ============================================================
-- 11. PLANNED COURSES
-- ============================================================

CREATE TABLE IF NOT EXISTS planned_courses (
    planned_course_id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,

    FOREIGN KEY (plan_id)
        REFERENCES term_plans(plan_id)
        ON DELETE CASCADE,

    FOREIGN KEY (course_id)
        REFERENCES courses(course_id),

    UNIQUE (plan_id, course_id)
);

-- ============================================================
-- 12. PROGRAM REQUIREMENTS
--
-- A requirement can represent:
-- 1. A required individual course.
-- 2. A minimum credit total within a verified category.
--
-- Detailed category rules must be verified before use.
-- ============================================================

CREATE TABLE IF NOT EXISTS program_requirements (
    requirement_id INTEGER PRIMARY KEY AUTOINCREMENT,
    program_id INTEGER NOT NULL,
    requirement_name TEXT NOT NULL,

    requirement_type TEXT NOT NULL,

    required_course_id INTEGER,
    minimum_credits REAL,

    source_id INTEGER NOT NULL,
    notes TEXT,

    FOREIGN KEY (program_id)
        REFERENCES programs(program_id)
        ON DELETE CASCADE,

    FOREIGN KEY (required_course_id)
        REFERENCES courses(course_id),

    FOREIGN KEY (source_id)
        REFERENCES academic_sources(source_id),

    CHECK (
        requirement_type IN (
            'REQUIRED_COURSE',
            'MINIMUM_CREDITS'
        )
    ),

    CHECK (
        (
            requirement_type = 'REQUIRED_COURSE'
            AND required_course_id IS NOT NULL
            AND minimum_credits IS NULL
        )
        OR
        (
            requirement_type = 'MINIMUM_CREDITS'
            AND required_course_id IS NULL
            AND minimum_credits > 0
        )
    )
);

-- ============================================================
-- 13. COURSES ELIGIBLE FOR CREDIT REQUIREMENTS
-- ============================================================

CREATE TABLE IF NOT EXISTS requirement_courses (
    requirement_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,

    PRIMARY KEY (requirement_id, course_id),

    FOREIGN KEY (requirement_id)
        REFERENCES program_requirements(requirement_id)
        ON DELETE CASCADE,

    FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
);

-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_courses_code
ON courses(course_code);

CREATE INDEX IF NOT EXISTS idx_courses_title
ON courses(course_title);

CREATE INDEX IF NOT EXISTS idx_completed_profile
ON completed_courses(profile_id);

CREATE INDEX IF NOT EXISTS idx_plans_profile
ON term_plans(profile_id);

CREATE INDEX IF NOT EXISTS idx_course_campuses_campus
ON course_campuses(campus_id);

CREATE INDEX IF NOT EXISTS idx_prerequisite_groups_course
ON prerequisite_groups(course_campus_id);

-- ============================================================
-- END OF SCHEMA
-- ============================================================
