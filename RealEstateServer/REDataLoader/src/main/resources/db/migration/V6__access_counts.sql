CREATE TABLE IF NOT EXISTS access_counts (
    target_type TEXT   NOT NULL,
    target_id   TEXT   NOT NULL,
    hits        BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (target_type, target_id)
);

CREATE INDEX IF NOT EXISTS idx_access_counts_type_hits
    ON access_counts (target_type, hits DESC);
