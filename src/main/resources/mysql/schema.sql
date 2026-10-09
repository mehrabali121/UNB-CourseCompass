-- ============================================================
-- UNB CourseCompass - MySQL 8.4 Schema (Version 1)
-- Independent student project. Not affiliated with or endorsed by UNB.
-- Academic records must come from verified UNB sources.
-- This schema does not import data or modify the SQLite database.
-- ============================================================

CREATE TABLE IF NOT EXISTS academic_sources (
    source_id INT NOT NULL AUTO_INCREMENT,
    source_title VARCHAR(255) NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    verified_on DATE NOT NULL,
    notes TEXT,
    PRIMARY KEY (source_id),
    CONSTRAINT chk_source_title CHECK (CHAR_LENGTH(TRIM(source_title)) > 0),
    CONSTRAINT chk_source_url CHECK (CHAR_LENGTH(TRIM(source_url)) > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS campuses (
    campus_id INT NOT NULL,
    campus_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (campus_id),
    UNIQUE KEY uq_campus_name (campus_name),
    CONSTRAINT chk_campus_name CHECK (campus_name IN ('Fredericton', 'Saint John'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS programs (
    program_id INT NOT NULL AUTO_INCREMENT,
    program_name VARCHAR(255) NOT NULL,
    campus_id INT NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    audit_status VARCHAR(20) NOT NULL DEFAULT 'UNSUPPORTED',
    source_id INT NULL,
    notes TEXT,
    PRIMARY KEY (program_id),
    UNIQUE KEY uq_program_campus_year (program_name, campus_id, academic_year),
    CONSTRAINT fk_program_campus FOREIGN KEY (campus_id) REFERENCES campuses(campus_id),
    CONSTRAINT fk_program_source FOREIGN KEY (source_id) REFERENCES academic_sources(source_id),
    CONSTRAINT chk_program_audit CHECK (audit_status IN ('SUPPORTED', 'PARTIAL', 'UNSUPPORTED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS courses (
    course_id INT NOT NULL AUTO_INCREMENT,
    course_code VARCHAR(30) NOT NULL,
    course_title VARCHAR(255) NOT NULL,
    credit_hours DOUBLE NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    source_id INT NOT NULL,
    notes TEXT,
    PRIMARY KEY (course_id),
    UNIQUE KEY uq_course_code_year (course_code, academic_year),
    KEY idx_courses_code (course_code),
    KEY idx_courses_title (course_title),
    CONSTRAINT fk_course_source FOREIGN KEY (source_id) REFERENCES academic_sources(source_id),
    CONSTRAINT chk_course_code CHECK (CHAR_LENGTH(TRIM(course_code)) > 0),
    CONSTRAINT chk_course_title CHECK (CHAR_LENGTH(TRIM(course_title)) > 0),
    CONSTRAINT chk_course_credits CHECK (credit_hours >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course_campuses (
    course_campus_id INT NOT NULL AUTO_INCREMENT,
    course_id INT NOT NULL,
    campus_id INT NOT NULL,
    prerequisite_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    prerequisite_notes TEXT,
    offering_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    source_id INT NOT NULL,
    PRIMARY KEY (course_campus_id),
    UNIQUE KEY uq_course_campus (course_id, campus_id),
    KEY idx_course_campuses_campus (campus_id),
    CONSTRAINT fk_course_campus_course FOREIGN KEY (course_id) REFERENCES courses(course_id),
    CONSTRAINT fk_course_campus_campus FOREIGN KEY (campus_id) REFERENCES campuses(campus_id),
    CONSTRAINT fk_course_campus_source FOREIGN KEY (source_id) REFERENCES academic_sources(source_id),
    CONSTRAINT chk_prerequisite_status CHECK (prerequisite_status IN ('NONE', 'STRUCTURED', 'PARTIAL', 'UNKNOWN')),
    CONSTRAINT chk_offering_status CHECK (offering_status IN ('UNKNOWN', 'VERIFIED_OFFERED', 'VERIFIED_NOT_OFFERED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS profiles (
    profile_id INT NOT NULL AUTO_INCREMENT,
    profile_name VARCHAR(255) NOT NULL,
    campus_id INT NOT NULL,
    program_id INT NULL,
    academic_year VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id),
    CONSTRAINT fk_profile_campus FOREIGN KEY (campus_id) REFERENCES campuses(campus_id),
    CONSTRAINT fk_profile_program FOREIGN KEY (program_id) REFERENCES programs(program_id),
    CONSTRAINT chk_profile_name CHECK (CHAR_LENGTH(TRIM(profile_name)) > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS completed_courses (
    completion_id INT NOT NULL AUTO_INCREMENT,
    profile_id INT NOT NULL,
    course_id INT NOT NULL,
    completed_on VARCHAR(30) NULL,
    PRIMARY KEY (completion_id),
    UNIQUE KEY uq_completed_profile_course (profile_id, course_id),
    KEY idx_completed_profile (profile_id),
    CONSTRAINT fk_completion_profile FOREIGN KEY (profile_id) REFERENCES profiles(profile_id) ON DELETE CASCADE,
    CONSTRAINT fk_completion_course FOREIGN KEY (course_id) REFERENCES courses(course_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS prerequisite_groups (
    group_id INT NOT NULL AUTO_INCREMENT,
    course_campus_id INT NOT NULL,
    group_number INT NOT NULL,
    PRIMARY KEY (group_id),
    UNIQUE KEY uq_prereq_group (course_campus_id, group_number),
    KEY idx_prerequisite_groups_course (course_campus_id),
    CONSTRAINT fk_group_course_campus FOREIGN KEY (course_campus_id) REFERENCES course_campuses(course_campus_id) ON DELETE CASCADE,
    CONSTRAINT chk_group_number CHECK (group_number > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS prerequisite_options (
    option_id INT NOT NULL AUTO_INCREMENT,
    group_id INT NOT NULL,
    required_course_id INT NOT NULL,
    PRIMARY KEY (option_id),
    UNIQUE KEY uq_prereq_option (group_id, required_course_id),
    CONSTRAINT fk_option_group FOREIGN KEY (group_id) REFERENCES prerequisite_groups(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_option_course FOREIGN KEY (required_course_id) REFERENCES courses(course_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS term_plans (
    plan_id INT NOT NULL AUTO_INCREMENT,
    profile_id INT NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    term_name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id),
    KEY idx_plans_profile (profile_id),
    CONSTRAINT fk_plan_profile FOREIGN KEY (profile_id) REFERENCES profiles(profile_id) ON DELETE CASCADE,
    CONSTRAINT chk_plan_name CHECK (CHAR_LENGTH(TRIM(plan_name)) > 0),
    CONSTRAINT chk_term_name CHECK (term_name IN ('Fall', 'Winter', 'Summer', 'Other'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS planned_courses (
    planned_course_id INT NOT NULL AUTO_INCREMENT,
    plan_id INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (planned_course_id),
    UNIQUE KEY uq_planned_course (plan_id, course_id),
    CONSTRAINT fk_planned_course_plan FOREIGN KEY (plan_id) REFERENCES term_plans(plan_id) ON DELETE CASCADE,
    CONSTRAINT fk_planned_course_course FOREIGN KEY (course_id) REFERENCES courses(course_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS program_requirements (
    requirement_id INT NOT NULL AUTO_INCREMENT,
    program_id INT NOT NULL,
    requirement_name VARCHAR(255) NOT NULL,
    requirement_type VARCHAR(30) NOT NULL,
    required_course_id INT NULL,
    minimum_credits DOUBLE NULL,
    source_id INT NOT NULL,
    notes TEXT,
    PRIMARY KEY (requirement_id),
    CONSTRAINT fk_requirement_program FOREIGN KEY (program_id) REFERENCES programs(program_id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_course FOREIGN KEY (required_course_id) REFERENCES courses(course_id),
    CONSTRAINT fk_requirement_source FOREIGN KEY (source_id) REFERENCES academic_sources(source_id),
    CONSTRAINT chk_requirement_type CHECK (requirement_type IN ('REQUIRED_COURSE', 'MINIMUM_CREDITS')),
    CONSTRAINT chk_requirement_details CHECK (
        (requirement_type = 'REQUIRED_COURSE' AND required_course_id IS NOT NULL AND minimum_credits IS NULL)
        OR
        (requirement_type = 'MINIMUM_CREDITS' AND required_course_id IS NULL AND minimum_credits > 0)
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS requirement_courses (
    requirement_id INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (requirement_id, course_id),
    CONSTRAINT fk_requirement_course_map_requirement FOREIGN KEY (requirement_id) REFERENCES program_requirements(requirement_id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_course_map_course FOREIGN KEY (course_id) REFERENCES courses(course_id)
) ENGINE=InnoDB;
