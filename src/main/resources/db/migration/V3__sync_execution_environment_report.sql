DROP TABLE IF EXISTS anomaly_report;

CREATE TABLE sync_execution_environment_report (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NULL,
    trigger VARCHAR(16) NOT NULL,
    has_anomalies BOOLEAN NOT NULL DEFAULT FALSE,
    max_severity VARCHAR(16) NOT NULL DEFAULT 'NONE',
    database_before_json JSONB NOT NULL,
    exchange_before_json JSONB NOT NULL,
    database_after_json JSONB NULL,
    exchange_after_json JSONB NULL,
    CONSTRAINT fk_sync_execution_environment_report_exchange_id
        FOREIGN KEY (exchange_id) REFERENCES exchange (id)
);

CREATE INDEX idx_sync_execution_environment_report_exchange_started_at
    ON sync_execution_environment_report (exchange_id, started_at DESC);

CREATE INDEX idx_sync_execution_environment_report_finished_at
    ON sync_execution_environment_report (finished_at);

CREATE INDEX idx_sync_execution_environment_report_has_anomalies_finished_at
    ON sync_execution_environment_report (has_anomalies, finished_at);

CREATE TABLE sync_execution_environment_report_anomaly (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    inst_id VARCHAR(64) NULL,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    details_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sync_execution_environment_report_anomaly_report_id
        FOREIGN KEY (report_id) REFERENCES sync_execution_environment_report (id) ON DELETE CASCADE
);

CREATE INDEX idx_sync_execution_environment_report_anomaly_report_id
    ON sync_execution_environment_report_anomaly (report_id);

CREATE INDEX idx_sync_execution_environment_report_anomaly_inst_id
    ON sync_execution_environment_report_anomaly (inst_id);

CREATE INDEX idx_sync_execution_environment_report_anomaly_severity
    ON sync_execution_environment_report_anomaly (severity);
