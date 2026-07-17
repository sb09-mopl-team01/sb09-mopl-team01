CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_contents_title_trgm
    ON contents USING gin (lower(title) gin_trgm_ops);

CREATE INDEX idx_contents_description_trgm
    ON contents USING gin (lower(description) gin_trgm_ops);
