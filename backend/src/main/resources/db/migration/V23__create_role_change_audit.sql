CREATE TABLE role_change_audits (
    id BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT NOT NULL REFERENCES users(id),
    changed_by_user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(30) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role_change_audit_role CHECK (role IN ('SCORER', 'ORGANIZER')),
    CONSTRAINT chk_role_change_audit_operation CHECK (operation IN ('GRANT', 'REVOKE'))
);

CREATE INDEX idx_role_change_audits_target_created
    ON role_change_audits (target_user_id, created_at DESC);
