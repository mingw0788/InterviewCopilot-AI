CREATE TABLE users (
    id BINARY(16) NOT NULL,
    login_identifier VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_login_identifier UNIQUE (login_identifier),
    CONSTRAINT chk_users_login_identifier CHECK (
        CHAR_LENGTH(login_identifier) BETWEEN 3 AND 50
        AND login_identifier = TRIM(login_identifier)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview_sessions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    target_position VARCHAR(100) NOT NULL,
    skills JSON NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    question_count SMALLINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_question_number SMALLINT UNSIGNED NULL,
    interview_overall_score DECIMAL(5, 2) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_interview_sessions PRIMARY KEY (id),
    CONSTRAINT fk_interview_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_interview_sessions_question_count CHECK (question_count BETWEEN 3 AND 10),
    CONSTRAINT chk_interview_sessions_current_question CHECK (
        current_question_number IS NULL
        OR current_question_number BETWEEN 1 AND question_count
    ),
    CONSTRAINT chk_interview_sessions_score CHECK (
        interview_overall_score IS NULL OR interview_overall_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_interview_sessions_version CHECK (version >= 0),
    INDEX idx_interview_sessions_user_created (user_id, created_at),
    INDEX idx_interview_sessions_user_status_created (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview_questions (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    question_number SMALLINT UNSIGNED NOT NULL,
    question_text TEXT NOT NULL,
    topic VARCHAR(200) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    expected_points JSON NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    llm_provider VARCHAR(100) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    token_usage JSON NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_interview_questions PRIMARY KEY (id),
    CONSTRAINT uk_interview_questions_session_number UNIQUE (session_id, question_number),
    CONSTRAINT fk_interview_questions_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id),
    CONSTRAINT chk_interview_questions_number CHECK (question_number BETWEEN 1 AND 10),
    CONSTRAINT chk_interview_questions_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview_answers (
    id BINARY(16) NOT NULL,
    question_id BINARY(16) NOT NULL,
    answer_content TEXT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_interview_answers PRIMARY KEY (id),
    CONSTRAINT uk_interview_answers_question UNIQUE (question_id),
    CONSTRAINT fk_interview_answers_question FOREIGN KEY (question_id) REFERENCES interview_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE answer_evaluations (
    id BINARY(16) NOT NULL,
    answer_id BINARY(16) NOT NULL,
    accuracy DECIMAL(5, 2) NOT NULL,
    completeness DECIMAL(5, 2) NOT NULL,
    depth DECIMAL(5, 2) NOT NULL,
    clarity DECIMAL(5, 2) NOT NULL,
    answer_overall_score DECIMAL(5, 2) NOT NULL,
    strengths JSON NOT NULL,
    missing_points JSON NOT NULL,
    feedback TEXT NOT NULL,
    llm_provider VARCHAR(100) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    evaluation_version VARCHAR(40) NOT NULL,
    token_usage JSON NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_answer_evaluations PRIMARY KEY (id),
    CONSTRAINT uk_answer_evaluations_answer UNIQUE (answer_id),
    CONSTRAINT fk_answer_evaluations_answer FOREIGN KEY (answer_id) REFERENCES interview_answers (id),
    CONSTRAINT chk_answer_evaluations_scores CHECK (
        accuracy BETWEEN 0 AND 100
        AND completeness BETWEEN 0 AND 100
        AND depth BETWEEN 0 AND 100
        AND clarity BETWEEN 0 AND 100
        AND answer_overall_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_answer_evaluations_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview_reports (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    interview_overall_score DECIMAL(5, 2) NOT NULL,
    question_count SMALLINT UNSIGNED NOT NULL,
    completed_question_count SMALLINT UNSIGNED NOT NULL,
    average_accuracy DECIMAL(5, 2) NOT NULL,
    average_completeness DECIMAL(5, 2) NOT NULL,
    average_depth DECIMAL(5, 2) NOT NULL,
    average_clarity DECIMAL(5, 2) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    strength_summary TEXT NULL,
    weakness_summary TEXT NULL,
    improvement_suggestions JSON NULL,
    overall_comment TEXT NULL,
    llm_provider VARCHAR(100) NULL,
    model_name VARCHAR(200) NULL,
    prompt_version VARCHAR(40) NULL,
    report_version VARCHAR(40) NULL,
    token_usage JSON NULL,
    latency_ms BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    CONSTRAINT pk_interview_reports PRIMARY KEY (id),
    CONSTRAINT uk_interview_reports_session UNIQUE (session_id),
    CONSTRAINT fk_interview_reports_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id),
    CONSTRAINT chk_interview_reports_scores CHECK (
        interview_overall_score BETWEEN 0 AND 100
        AND average_accuracy BETWEEN 0 AND 100
        AND average_completeness BETWEEN 0 AND 100
        AND average_depth BETWEEN 0 AND 100
        AND average_clarity BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_interview_reports_counts CHECK (
        question_count BETWEEN 3 AND 10
        AND completed_question_count <= question_count
    ),
    CONSTRAINT chk_interview_reports_duration CHECK (duration_seconds >= 0),
    CONSTRAINT chk_interview_reports_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT chk_interview_reports_version CHECK (version >= 0),
    INDEX idx_interview_reports_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idempotency_records (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    operation VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    resource_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_records_user_operation_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_records_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_idempotency_records_key CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 8 AND 128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
