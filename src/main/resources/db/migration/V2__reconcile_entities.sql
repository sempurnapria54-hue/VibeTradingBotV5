CREATE TABLE position (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    side VARCHAR(20) NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT fk_position_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT fk_position_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_position_exchange_instrument
    ON position (exchange_id, instrument_id);

CREATE TABLE "order" (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    client_order_id VARCHAR(128) NOT NULL,
    exchange_order_id VARCHAR(128) NULL,
    status VARCHAR(50) NOT NULL,
    type VARCHAR(50) NULL,
    side VARCHAR(20) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_order_exchange_instr_client_order UNIQUE (exchange_id, instrument_id, client_order_id),
    CONSTRAINT fk_order_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT fk_order_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_order_exchange_instrument
    ON "order" (exchange_id, instrument_id);

CREATE TABLE algo_order (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    client_algo_order_id VARCHAR(128) NOT NULL,
    exchange_algo_order_id VARCHAR(128) NULL,
    status VARCHAR(50) NOT NULL,
    algo_type VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_algo_order_exchange_instr_client_algo_order UNIQUE (exchange_id, instrument_id, client_algo_order_id),
    CONSTRAINT fk_algo_order_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT fk_algo_order_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_algo_order_exchange_instrument
    ON algo_order (exchange_id, instrument_id);

CREATE TABLE anomaly_report (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    instrument_id BIGINT NULL,
    severity VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    CONSTRAINT fk_anomaly_report_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT fk_anomaly_report_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_anomaly_report_exchange_instrument
    ON anomaly_report (exchange_id, instrument_id);

CREATE INDEX idx_anomaly_report_created_at
    ON anomaly_report (created_at);
