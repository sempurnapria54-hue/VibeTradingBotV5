CREATE TABLE candle_group (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    coverage_start_ts BIGINT NOT NULL,
    backfill_cursor_ts BIGINT NULL,
    last_tail_sync_ts BIGINT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_success_at TIMESTAMPTZ NULL,
    last_error_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(32) NULL,
    last_error_message VARCHAR(1024) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    CONSTRAINT uk_candle_group_instrument_timeframe UNIQUE (instrument_id, timeframe),
    CONSTRAINT fk_candle_group_instrument_id FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE INDEX idx_candle_group_status ON candle_group (status);
CREATE INDEX idx_candle_group_lease_until ON candle_group (lease_until);
CREATE INDEX idx_candle_group_instrument_timeframe ON candle_group (instrument_id, timeframe);
