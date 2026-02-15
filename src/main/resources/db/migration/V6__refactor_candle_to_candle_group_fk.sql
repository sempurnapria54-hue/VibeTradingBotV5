ALTER TABLE candle
    ADD COLUMN candle_group_id BIGINT;

INSERT INTO candle_group (
    instrument_id,
    timeframe,
    status,
    coverage_start_ts,
    backfill_cursor_ts,
    last_tail_sync_ts,
    attempt_count,
    last_success_at,
    last_error_at,
    last_error_code,
    last_error_message,
    lease_owner,
    lease_until,
    created_at,
    created_by,
    modified_at,
    modified_by
)
SELECT
    c.instrument_id,
    c.timeframe,
    'SYNC',
    MIN(c.timestamp),
    NULL,
    MAX(c.timestamp),
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NOW(),
    'flyway',
    NOW(),
    'flyway'
FROM candle c
GROUP BY c.instrument_id, c.timeframe
ON CONFLICT (instrument_id, timeframe) DO NOTHING;

UPDATE candle c
SET candle_group_id = cg.id
FROM candle_group cg
WHERE cg.instrument_id = c.instrument_id
  AND cg.timeframe = c.timeframe;

ALTER TABLE candle
    ALTER COLUMN candle_group_id SET NOT NULL;

ALTER TABLE candle
    DROP CONSTRAINT IF EXISTS uk_candle_instr_tf_ts;

ALTER TABLE candle
    DROP CONSTRAINT IF EXISTS fk_candle_instrument_id;

DROP INDEX IF EXISTS idx_candle_instrument_timeframe_timestamp;

ALTER TABLE candle
    DROP COLUMN instrument_id,
    DROP COLUMN timeframe,
    DROP COLUMN status;

ALTER TABLE candle
    ADD CONSTRAINT fk_candle_candle_group_id
        FOREIGN KEY (candle_group_id) REFERENCES candle_group (id) ON DELETE CASCADE;

ALTER TABLE candle
    ADD CONSTRAINT uk_candle_group_ts UNIQUE (candle_group_id, timestamp);

CREATE INDEX idx_candle_group_timestamp ON candle (candle_group_id, timestamp);
