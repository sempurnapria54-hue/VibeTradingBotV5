CREATE TABLE deals
(
    id                   BIGSERIAL PRIMARY KEY,
    internal_id          VARCHAR(255)    NOT NULL,
    instrument_id        BIGINT          NOT NULL,
    status               VARCHAR(64)     NOT NULL,
    close_reason         VARCHAR(128),
    result_profit        NUMERIC(50, 30),
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_deals_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT uk_deals_internal_id UNIQUE (internal_id)
);

COMMENT ON TABLE deals IS 'Сделка и её жизненный цикл в FSM.';
COMMENT ON COLUMN deals.id IS 'Внутренний идентификатор сделки.';
COMMENT ON COLUMN deals.internal_id IS 'Межсервисный идентификатор сделки.';
COMMENT ON COLUMN deals.instrument_id IS 'Идентификатор инструмента.';
COMMENT ON COLUMN deals.status IS 'Текущий внутренний статус сделки.';
COMMENT ON COLUMN deals.close_reason IS 'Причина закрытия сделки.';
COMMENT ON COLUMN deals.result_profit IS 'Итоговый финансовый результат сделки.';
COMMENT ON COLUMN deals.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN deals.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN deals.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN deals.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN deals.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN deals.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE orders
(
    id                    BIGSERIAL PRIMARY KEY,
    deal_id               BIGINT          NOT NULL,
    internal_id           VARCHAR(255)    NOT NULL,
    external_id           VARCHAR(255),
    status                VARCHAR(64)     NOT NULL,
    close_reason          VARCHAR(128),
    type                  VARCHAR(64),
    side                  VARCHAR(64),
    external_status       VARCHAR(64),
    price                 NUMERIC(50, 30),
    size                  NUMERIC(50, 30) NOT NULL,
    accumulated_fill_size NUMERIC(50, 30),
    average_price         NUMERIC(50, 30),
    fee                   NUMERIC(50, 30),
    created_at            TIMESTAMPTZ     NOT NULL,
    created_by            VARCHAR(255),
    modified_at           TIMESTAMPTZ,
    modified_by           VARCHAR(255),
    external_created_at   TIMESTAMPTZ,
    external_modified_at  TIMESTAMPTZ,
    CONSTRAINT fk_orders_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT uk_orders_internal_id UNIQUE (internal_id)
);

COMMENT ON TABLE orders IS 'Обычный биржевой ордер, связанный со сделкой.';
COMMENT ON COLUMN orders.id IS 'Внутренний идентификатор ордера.';
COMMENT ON COLUMN orders.deal_id IS 'Идентификатор сделки.';
COMMENT ON COLUMN orders.internal_id IS 'Межсервисный идентификатор ордера.';
COMMENT ON COLUMN orders.external_id IS 'Идентификатор ордера на бирже.';
COMMENT ON COLUMN orders.status IS 'Текущий внутренний статус ордера.';
COMMENT ON COLUMN orders.close_reason IS 'Причина закрытия ордера.';
COMMENT ON COLUMN orders.type IS 'Тип ордера в бизнес-терминах.';
COMMENT ON COLUMN orders.side IS 'Сторона ордера.';
COMMENT ON COLUMN orders.external_status IS 'Состояние ордера на стороне биржи.';
COMMENT ON COLUMN orders.price IS 'Цена ордера.';
COMMENT ON COLUMN orders.size IS 'Объём ордера.';
COMMENT ON COLUMN orders.accumulated_fill_size IS 'Накопленный исполненный объём.';
COMMENT ON COLUMN orders.average_price IS 'Средняя цена исполнения.';
COMMENT ON COLUMN orders.fee IS 'Комиссия по ордеру.';
COMMENT ON COLUMN orders.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN orders.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN orders.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN orders.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN orders.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN orders.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE attached_algo_orders
(
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT          NOT NULL,
    internal_id          VARCHAR(255)    NOT NULL,
    external_attached_id VARCHAR(255),
    external_id          VARCHAR(255),
    status               VARCHAR(64)     NOT NULL,
    type                 VARCHAR(64)     NOT NULL,
    external_status      VARCHAR(64),
    external_type        VARCHAR(64),
    size                 NUMERIC(50, 30) NOT NULL,
    sl_trigger_price     NUMERIC(50, 30) NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_attached_algo_orders_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_attached_algo_orders_internal_id UNIQUE (internal_id)
);

COMMENT ON TABLE attached_algo_orders IS 'Прикреплённый защитный algo-ордер обычного ордера.';
COMMENT ON COLUMN attached_algo_orders.id IS 'Внутренний идентификатор прикреплённого algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.order_id IS 'Ссылка на обычный ордер.';
COMMENT ON COLUMN attached_algo_orders.internal_id IS 'Межсервисный идентификатор прикреплённого algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.external_attached_id IS 'Идентификатор прикреплённого algo-ордера на бирже до активации.';
COMMENT ON COLUMN attached_algo_orders.external_id IS 'Идентификатор algo-ордера на бирже после создания.';
COMMENT ON COLUMN attached_algo_orders.status IS 'Текущий внутренний статус прикреплённого algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.type IS 'Внутренний тип прикреплённого algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.external_status IS 'Состояние algo-ордера на стороне биржи.';
COMMENT ON COLUMN attached_algo_orders.external_type IS 'Биржевой тип algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.size IS 'Объём algo-ордера.';
COMMENT ON COLUMN attached_algo_orders.sl_trigger_price IS 'Триггерная цена stop-loss.';
COMMENT ON COLUMN attached_algo_orders.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN attached_algo_orders.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN attached_algo_orders.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN attached_algo_orders.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN attached_algo_orders.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN attached_algo_orders.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE algo_orders
(
    id                     BIGSERIAL PRIMARY KEY,
    deal_id                BIGINT          NOT NULL,
    internal_id            VARCHAR(255)    NOT NULL,
    status                 VARCHAR(64)     NOT NULL,
    close_reason           VARCHAR(128),
    condition_type         VARCHAR(128)    NOT NULL,
    size                   NUMERIC(50, 30),
    direction              VARCHAR(64),
    external_id            VARCHAR(255),
    external_type          VARCHAR(64),
    external_status        VARCHAR(64),
    external_direction     VARCHAR(64),
    external_position_side VARCHAR(64),
    condition              JSONB           NOT NULL,
    created_at             TIMESTAMPTZ     NOT NULL,
    created_by             VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    external_created_at    TIMESTAMPTZ,
    external_modified_at   TIMESTAMPTZ,
    CONSTRAINT fk_algo_orders_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT uk_algo_orders_internal_id UNIQUE (internal_id)
);

