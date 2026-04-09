CREATE TABLE balances (
    id BIGSERIAL PRIMARY KEY,
    exchange_id BIGINT NOT NULL,
    currency VARCHAR(32) NOT NULL,
    available NUMERIC(50, 30) NOT NULL,
    frozen NUMERIC(50, 30) NOT NULL,
    total NUMERIC(50, 30) NOT NULL,
    external_updated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NULL,
    modified_at TIMESTAMPTZ NULL,
    modified_by VARCHAR(255) NULL,
    external_created_at TIMESTAMPTZ NULL,
    external_modified_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_balances_exchange_currency UNIQUE (exchange_id, currency),
    CONSTRAINT fk_balances_exchange_id FOREIGN KEY (exchange_id) REFERENCES exchanges (id)
);
