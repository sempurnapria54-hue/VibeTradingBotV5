CREATE TABLE balance_containers (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    total_equity NUMERIC(50, 30) NOT NULL,
    unrealized_profit NUMERIC(50, 30) NOT NULL,
    external_updated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    external_created_at TIMESTAMPTZ NULL,
    external_modified_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_balance_containers_exchange UNIQUE (exchange_id),
    CONSTRAINT fk_balance_containers_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchanges (id)
);

INSERT INTO balance_containers (
    exchange_id,
    total_equity,
    unrealized_profit,
    created_at
)
SELECT DISTINCT b.exchange_id,
       0,
       0,
       NOW()
FROM balances b
ON CONFLICT (exchange_id) DO NOTHING;

ALTER TABLE balances
    ADD COLUMN balance_container_id BIGINT;

UPDATE balances b
SET balance_container_id = bc.id
FROM balance_containers bc
WHERE bc.exchange_id = b.exchange_id;

ALTER TABLE balances
    ALTER COLUMN balance_container_id SET NOT NULL;

ALTER TABLE balances
    ADD CONSTRAINT fk_balances_balance_container_id
        FOREIGN KEY (balance_container_id) REFERENCES balance_containers (id);
