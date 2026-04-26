CREATE TABLE kairo_executions (
    execution_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    checkpoint TEXT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kairo_execution_events (
    event_id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload_json TEXT NOT NULL,
    event_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exec_events_exec_id FOREIGN KEY (execution_id) REFERENCES kairo_executions(execution_id)
);

CREATE INDEX idx_exec_events_exec_id ON kairo_execution_events(execution_id, created_at);
CREATE INDEX idx_executions_status ON kairo_executions(status);
