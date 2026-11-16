CREATE TABLE dialogue_history (
    id VARCHAR(36) PRIMARY KEY,
    created_time TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    topic TEXT NOT NULL,
    target_phrase TEXT NOT NULL,
    scenario TEXT NOT NULL,
    dialogues TEXT NOT NULL
);
CREATE INDEX dialogue_history_order ON dialogue_history (created_time, id);
