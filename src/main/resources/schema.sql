CREATE TABLE dialogue_history (
    id VARCHAR(36) PRIMARY KEY,
    created_time TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    topic TEXT NOT NULL,
    target_phrase TEXT NOT NULL,
    scenario TEXT NOT NULL,
    dialogues TEXT NOT NULL
);
CREATE INDEX dialogue_history_order ON dialogue_history (created_time, id);

CREATE TABLE dialogue_quota_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);
INSERT INTO dialogue_quota_lock (id) VALUES (1);

CREATE TABLE dialogue_generation_request (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(128) UNIQUE,
    scenario TEXT NOT NULL,
    reserved_at TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    daily_period_start TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    weekly_period_start TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    attempt_token VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('reserved', 'succeeded', 'failed')),
    history_id VARCHAR(36),
    response_snapshot TEXT
);
CREATE INDEX dialogue_generation_daily ON dialogue_generation_request (daily_period_start, state);
CREATE INDEX dialogue_generation_weekly ON dialogue_generation_request (weekly_period_start, state);
