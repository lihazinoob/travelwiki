-- V3: Destination knowledge schema (Tier 1 curated structured data)
--
-- destinations        — one row per supported destination; code is the machine key
-- destination_activities — ordered list of activities for the AI prompt context
-- destination_aliases — resolves free-text user input to a canonical destination row
--
-- Enum-like columns (destination_type, category) use VARCHAR per project rule.

CREATE TABLE destinations (
    id                      BIGSERIAL    PRIMARY KEY,
    code                    VARCHAR(80)  NOT NULL UNIQUE,
    name                    VARCHAR(120) NOT NULL,
    country                 VARCHAR(80),
    region                  VARCHAR(120),
    description             TEXT,
    destination_type        VARCHAR(80),
    recommended_min_days    INT,
    recommended_max_days    INT,
    best_for                TEXT,
    local_tips              TEXT,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE destination_activities (
    id                          BIGSERIAL       PRIMARY KEY,
    destination_id              BIGINT          NOT NULL REFERENCES destinations(id),
    title                       VARCHAR(150)    NOT NULL,
    description                 TEXT,
    category                    VARCHAR(80),
    estimated_cost_min          NUMERIC(12, 2),
    estimated_cost_max          NUMERIC(12, 2),
    recommended_duration_minutes INT,
    priority                    INT             NOT NULL DEFAULT 0
);

CREATE TABLE destination_aliases (
    id              BIGSERIAL    PRIMARY KEY,
    destination_id  BIGINT       NOT NULL REFERENCES destinations(id),
    alias           VARCHAR(150) NOT NULL UNIQUE
);

CREATE INDEX idx_destinations_code                  ON destinations(code);
CREATE INDEX idx_destination_activities_dest_id     ON destination_activities(destination_id);
CREATE INDEX idx_destination_aliases_alias          ON destination_aliases(alias);
