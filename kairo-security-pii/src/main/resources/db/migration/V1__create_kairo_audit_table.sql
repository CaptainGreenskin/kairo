-- Append-only audit log for Kairo security events.
--
-- Each row captures a single GuardrailPolicy decision with enough context for an
-- after-the-fact compliance audit: who (agent), what (target), when (timestamp), why
-- (reason), how (action) and any policy-supplied attributes.
--
-- The table is intentionally append-only — there is no UPDATE or DELETE column. Hosting
-- apps that need retention rollover should partition by `created_at` and drop old
-- partitions, or stream into a downstream archive.
CREATE TABLE IF NOT EXISTS kairo_audit (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    agent_name      VARCHAR(255),
    target_name     VARCHAR(255),
    phase           VARCHAR(32),
    policy_name     VARCHAR(128),
    reason          VARCHAR(1024),
    attributes_json CLOB,
    created_at      TIMESTAMP   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_kairo_audit_created_at ON kairo_audit (created_at);
CREATE INDEX IF NOT EXISTS idx_kairo_audit_agent ON kairo_audit (agent_name);
CREATE INDEX IF NOT EXISTS idx_kairo_audit_event_type ON kairo_audit (event_type);
