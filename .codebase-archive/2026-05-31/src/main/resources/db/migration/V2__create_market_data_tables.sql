CREATE TABLE candle_groups
(
    id                        BIGSERIAL PRIMARY KEY,
    instrument_id             BIGINT       NOT NULL,
    timeframe                 VARCHAR(64)  NOT NULL,
    external_timeframe        VARCHAR(64)  NOT NULL,
    status                    VARCHAR(64)  NOT NULL,
    coverage_start_utc_millis BIGINT,
    coverage_end_utc_millis   BIGINT,
    created_at                TIMESTAMPTZ  NOT NULL,
    created_by                VARCHAR(255),
    modified_at               TIMESTAMPTZ,
    modified_by               VARCHAR(255),
    external_created_at       TIMESTAMPTZ,
    external_modified_at      TIMESTAMPTZ,
    CONSTRAINT fk_candle_groups_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT uk_candle_group_instrument_timeframe UNIQUE (instrument_id, timeframe)
);

COMMENT ON TABLE candle_groups IS 'Группа свечей одного инструмента и таймфрейма.';
COMMENT ON COLUMN candle_groups.id IS 'Внутренний идентификатор группы свечей.';
COMMENT ON COLUMN candle_groups.instrument_id IS 'Инструмент-владелец группы свечей.';
COMMENT ON COLUMN candle_groups.timeframe IS 'Таймфрейм группы.';
COMMENT ON COLUMN candle_groups.external_timeframe IS 'Таймфрейм группы в формате биржи.';
COMMENT ON COLUMN candle_groups.status IS 'Текущий статус жизненного цикла загрузки свечей.';
COMMENT ON COLUMN candle_groups.coverage_start_utc_millis IS 'Время открытия первой свечи в UTC миллисекундах.';
COMMENT ON COLUMN candle_groups.coverage_end_utc_millis IS 'Время закрытия последней свечи в UTC миллисекундах.';
COMMENT ON COLUMN candle_groups.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN candle_groups.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN candle_groups.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN candle_groups.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN candle_groups.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN candle_groups.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE candles
(
    id                   BIGSERIAL PRIMARY KEY,
    candle_group_id      BIGINT          NOT NULL,
    open_timestamp       BIGINT          NOT NULL,
    open                 NUMERIC(50, 30) NOT NULL,
    high                 NUMERIC(50, 30) NOT NULL,
    low                  NUMERIC(50, 30) NOT NULL,
    close                NUMERIC(50, 30) NOT NULL,
    volume               NUMERIC(50, 30),
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_candles_candle_group FOREIGN KEY (candle_group_id) REFERENCES candle_groups (id),
    CONSTRAINT uk_candle_group_id_open_timestamp UNIQUE (candle_group_id, open_timestamp)
);

COMMENT ON TABLE candles IS 'Одна свеча рынка в UTC.';
COMMENT ON COLUMN candles.id IS 'Внутренний идентификатор свечи.';
COMMENT ON COLUMN candles.candle_group_id IS 'Идентификатор группы свечей.';
COMMENT ON COLUMN candles.open_timestamp IS 'Время открытия свечи в UTC миллисекундах.';
COMMENT ON COLUMN candles.open IS 'Цена открытия свечи.';
COMMENT ON COLUMN candles.high IS 'Максимальная цена за интервал свечи.';
COMMENT ON COLUMN candles.low IS 'Минимальная цена за интервал свечи.';
COMMENT ON COLUMN candles.close IS 'Цена закрытия свечи.';
COMMENT ON COLUMN candles.volume IS 'Объём торгов за интервал свечи.';
COMMENT ON COLUMN candles.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN candles.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN candles.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN candles.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN candles.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN candles.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';
