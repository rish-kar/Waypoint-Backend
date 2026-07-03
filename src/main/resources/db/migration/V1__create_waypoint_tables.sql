CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    picture_url VARCHAR(2048),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_users_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    external_customer_id VARCHAR(255),
    external_subscription_id VARCHAR(255) UNIQUE,
    external_product_id VARCHAR(255),
    external_variant_id VARCHAR(255),
    plan VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    renews_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_user_status ON subscriptions (user_id, status);
CREATE INDEX idx_subscriptions_external_customer ON subscriptions (external_customer_id);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    event_hash VARCHAR(64) UNIQUE NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    external_object_id VARCHAR(255),
    processing_status VARCHAR(50) NOT NULL,
    payload_json TEXT NOT NULL,
    error_message TEXT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NULL
);

CREATE INDEX idx_webhook_events_event_name ON webhook_events (event_name);
CREATE INDEX idx_webhook_events_processing_status ON webhook_events (processing_status);
