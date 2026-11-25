ALTER TABLE consents
    ALTER COLUMN terms_of_service DROP NOT NULL,
    ALTER COLUMN privacy_collection_use DROP NOT NULL,
    ALTER COLUMN overseas_transfer DROP NOT NULL,
    ALTER COLUMN age_14_plus DROP NOT NULL,
    ALTER COLUMN policy_analysis DROP NOT NULL,
    ALTER COLUMN third_party_policy DROP NOT NULL,
    ALTER COLUMN health_information DROP NOT NULL;
