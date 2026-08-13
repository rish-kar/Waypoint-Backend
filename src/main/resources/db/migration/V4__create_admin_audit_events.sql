CREATE TABLE admin_audit_events (
    id UUID PRIMARY KEY,
    admin_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_admin_audit_events_created_at ON admin_audit_events (created_at);
CREATE INDEX idx_admin_audit_events_admin_id ON admin_audit_events (admin_id);
CREATE INDEX idx_admin_audit_events_resource ON admin_audit_events (resource_type, resource_id);
