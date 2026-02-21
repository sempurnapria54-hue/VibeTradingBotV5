ALTER TABLE sync_execution_environment_report
    ADD COLUMN IF NOT EXISTS internal_id VARCHAR(36);

UPDATE sync_execution_environment_report
SET internal_id = lower(
        substr(md5(random()::text || clock_timestamp()::text), 1, 8) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 12)
    )
WHERE internal_id IS NULL;

ALTER TABLE sync_execution_environment_report
    ALTER COLUMN internal_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_sync_execution_environment_report_internal_id'
    ) THEN
        ALTER TABLE sync_execution_environment_report
            ADD CONSTRAINT uk_sync_execution_environment_report_internal_id UNIQUE (internal_id);
    END IF;
END $$;
