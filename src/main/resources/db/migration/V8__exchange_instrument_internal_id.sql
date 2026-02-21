ALTER TABLE exchange
    ADD COLUMN internal_id VARCHAR(36);

UPDATE exchange
SET internal_id = lower(
    substr(md5('exchange:' || id::text), 1, 8) || '-' ||
    substr(md5('exchange:' || id::text), 9, 4) || '-' ||
    substr(md5('exchange:' || id::text), 13, 4) || '-' ||
    substr(md5('exchange:' || id::text), 17, 4) || '-' ||
    substr(md5('exchange:' || id::text), 21, 12)
)
WHERE internal_id IS NULL;

ALTER TABLE exchange
    ALTER COLUMN internal_id SET NOT NULL;

ALTER TABLE exchange
    ADD CONSTRAINT uk_exchange_internal_id UNIQUE (internal_id);

ALTER TABLE instrument
    ADD COLUMN internal_id VARCHAR(36);

UPDATE instrument
SET internal_id = lower(
    substr(md5('instrument:' || id::text), 1, 8) || '-' ||
    substr(md5('instrument:' || id::text), 9, 4) || '-' ||
    substr(md5('instrument:' || id::text), 13, 4) || '-' ||
    substr(md5('instrument:' || id::text), 17, 4) || '-' ||
    substr(md5('instrument:' || id::text), 21, 12)
)
WHERE internal_id IS NULL;

ALTER TABLE instrument
    ALTER COLUMN internal_id SET NOT NULL;

ALTER TABLE instrument
    ADD CONSTRAINT uk_instrument_internal_id UNIQUE (internal_id);