COMMENT ON TABLE algo_orders IS 'Самостоятельный algo-ордер сопровождения сделки.';
COMMENT ON COLUMN algo_orders.id IS 'Внутренний идентификатор algo-ордера.';
COMMENT ON COLUMN algo_orders.deal_id IS 'Идентификатор сделки.';
COMMENT ON COLUMN algo_orders.internal_id IS 'Межсервисный идентификатор algo-ордера.';
COMMENT ON COLUMN algo_orders.status IS 'Текущий внутренний статус algo-ордера.';
COMMENT ON COLUMN algo_orders.close_reason IS 'Причина закрытия algo-ордера.';
COMMENT ON COLUMN algo_orders.condition_type IS 'Доменный тип условия algo-ордера.';
COMMENT ON COLUMN algo_orders.size IS 'Объём algo-ордера.';
COMMENT ON COLUMN algo_orders.direction IS 'Сторона algo-ордера в домене.';
COMMENT ON COLUMN algo_orders.external_id IS 'Идентификатор algo-ордера на бирже.';
COMMENT ON COLUMN algo_orders.external_type IS 'Биржевой тип algo-ордера.';
COMMENT ON COLUMN algo_orders.external_status IS 'Состояние algo-ордера на стороне биржи.';
COMMENT ON COLUMN algo_orders.external_direction IS 'Сторона algo-ордера на стороне биржи.';
COMMENT ON COLUMN algo_orders.external_position_side IS 'Сторона позиции на стороне биржи.';
COMMENT ON COLUMN algo_orders.condition IS 'Условие algo-ордера в JSONB.';
COMMENT ON COLUMN algo_orders.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN algo_orders.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN algo_orders.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN algo_orders.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN algo_orders.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN algo_orders.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';

CREATE TABLE positions
(
    id                   BIGSERIAL PRIMARY KEY,
    deal_id              BIGINT          NOT NULL,
    internal_id          VARCHAR(255)    NOT NULL,
    external_id          VARCHAR(255)    NOT NULL,
    status               VARCHAR(64)     NOT NULL,
    side                 VARCHAR(64)     NOT NULL,
    external_side        VARCHAR(64)     NOT NULL,
    size                 NUMERIC(50, 30) NOT NULL,
    average_price        NUMERIC(50, 30),
    mark_price           NUMERIC(50, 30),
    liquidation_price    NUMERIC(50, 30),
    leverage             INTEGER         NOT NULL,
    margin_mode          VARCHAR(64)     NOT NULL,
    unrealized_profit    NUMERIC(50, 30),
    created_at           TIMESTAMPTZ     NOT NULL,
    created_by           VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    external_created_at  TIMESTAMPTZ,
    external_modified_at TIMESTAMPTZ,
    CONSTRAINT fk_positions_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT uk_positions_external_id UNIQUE (external_id)
);

COMMENT ON TABLE positions IS 'Открытая или закрытая позиция по инструменту.';
COMMENT ON COLUMN positions.id IS 'Внутренний идентификатор позиции.';
COMMENT ON COLUMN positions.deal_id IS 'Идентификатор сделки.';
COMMENT ON COLUMN positions.internal_id IS 'Межсервисный идентификатор позиции.';
COMMENT ON COLUMN positions.external_id IS 'Идентификатор позиции на бирже.';
COMMENT ON COLUMN positions.status IS 'Текущий внутренний статус позиции.';
COMMENT ON COLUMN positions.side IS 'Сторона позиции в доменной модели.';
COMMENT ON COLUMN positions.external_side IS 'Сторона позиции на стороне биржи.';
COMMENT ON COLUMN positions.size IS 'Размер позиции.';
COMMENT ON COLUMN positions.average_price IS 'Средняя цена входа в позицию.';
COMMENT ON COLUMN positions.mark_price IS 'Текущая рыночная цена позиции.';
COMMENT ON COLUMN positions.liquidation_price IS 'Оценочная цена ликвидации позиции.';
COMMENT ON COLUMN positions.leverage IS 'Плечо позиции.';
COMMENT ON COLUMN positions.margin_mode IS 'Биржевой режим маржи.';
COMMENT ON COLUMN positions.unrealized_profit IS 'Нереализованный PnL позиции.';
COMMENT ON COLUMN positions.created_at IS 'Дата и время создания записи в системе.';
COMMENT ON COLUMN positions.created_by IS 'Пользователь или сервис, создавший запись.';
COMMENT ON COLUMN positions.modified_at IS 'Дата и время последнего изменения записи в системе.';
COMMENT ON COLUMN positions.modified_by IS 'Пользователь или сервис, выполнивший последнее изменение.';
COMMENT ON COLUMN positions.external_created_at IS 'Дата и время создания записи на стороне биржи.';
COMMENT ON COLUMN positions.external_modified_at IS 'Дата и время последнего обновления записи на стороне биржи.';
