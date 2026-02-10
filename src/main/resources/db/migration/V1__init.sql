CREATE TABLE exchange (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_exchange_name UNIQUE (name)
);

CREATE TABLE instrument (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    position_mode VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_instrument_exchange_name UNIQUE (exchange_id, name),
    CONSTRAINT fk_instrument_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchange (id)
);

CREATE TABLE candle (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    timestamp BIGINT NOT NULL,
    open NUMERIC(50, 30) NOT NULL,
    high NUMERIC(50, 30) NOT NULL,
    low NUMERIC(50, 30) NOT NULL,
    close NUMERIC(50, 30) NOT NULL,
    volume NUMERIC(50, 30) NULL,
    status VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_candle_instr_tf_ts UNIQUE (instrument_id, timeframe, timestamp),
    CONSTRAINT fk_candle_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_candle_instrument_timeframe_timestamp
    ON candle (instrument_id, timeframe, timestamp);
