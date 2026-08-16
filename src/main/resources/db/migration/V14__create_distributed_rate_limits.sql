CREATE TABLE request_rate_limit_windows (
    rate_key VARCHAR(255) PRIMARY KEY,
    window_started_at TIMESTAMP NOT NULL,
    request_count INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_request_rate_limit_windows_expires_at
    ON request_rate_limit_windows (expires_at);
