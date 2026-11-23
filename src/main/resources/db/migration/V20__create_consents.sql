CREATE TABLE consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    terms_of_service BOOLEAN NOT NULL,
    privacy_collection_use BOOLEAN NOT NULL,
    overseas_transfer BOOLEAN NOT NULL,
    age_14_plus BOOLEAN NOT NULL,
    policy_analysis BOOLEAN NOT NULL,
    third_party_policy BOOLEAN NOT NULL,
    health_information BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_consents_user UNIQUE (user_id),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
