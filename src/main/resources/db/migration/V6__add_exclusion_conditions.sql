CREATE TABLE IF NOT EXISTS exclusion_conditions (
    id UUID PRIMARY KEY,
    coverage_item_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    source_text TEXT,
    severity VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_exclusion_conditions_coverage_item
        FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id)
);

CREATE INDEX IF NOT EXISTS idx_exclusion_conditions_coverage_sort
    ON exclusion_conditions(coverage_item_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_exclusion_conditions_severity
    ON exclusion_conditions(severity);
