CREATE TABLE waypoint_refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_waypoint_refresh_sessions_user_id ON waypoint_refresh_sessions (user_id);
CREATE INDEX idx_waypoint_refresh_sessions_expires_at ON waypoint_refresh_sessions (expires_at);
CREATE INDEX idx_waypoint_refresh_sessions_revoked_at ON waypoint_refresh_sessions (revoked_at);
