# Двадцать пятая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала двадцать пятая пачка параллельных секций узла 1e (заход 256) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию после доковой двадцать четвёртой. Две волны:
первая — три субагента (живость условной заявки, навес JSONB, клетки
тестера) плюс правки ведущей; вторая — три субагента тестового кода в
пределах тех же модулей. Продовая дельта — пять модулей:
`services/common/model/domain`, `services/common/test-support`,
`services/market-data`, `services/strategies`, `services/trading-core`.
Запись захода — хроника шага, §«Сто восемьдесят восьмой заход (256) —
двадцать пятая пачка параллельных секций». Решения — Д2707-Д2724
`.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Живость условной заявки и защиты у родителя в `ERROR` — код не
    построен» — поле `AlgoOrder.externalLive`, предикаты `mayBeLive`,
    `isError`, выборка `DealTranche.mayBeLiveAlgoOrders`, миграция
    `V16__algo_order_external_live.sql`, писатель `RefreshAlgoOrderExecutor`
    (сохранение ненайденности терминальной заявки — только у `ERROR`, Д2710),
    читатели `KillSwitchExecutor` и `DealTerminalGate`, операнд
    `parentExternalLive` у резолва встроенной защиты, исход «ждать» у класса
    `PROBLEM`; `EXCHANGE_INVARIANT_VIOLATION` снят из `CloseReason` заявки,
    условной заявки и позиции; доки по факту — `AlgoOrder.md`, `Order.md`,
    `docs/models/mapping/AlgoOrder.md`, компоненты резолва,
    `docs/lifecycles/Position.md`; клетки — `trading-core-calc.md` (U10.4,
    U10.11, U10.15-U10.17), `domain-model-predicates.md` (U9.24,
    U10.18-U10.24), `trading-core-safety.md` (U19), `trading-core-fsm.md`
    (U2.17, U2.18);
  - «Код JSONB-навеса не отвечает правилу состава ключей строки» —
    `@JsonIgnore` на `InstrumentExternalRules#isLive`, пин терпимости к
    неизвестному свойству у хранимой копии семи конвертеров, группы `U13`,
    `U14`, клетки `U7.1`-`U7.3`, `U10.3`, `U11.7`;
  - «Хвосты двадцать третьей пачки 2026-09-30» — B8.12 (заголовок конверта
    без значения), `ALGO_ORDER_DECIDED` в B9.9, проба `WireFormPropertiesTest`;
  - «Клетки после двадцать четвёртой пачки 2026-09-30» — U9.11 OBV;
  - «Контракт операндов по типу правила условия дома не имеет» — javadoc
    `StrategyDefinitionValidator`, `StrategyConditionRule`, `@DisplayName`
    U28.24;
  - «Клетка B2.10 ящика market-data проверяет снятое ожидание о валютах»;
  - «Клетки и пробы после пятнадцатой пачки 2026-09-30» — G8 (U37.8, U37.9),
    `StrategyEventFormTest`, B4.9;
  - «Негейтящие находки второго круга кейсов `jsonb-overlay-roundtrip` не
    исполнены» — `Ф-3`, `Ф-5`.
- **Сужены:** «Клетки, получившие ожидание третьей доковой пачкой» (сняты
  `MarketModelsTest`, `MathAndIdentityTest`, `ExchangeAccountObservationTest`),
  «…четвёртой доковой пачкой» (снят пункт трёх классов FSM ядра), «…двадцатой
  пачкой» (снят пункт `jsonb-overlay-roundtrip.md`).
- **Заведена:** «Хвосты двадцать пятой пачки 2026-09-30» — javadoc класса
  отказа у `connector-okx`, `CREATED` в величине
  `trancheHasMayBeLiveStandaloneProtection`, имя метода в `AlgoOrder.md`.
- **Не взята:** «Грамматика условий в коде шире исполняемой» — одна тянет
  шесть модулей с продовой дельтой (Д2718).
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/common/model/domain,services/common/test-support,services/market-data,services/strategies,services/trading-core`:
  первый — красны `SchemaInputBoxTest` (B13.4, перечень миграций без `16`) и
  `OrderHarvestTest` (сохранение у терминальной заявки против
  `notFoundPastLocalTerminal`); после правок — 3589 тестов, дефектов 0; после
  второй волны — 3603 теста, дефектов 0.
