CREATE TABLE exchanges
(
    id                   BIGSERIAL PRIMARY KEY,
    internal_id          VARCHAR(255)  NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    base_url             VARCHAR(1024) NOT NULL,
    status               VARCHAR(64)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT uk_exchange_internal_id UNIQUE (internal_id),
    CONSTRAINT uk_exchange_name UNIQUE (name)
);

COMMENT ON TABLE exchanges IS 'Биржа, с которой работает торговый бот.';
COMMENT ON COLUMN exchanges.id IS 'Внутренний идентификатор биржи.';
COMMENT ON COLUMN exchanges.internal_id IS 'Межсервисный идентификатор биржи.';
COMMENT ON COLUMN exchanges.name IS 'Уникальное имя биржи.';
COMMENT ON COLUMN exchanges.base_url IS 'Базовый URL для API биржи.';
COMMENT ON COLUMN exchanges.status IS 'Текущий статус подключения или использования биржи.';
COMMENT ON COLUMN exchanges.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN exchanges.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN exchanges.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN exchanges.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN exchanges.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN exchanges.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE instruments
(
    id                   BIGSERIAL PRIMARY KEY,
    internal_id          VARCHAR(255) NOT NULL,
    exchange_id          BIGINT       NOT NULL,
    external_id          VARCHAR(255) NOT NULL,
    external_type        VARCHAR(64)  NOT NULL,
    status               VARCHAR(64)  NOT NULL,
    margin_mode          VARCHAR(64)  NOT NULL,
    external_margin_mode VARCHAR(64),
    leverage             INTEGER      NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_instruments_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges (id),
    CONSTRAINT uk_instrument_internal_id UNIQUE (internal_id),
    CONSTRAINT uk_instrument_exchange_id_external_id UNIQUE (exchange_id, external_id)
);

COMMENT ON TABLE instruments IS 'Торговый инструмент биржи.';
COMMENT ON COLUMN instruments.id IS 'Внутренний идентификатор инструмента.';
COMMENT ON COLUMN instruments.internal_id IS 'Межсервисный идентификатор инструмента.';
COMMENT ON COLUMN instruments.exchange_id IS 'Внутренний идентификатор биржи.';
COMMENT ON COLUMN instruments.external_id IS 'Имя инструмента на бирже.';
COMMENT ON COLUMN instruments.external_type IS 'Тип инструмента на бирже.';
COMMENT ON COLUMN instruments.status IS 'Текущий статус инструмента.';
COMMENT ON COLUMN instruments.margin_mode IS 'Режим маржи в доменной модели.';
COMMENT ON COLUMN instruments.external_margin_mode IS 'Режим маржи на стороне биржи.';
COMMENT ON COLUMN instruments.leverage IS 'Плечо инструмента.';
COMMENT ON COLUMN instruments.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN instruments.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN instruments.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN instruments.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN instruments.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN instruments.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';
