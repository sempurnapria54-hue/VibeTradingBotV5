ALTER TABLE orders
    ADD COLUMN strategy_action_id BIGINT;

COMMENT ON COLUMN orders.strategy_action_id IS 'Идентификатор действия стратегии, по которому был создан ордер. Нужен FSM для восстановления после рестарта и понимания, какие actions уже материализованы.';

-- FK на StrategyAction не добавляется: actions хранятся полиморфно в strategy_steps.actions JSONB.
ALTER TABLE orders
    ADD CONSTRAINT uk_orders_deal_id_strategy_action_id UNIQUE (deal_id, strategy_action_id);

ALTER TABLE algo_orders
    ADD COLUMN strategy_action_id BIGINT;

COMMENT ON COLUMN algo_orders.strategy_action_id IS 'Идентификатор действия стратегии, по которому был создан algo-ордер. Нужен FSM для восстановления после рестарта и понимания, какие actions уже материализованы.';

-- FK на StrategyAction не добавляется: actions хранятся полиморфно в strategy_steps.actions JSONB.
ALTER TABLE algo_orders
    ADD CONSTRAINT uk_algo_orders_deal_id_strategy_action_id UNIQUE (deal_id, strategy_action_id);
