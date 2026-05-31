CREATE TABLE balance_containers
(
    id                   BIGSERIAL PRIMARY KEY,
    exchange_id          BIGINT          NOT NULL,
    total_equity         NUMERIC(50, 30) NOT NULL,
    unrealized_profit    NUMERIC(50, 30) NOT NULL,
    external_updated_at  TIMESTAMPTZ,
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_balance_containers_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges (id),
    CONSTRAINT uk_balance_containers_exchange UNIQUE (exchange_id)
);

COMMENT ON TABLE balance_containers IS 'Снимок баланса аккаунта на бирже.';
COMMENT ON COLUMN balance_containers.id IS 'Внутренний идентификатор контейнера баланса.';
COMMENT ON COLUMN balance_containers.exchange_id IS 'Идентификатор биржи владельца snapshot аккаунта.';
COMMENT ON COLUMN balance_containers.total_equity IS 'Суммарная equity аккаунта.';
COMMENT ON COLUMN balance_containers.unrealized_profit IS 'Суммарный нереализованный PnL аккаунта.';
COMMENT ON COLUMN balance_containers.external_updated_at IS 'Время последнего обновления контейнера на стороне биржи.';
COMMENT ON COLUMN balance_containers.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN balance_containers.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN balance_containers.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN balance_containers.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN balance_containers.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN balance_containers.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE balances
(
    id                   BIGSERIAL PRIMARY KEY,
    currency             VARCHAR(64)     NOT NULL,
    available            NUMERIC(50, 30) NOT NULL,
    frozen               NUMERIC(50, 30) NOT NULL,
    total                NUMERIC(50, 30) NOT NULL,
    external_updated_at  TIMESTAMPTZ,
    balance_container_id BIGINT          NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_balances_balance_container FOREIGN KEY (balance_container_id) REFERENCES balance_containers (id),
    CONSTRAINT uk_balances_balance_container_currency UNIQUE (balance_container_id, currency)
);

COMMENT ON TABLE balances IS 'Баланс аккаунта по одной валюте.';
COMMENT ON COLUMN balances.id IS 'Внутренний идентификатор баланса.';
COMMENT ON COLUMN balances.currency IS 'Валюта баланса.';
COMMENT ON COLUMN balances.available IS 'Доступный баланс.';
COMMENT ON COLUMN balances.frozen IS 'Заблокированный баланс.';
COMMENT ON COLUMN balances.total IS 'Общий баланс по валюте.';
COMMENT ON COLUMN balances.external_updated_at IS 'Время последнего обновления баланса на стороне биржи.';
COMMENT ON COLUMN balances.balance_container_id IS 'Идентификатор контейнера snapshot аккаунта.';
COMMENT ON COLUMN balances.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN balances.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN balances.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN balances.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN balances.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN balances.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE anomaly_reports
(
    id                   BIGSERIAL PRIMARY KEY,
    internal_id          VARCHAR(255) NOT NULL,
    exchange_id          BIGINT       NOT NULL,
    instrument_id        BIGINT,
    status               VARCHAR(64)  NOT NULL,
    severity             VARCHAR(64)  NOT NULL,
    code                 VARCHAR(255) NOT NULL,
    message              TEXT,
    internal_before      JSONB        NOT NULL,
    external_before      JSONB        NOT NULL,
    internal_after       JSONB,
    external_after       JSONB,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_anomaly_reports_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges (id),
    CONSTRAINT fk_anomaly_reports_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id)
);

COMMENT ON TABLE anomaly_reports IS 'Отчёт о глобальной аномалии системы.';
COMMENT ON COLUMN anomaly_reports.id IS 'Внутренний идентификатор отчёта об аномалии.';
COMMENT ON COLUMN anomaly_reports.internal_id IS 'Межсервисный идентификатор отчёта об аномалии.';
COMMENT ON COLUMN anomaly_reports.exchange_id IS 'Идентификатор биржи, в рамках которой зафиксирована аномалия.';
COMMENT ON COLUMN anomaly_reports.instrument_id IS 'Идентификатор инструмента, если аномалия относится к конкретному инструменту.';
COMMENT ON COLUMN anomaly_reports.status IS 'Текущий статус обработки аномалии.';
COMMENT ON COLUMN anomaly_reports.severity IS 'Уровень критичности аномалии.';
COMMENT ON COLUMN anomaly_reports.code IS 'Код аномалии для машинной обработки.';
COMMENT ON COLUMN anomaly_reports.message IS 'Человекочитаемое описание аномалии.';
COMMENT ON COLUMN anomaly_reports.internal_before IS 'Внутренний снимок состояния до обработки аномалии.';
COMMENT ON COLUMN anomaly_reports.external_before IS 'Внешний снимок состояния до обработки аномалии.';
COMMENT ON COLUMN anomaly_reports.internal_after IS 'Внутренний снимок состояния после обработки аномалии.';
COMMENT ON COLUMN anomaly_reports.external_after IS 'Внешний снимок состояния после обработки аномалии.';
COMMENT ON COLUMN anomaly_reports.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN anomaly_reports.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN anomaly_reports.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN anomaly_reports.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN anomaly_reports.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN anomaly_reports.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';
