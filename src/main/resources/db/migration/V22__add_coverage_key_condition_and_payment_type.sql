ALTER TABLE policy_terms_coverages
    ADD COLUMN key_condition TEXT;

ALTER TABLE coverage_items
    ADD COLUMN payment_type VARCHAR(30);
