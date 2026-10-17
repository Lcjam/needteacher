CREATE TABLE tutor_profiles (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL UNIQUE,
    visibility VARCHAR(20) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    university VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    introduction VARCHAR(2000),
    amount_won INT NOT NULL,
    tuition_unit VARCHAR(20) NOT NULL,
    session_minutes INT NOT NULL,
    sessions_per_month INT,
    declaration_type VARCHAR(30) NOT NULL,
    registration_number VARCHAR(50),
    university_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tutor_profile_subjects (
    profile_id UUID NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
    subject_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (profile_id, subject_code)
);

CREATE TABLE tutor_profile_grades (
    profile_id UUID NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
    grade_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (profile_id, grade_code)
);

CREATE TABLE tutor_profile_dongs (
    profile_id UUID NOT NULL REFERENCES tutor_profiles(id) ON DELETE CASCADE,
    dong_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (profile_id, dong_code)
);

CREATE INDEX idx_tutor_profiles_search ON tutor_profiles(visibility, updated_at DESC, id DESC);
CREATE INDEX idx_tutor_profile_subjects_code ON tutor_profile_subjects(subject_code, profile_id);
CREATE INDEX idx_tutor_profile_grades_code ON tutor_profile_grades(grade_code, profile_id);
CREATE INDEX idx_tutor_profile_dongs_code ON tutor_profile_dongs(dong_code, profile_id);
