ALTER TABLE instrument
    ADD COLUMN inst_id VARCHAR(100),
    ADD COLUMN inst_type VARCHAR(50);

UPDATE instrument
SET inst_id = name,
    inst_type = 'SWAP'
WHERE inst_id IS NULL
   OR inst_type IS NULL;

ALTER TABLE instrument
    ALTER COLUMN inst_id SET NOT NULL,
    ALTER COLUMN inst_type SET NOT NULL;

ALTER TABLE instrument
    ADD CONSTRAINT uk_instrument_exchange_inst_id UNIQUE (exchange_id, inst_id);
