CREATE TABLE strategies
(
    id                   BIGSERIAL PRIMARY KEY,
    internal_id          VARCHAR(255)    NOT NULL,
    instrument_id        BIGINT          NOT NULL,
    name                 VARCHAR(255)    NOT NULL,
    version              INTEGER         NOT NULL,
    status               VARCHAR(64)     NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_strategies_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT uk_strategies_internal_id UNIQUE (internal_id),
    CONSTRAINT uk_strategies_instrument_id_version UNIQUE (instrument_id, version)
);

CREATE UNIQUE INDEX uk_strategies_active_instrument
    ON strategies (instrument_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE strategies IS 'Контейнер стратегии для конкретного инструмента.';
COMMENT ON COLUMN strategies.id IS 'Технический идентификатор стратегии.';
COMMENT ON COLUMN strategies.internal_id IS 'Безопасный внешний / межсервисный идентификатор стратегии.';
COMMENT ON COLUMN strategies.instrument_id IS 'Идентификатор инструмента стратегии.';
COMMENT ON COLUMN strategies.name IS 'Человекочитаемое имя стратегии.';
COMMENT ON COLUMN strategies.version IS 'Append-only версия стратегии.';
COMMENT ON COLUMN strategies.status IS 'Статус контейнера стратегии.';

CREATE TABLE strategy_details
(
    id                       BIGSERIAL PRIMARY KEY,
    strategy_id              BIGINT          NOT NULL,
    market_phase_type        VARCHAR(64)     NOT NULL,
    phase_entry_policy       VARCHAR(64)     NOT NULL,
    risk_per_trade_percent   NUMERIC(50, 30),
    max_leverage             INTEGER,
    target_risk_reward_ratio NUMERIC(50, 30),
    created_at               TIMESTAMPTZ     NOT NULL,
    created_by               VARCHAR(255),
    modified_at              TIMESTAMPTZ,
    modified_by              VARCHAR(255),
    external_created_at      TIMESTAMPTZ,
    external_modified_at     TIMESTAMPTZ,
    CONSTRAINT fk_strategy_details_strategy FOREIGN KEY (strategy_id) REFERENCES strategies (id),
    CONSTRAINT uk_strategy_details_strategy_id_market_phase UNIQUE (strategy_id, market_phase_type)
);

COMMENT ON TABLE strategy_details IS 'Настройки стратегии для конкретной фазы рынка.';
COMMENT ON COLUMN strategy_details.id IS 'Технический идентификатор detail стратегии.';
COMMENT ON COLUMN strategy_details.strategy_id IS 'Идентификатор стратегии-владельца.';
COMMENT ON COLUMN strategy_details.market_phase_type IS 'Фаза рынка, для которой действует detail.';
COMMENT ON COLUMN strategy_details.phase_entry_policy IS 'Политика входа для выбранной фазы рынка.';
COMMENT ON COLUMN strategy_details.risk_per_trade_percent IS 'Риск на сделку в процентах от капитала.';
COMMENT ON COLUMN strategy_details.max_leverage IS 'Максимально допустимое плечо.';
COMMENT ON COLUMN strategy_details.target_risk_reward_ratio IS 'Целевой ориентир reward/risk.';

CREATE TABLE strategy_steps
(
    id                   BIGSERIAL PRIMARY KEY,
    strategy_details_id  BIGINT          NOT NULL,
    deal_status          VARCHAR(64)     NOT NULL,
    step_type            VARCHAR(64)     NOT NULL,
    step_index           INTEGER         NOT NULL,
    condition            JSONB,
    actions              JSONB,
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_strategy_steps_strategy_details FOREIGN KEY (strategy_details_id) REFERENCES strategy_details (id),
    CONSTRAINT uk_strategy_steps_details_status_step_index UNIQUE (strategy_details_id, deal_status, step_index)
);

COMMENT ON TABLE strategy_steps IS 'Плоское persistence-представление шага стратегии.';
COMMENT ON COLUMN strategy_steps.id IS 'Технический идентификатор шага стратегии.';
COMMENT ON COLUMN strategy_steps.strategy_details_id IS 'Идентификатор detail стратегии-владельца.';
COMMENT ON COLUMN strategy_steps.deal_status IS 'Статус сделки, к которому относится шаг. Хранится строкой.';
COMMENT ON COLUMN strategy_steps.step_type IS 'Тип шага стратегии.';
COMMENT ON COLUMN strategy_steps.step_index IS 'Технический индекс порядка шага внутри одного Deal.Status.';
COMMENT ON COLUMN strategy_steps.condition IS 'Условие применимости шага в JSONB.';
COMMENT ON COLUMN strategy_steps.actions IS 'Список действий шага в JSONB.';
