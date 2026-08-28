# Механика финализации P&L (шаг 7)

## На какой вопрос отвечает этот файл

Как механически добываются P&L-факты, кто считает и пишет число
`resultProfit` (штатно и аварийно), где durable-живёт посчитанное число,
откуда берётся ставка комиссии для сайзинга, как реагирует сверка
bills↔net — и почему именно так.

## Контекст

`GAPS_CLOSE_1` выбрал **источник** числа (net из positions-history +
разбивка из bills, `docs/decisions/result-profit-source.md`), но отложил
механику; `DOCS_CHECK_2` вскрыл 11 гейтящих пробелов (N1-N12). Это
решение закрывает механику шестью пунктами ниже. Действующие
формулировки живут **в домах** (указатели при каждом пункте,
`.claude/rules/policy-home.md`); здесь — итог решения и отвергнутые
альтернативы.

## Решения

### 1. Добыча P&L-фактов — нога цикла + `REFRESH_BILLS_COMMAND`, замена `REFRESH_FILLS` (N6, N12)

Положение закрытия добывается **второй ногой** evidence-cycle
`REFRESH_POSITION_COMMAND` (live → positions-history) и приземляется
полями на `Position`; разбивка — новой командой `REFRESH_BILLS_COMMAND`
(строки `DealCashFlow`), эмитимой звеном `REFRESH_DEAL_CONTEXT_ACTION`.
`REFRESH_FILLS` снимается (функция покрыта `REFRESH_ORDER_COMMAND`).
Оба факта durable ⇒ границу прохода FSM пересекают штатно;
`FinalizeDealExitExecutor` остаётся off-exchange и читает уже
приземлённый факт.

- **Отвергнуто:** отдельная `REFRESH_POSITIONS_HISTORY`-команда (та же
  сущность `Position`, по одной refresh-команде на сущность);
  integration read вне command-layer (теряет командный
  retry/идемпотентность); fetch внутри финализатора (ломает «refresh
  populates → finalize consolidates»); «команда внутри команды» и
  снапшот в памяти действия (канон `command-lifecycle` не допускает,
  операнд окна линковки был бы ненаблюдаем).
- **Дома:** `docs/components/RefreshPositionExecutor.md`,
  `docs/components/RefreshBillsExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/rules/command-lifecycle.md`,
  `docs/decisions/refresh-evidence-cycle-ownership.md`.

### 2. Носитель staged-числа — поля `Deal`, пишет `FINALIZE_EXIT` (N7)

`FINALIZE_DEAL_EXIT_COMMAND` пишет `resultProfit`/`resultProfitCurrency`
прямо на `Deal` в одной транзакции с durable-продвижением своего звена;
`MARK_DEAL_CLOSED_COMMAND` читает готовое число, ассертит непустоту и
ставит `CLOSED`. Рестарт-safe: `COMPLETED` ⟺ `resultProfit` persisted.

- **Отвергнуто (ревизия framing `GAPS_CLOSE_1`):** «`MARK_CLOSED` пишет
  число» — durable-слот между двумя командами не был назначен, ломалось
  об идемпотентность/рестарт.
- **Дом:** `docs/models/domain/aggregate/Deal.md` §«Итоговый PnL
  (resultProfit)»; терминальная сторона —
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/lifecycles/Deal.md` §«Терминальный контракт финализации».

### 3. Аварийный терминал: владелец + провенанс-контракт (N8)

Вводится `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`
(`MarkDealEmergencyClosedExecutor`, звено `FINALIZE_DEAL_ERROR_ACTION`),
терминальное ребро `ERROR → EMERGENCY_CLOSED`, симметрично
`MARK_DEAL_CLOSED_COMMAND`. Число — best-effort по той же формуле, что
на чистой тропе; genuinely недоступно → `resultProfit = null` с
семантикой «неисчислимо» (**не ноль** — маркер несёт nullability),
сделка терминализуется всё равно. Null — отложенный долг: направление —
добор числа до истечения окна positions-history (материализация —
форвард фазы 2).

- **Отвергнуто:** зануление недоступного числа (прямая ложь о числе);
  «пометки достаточно» (пропуск null'ов outcome-коррелирован — омиссия
  воспроизводит смещение); worst-case импутация; метрика счёта дыр как
  замена добора.
- **Дома:** `docs/lifecycles/Deal.md` §«Терминальный контракт
  финализации», `docs/components/MarkDealEmergencyClosedExecutor.md`,
  `docs/models/domain/aggregate/Deal.md` §«Признаки отбора для отчёта».

### 4. Ставка комиссии для сайзинга — seam на `InstrumentExternalRules`, дом на `TradeFeeRate` (N9)

Поверхность чтения не менялась: `SizeCalculator`/`RiskValidator` читают
аксессор `InstrumentExternalRules.takerFeeRate()`; гидрация — на
хранилищном слое (`InstrumentExternalRulesDataService`). Дом значения —
`TradeFeeRate` (строка на комиссионную группу, история значением,
свежесть счётчиком `refreshCount`); на навесе инструмента — только ключ
группы, резолв по паре сырых (`instType`, `groupId`). Синк — один
писатель (`InstrumentExternalRulesSyncJob`), чтение раз-на-тик.
Несвежесть (возраст > порога, стартово 24 ч) → холд
**инструмент-scope** (`Instrument.Status.ENTRY_BLOCKED`, мягкая
реакция без kill-switch, живые сделки доживают и сопровождаются
штатно; снятие вручную). Нерезолвящаяся ставка → реджект
`FEE_RATE_UNAVAILABLE`; знак ставки снимается при маппинге; прогноз по
taker.

- **Отвергнуто:** ставка полями на навесе инструмента (копия на каждом
  инструменте — заготовка под расхождение при смене тира); отдельная
  `TradeFeeSyncJob` (гонка двух писателей в один навес); чтение на входе
  в сделку (точка отказа на горячем пути ради устранения дрейфа ≈ 0);
  fallback-ставка из конфига (тихое оптимистичное смещение); холд
  биржа-scope (радиус ущерба — инструменты одной группы на исправной
  бирже); enforcement через `TRADE_BLOCKED` (воскресил бы kill-switch
  за радиусом ущерба).
- **Дома:** `docs/models/domain/other/TradeFeeRate.md`,
  `docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»,
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/components/InstrumentExternalRulesSyncJob.md`,
  `docs/models/mapping/TradeFeeRate.md` §«Знак ставки»,
  `docs/rules/error-handling-policy.md` §«Радиус ущерба задаёт scope».

### 5. Реакция сверки bills ↔ net (N10)

Число всегда авторитетно = net (в settle-ccy; заголовочное число = net +
cross-ccy-слагаемое). Сверка — четыре пары по категориям, допуск
`epsilon` один на сделку (двухчастный тест, общий пол); расхождение
сверх допуска → `reconciliationStatus = MISMATCHED` + `AnomalyReport`
`PNL_RECONCILIATION_MISMATCH`, финализация не блокируется; при
**калиброванном** допуске сверх того — биржевая **ступень 1**
(`Exchange.HOLD` + ручной разбор; решение держателя `GAPS_CLOSE_19`,
дом — `docs/rules/pnl-reconciliation.md` §«Реакция на расхождение»).
Инвариант
«комиссии только в settle-ccy» — принят (дом —
`docs/rules/trading-constraints.md`); чужая валюта персистится,
пересчитывается по курсу из свечи на момент операции
(`DealCashFlow.appliedRate`), неполученный курс — долг с догоном.

- **Отвергнуто:** подмена числа bills-суммой; блок терминала по
  расхождению; якорь относительного члена на |net| (глотает дыру при
  большом net, ложно звенит при net ≈ 0) или на издержечные потоки
  (схлопывается при их выпадении); пер-парные якоря допуска (три-четыре
  калибруемые величины); тикер на момент обработки как источник курса
  (невоспроизводим, неробастен); перенос cross-ccy-пересчёта на
  финализацию (guard зависел бы от факта закрытия).
- **Дом:** `docs/rules/pnl-reconciliation.md`; операционные детали —
  `docs/components/FinalizeDealExitExecutor.md` §«Расчёт прибыли
  (шаг 7) и сверка», `docs/models/mapping/DealCashFlow.md`,
  `docs/components/RefreshBillsExecutor.md` §«Носитель курса».

### 5a. Асимметрия троп отказа добычи

Отказ добычи на **штатной** тропе — ретрай по бюджету
`REFRESH_DEAL_CONTEXT_ACTION`, исчерпание → `ERROR` + холд инструмента.
На **аварийном** терминале отказ канала добычи после того же бюджета
даёт durable-исход «недоступно» (`FAILED` строки) → `resultProfit =
null`, терминал ставится, радиусной реакции нет; **дефект содержимого
ответа** (`ControlledExchangeException`) — как на штатной тропе: бросок
⇒ биржевая ступень 2 (`Exchange.TRADE_BLOCKED` + flatten), параллельно
с ошибочным терминалом. Исполнитель приравнивания —
`ServiceCommandExecutor`; предикат — **дизъюнкция** «`Deal.status =
ERROR` или живого риска нет» (A3 `DOCS_CHECK_20`, дом —
`docs/components/ServiceCommandExecutor.md` §«Ветка „радиус
локализован“»).

- **Отвергнуто:** разнести тропы отдельными типами действия
  (дискриминатор уже существует durable-полем `Deal.status`); «жёсткий
  отказ ≡ пусто» с нулевыми попытками (исход без durable-носителя,
  проглатывание нарушения контракта); признать холд на аварийной тропе
  штатным (довод радиуса).
- **Дом:** `docs/rules/pnl-reconciliation.md` §«Асимметрия троп отказа
  добычи»; ветка исполнителя —
  `docs/components/ServiceCommandExecutor.md` §«Ветка „радиус
  локализован“» (B9 `DOCS_CHECK_21`: прежний адрес §«Ветка
  `Deal.status = ERROR`» — стейл-имя, не существующее ни в одном
  файле; свип C4 `GAPS_CLOSE_20` его не нашёл, потому что имя разбито
  переносом строки, а фильтр свипа шёл по словам-дискриминаторам).

### 6. Инвариант агрегации positions-history — рантайм-верификация (N11)

Инвариант: **один эпизод** ↔ один `posId` ↔ одна финализированная запись
positions-history с `realizedPnl`, кумулятивным по всем
partial-закрытиям **этого эпизода**; читается финализированной. До
рантайм-верификации — **предположение**, гейтит корректность числа ⇒
верификация до `CODE` (предусловие п. 1).

**Формулировка «одна сделка ↔ один `posId`» снята** (T3 `DOCS_CHECK_18`,
решение держателя): сделка многоэпизодна
(`docs/decisions/multi-episode-deal.md`), эпизодов у неё может быть
несколько, и число сделки — Σ по ним. Верифицируемое утверждение
сместилось на **эпизод**: инвариант про кумулятивность внутри одного
`posId` остаётся ровно тем же и проверяется тем же кейсом; добавляется
вторая половина — **окно сделки может содержать несколько записей, и
каждая адресуется своим `posId`**.

- **Дома:** `docs/models/mapping/PositionCloseResult.md`,
  `docs/integrations/okx/contracts/position.md` §«Инвариант агрегации»,
  `.claude/tests/source-api/okx/plan.md` §AG1.

## Носители (стадия 2)

`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`,
`docs/models/mapping/PositionCloseResult.md` (снапшот приземляется на
`Position`), `docs/models/domain/other/DealCashFlow.md` +
`docs/models/mapping/DealCashFlow.md`,
`docs/models/domain/other/TradeFeeRate.md` +
`docs/models/mapping/TradeFeeRate.md` (реш.4, N9 — прогнозная комиссия
входит в плановый риск и в омиссионный член допуска; носитель был
пропущен вместе со строкой сборки, B1 `DOCS_CHECK_19`).

## Следствия

Не-схемная дельта `CODE` шага 7 (новые команды/executor'ы, правки
компонентов) — рабочий носитель `.claude/work/backlog.md` §Шаг 7;
развёрнутая историческая редакция — архив (§История). Биржевые реакции
дельты читаются по лестнице `docs/rules/exchange-hold.md`
(`Exchange.TRADE_BLOCKED` — ступень 2 с kill-switch; `Exchange.HOLD` —
ручной гейт входов).

### Schema-дельта шага 7 — сборка-указатель

**Это сборка, а не место истины.** Место истины схемы каждой сущности —
её §Персистентность (`docs/rules/persistence-representation.md` §«Место
истины схемы — §Персистентность модели»); сборка обязана **совпадать по
составу** с перечнями моделей, и детектор расхождения исполняется по ней
(там же, «симметричное требование к обоим носителям»).

**Каждая строка несёт перечень колонок, а не счёт и не описание**
(B1 `DOCS_CHECK_19`, распространено на все строки B5 `DOCS_CHECK_20`).
Прежняя редакция писала «24 колонки» — счёт не
называет, **какая** колонка расходится, то есть детектор на нём
неисполним в единственном направлении, ради которого заведён. Тем же
дефектом были описания-заместители перечня («восемь колонок положения
закрытия», «колонки валют инструмента», «`CREATE TABLE` + частичный
ключ») — по ним диф с §Персистентность не исполняется. Это была
самоприменимость **дважды**: правило «совпадать по **составу**»
сформулировано здесь и здесь же не применялось к собственным строкам.
Цена — длина строки таблицы.

**Индексы и ключи называются именами** (B8 `DOCS_CHECK_20`). Строка,
объявляющая объект без имени («+поисковый индекс …»), заставляет
писателя миграции имя придумать, тогда как для двух других объектов
того же шага имена названы правилом
(`docs/rules/persistence-representation.md`). Безымянных объектов в
сборке не остаётся.

**Клейм проверяется по объектам, а не по строкам** (B2 `DOCS_CHECK_21`).
Прежний проход прочитал требование как «у каждой **строки** сборки нет
безымянных объектов» и пропустил три частичных ключа, объявленных внутри
строки счётом («+два частичных ключа») и предикатом («частичный ключ
§Инварианты»). Счёт — не перечень, и правило «перечень, а не счёт»
действует **внутри** строки тоже: раскрывается каждый вводимый объект,
а не их количество.

**Строка несёт и снимаемое, а не только вводимое** (B1 `DOCS_CHECK_21`).
Детектор расхождения ищет **разницу** составов, поэтому объект, который
шаг сносит (`−колонка`, `−ключ`, `−индекс`, `DROP TABLE`), обязан быть в
строке так же, как вводимый: иначе снос виден только месту истины, а
сборка молча его теряет — зеркальный случай той же тропы, ради которой
сборка и заведена.

Пустой сборки быть не может:
без неё детектор исполнять не на чем — ровно так колонка
`orders.liquidation_distance_ratio` и не доехала до миграции (B1
`DOCS_CHECK_18`).

| Сущность / таблица | Что меняется | Место истины |
|---|---|---|
| `deals` | `ALTER`: `planned_risk_amount`, `incurred_risk_amount`, `current_risk_amount`, `protection_relieved_risk_amount`, `planned_risk_currency`, **`planned_risk_equity_base`**, `bills_window_begin`, `bills_window_end`, **`bills_fetched_through`**, `close_outcome`, `reconciliation_status`, `breakdown_incomplete`, `risk_benchmark_availability`; `+ix_deal_status_close_outcome`, `−ix_deal_status` | `docs/models/domain/aggregate/Deal.md` §Персистентность |
| `orders` | `ALTER`: `planned_entry_price`, `planned_size_contracts`, `planned_risk_amount`, `planned_risk_currency`, `planned_contract_value`, `planned_stop_price` (инвариант «шесть или ни одного») **+ `liquidation_distance_ratio`** (седьмое число, в инвариант не входит) **+ `position_id`** (FK → `positions`, nullable — эпизод, к которому относится нога; N5 `DOCS_CHECK_20`, решение держателя); `+fk_order_position`, `+ix_order_position` | `docs/models/domain/core/Order.md` §Персистентность |
| `attached_algo_orders` | `ALTER`: **`+trigger_price_type`** (`varchar(64)`, nullable — енум `TriggerPriceType` строкой; ценовая база триггера attached-защиты, C1 `DOCS_CHECK_20`) | `docs/models/domain/core/Order.md` §«Персистентность `AttachedAlgoOrder`» |
| `positions` | `ALTER`: восемь колонок положения закрытия — `external_realized_profit`, `external_result_currency`, `external_close_average_price`, `external_close_type`, `external_funding_cost`, `external_realized_profit_gross`, `external_fee`, `external_liquidation_penalty`; **`−uk_position_deal`**, `+uk_position_deal_live` (частичный `unique (deal_id) where status = 'ACTIVE'`), `+uk_position_deal_external` (частичный `unique (deal_id, external_id) where external_id is not null` — B5 `DOCS_CHECK_21`), `+ix_position_deal` — многоэпизодная сделка | `docs/models/domain/core/Position.md` §Персистентность |
| `deal_cash_flows` | **`CREATE TABLE`**: `id`, `deal_id`, `exchange_id`, `category`, `amount`, `external_fee`, `ccy`, `applied_rate`, `rate_status`, `applied_rate_candle_instrument`, `applied_rate_candle_timeframe`, `applied_rate_candle_open_time`, `external_instrument_id`, `external_bill_id`, `external_type`, `external_sub_type`, `external_order_id` + шесть audit-колонок; `+uk_deal_cash_flow_exchange_bill`, `+fk_deal_cash_flow_deal`, `+fk_deal_cash_flow_exchange`, `+ix_deal_cash_flow_deal`, `+ix_deal_cash_flow_unlinked` (частичный, `where deal_id is null`) | `docs/models/domain/other/DealCashFlow.md` §Персистентность |
| `trade_fee_rates` | **`CREATE TABLE`**: `id`, `exchange_id`, `external_instrument_type`, `external_fee_group_id`, `instrument_type`, `external_taker_fee_rate`, `external_maker_fee_rate`, **`external_fee_level`**, `refresh_count` + шесть audit-колонок; `+fk_trade_fee_rate_exchange`, `+ix_trade_fee_rate_group`. `UNIQUE` по ключу группы **не заводится** — история означает несколько строк на ключ | `docs/models/domain/other/TradeFeeRate.md` §Персистентность |
| `deal_action_states` → `deal_strategy_action_states` | `RENAME`; `+target_entity_type`, `+target_entity_id`, `+`шесть audit-колонок; **`−target`** (jsonb — расплющена в две колонки, двух представлений одного факта не оставляем); `status` `varchar(32)` → **`varchar(64)`**; `−uk_deal_action_state_deal_action`, `+uk_deal_strategy_action_state_target` (частичный, `where … and target_entity_id is not null`), `+uk_deal_strategy_action_state_action` (частичный, `where … and target_entity_id is null`); **`+fk_deal_strategy_action_state_deal`** (симметрия с таблицей-близнецом), `fk_deal_action_state_strategy_action` → **`fk_deal_strategy_action_state_strategy_action`** (`RENAME CONSTRAINT`); `+ix_deal_strategy_action_state_deal` | `docs/models/domain/other/DealActionState.md` §Персистентность |
| `deal_system_action_states` | **`CREATE TABLE`**: `id`, `deal_id`, `system_action_type`, `status`, `attempt_count`, `max_attempts`, `next_retry_at`, `last_error` + шесть audit-колонок (типы и nullability — место истины); `+fk_deal_system_action_state_deal`, `+uk_deal_system_action_state_action` (частичный, `where status in (живые)`), `+ix_deal_system_action_state_deal`. Target-колонок нет — цель системного действия всегда сама сделка | там же |
| `deal_finalization_states` | **`DROP TABLE`** (роль перенесена) | там же, §«Правило переноса» |
| `anomaly_reports` | `ALTER`: `+kind` (`not null` сразу), `scope` `varchar(16)` → `varchar(64)`; **`+ix_anomaly_report_unfinished_state`** — поисковый индекс незавершённых `STATE`-отчётов по (`exchange_id`, `code`, `scope`, `instrument_id`) `where kind = 'STATE' and status in ('CREATED', 'IN_PROGRESS', 'KILL_SWITCH_EXECUTED')`; `fee_group_key` **не заводится** | `docs/models/domain/other/AnomalyReport.md` §Персистентность |
| `instruments` | `ALTER`: `external_settlement_currency`, `external_base_currency`, `external_quote_currency` — все `varchar(64)`, nullable | `docs/models/domain/core/Instrument.md` §Персистентность |
| `strategy_details` | `ALTER`: `+position_reopen_allowed` (многоэпизодная сделка — параметр стратегии); **`risk_per_trade_percent` → `risk_per_action_percent`** (`RENAME`), **`+cumulative_risk_per_deal_multiplier`** (`numeric(36,18)`, nullable) и **`+strategy_simultaneous_risk_per_deal_percent`** (`numeric(36,18)`, nullable — максимум одновременного риска стратегии, C9 `DOCS_CHECK_21`, расширение держателя) — сделочные лимиты риска (`docs/decisions/per-trade-risk-policy.md` §«Три лимита внутри уровня „риск на сделку“»). **Глобальный** максимум одновременного риска колонки не получает — он остаётся конфигом | `docs/models/domain/aggregate/Strategy.md` §Персистентность |

Бэкфилла не требует ни одна строка сборки: до конца фазы 1 таблицы пусты
(`.claude/rules/pre-launch-schema-changes.md`). Обязательные колонки
вводятся `ALTER`'ом напрямую, без `default` «на время миграции».

## Предусловия `CODE` шага 7

Реестр сквозной: гейты и «рассмотрено — не гейтит» в одном месте.
Развёрнутые доводы — в архивной редакции (§История) и в названных
носителях.

1. **Инвариант агрегации positions-history** (реш.6) — **гейтит**;
   рантайм-прогон `.claude/tests/source-api/okx/plan.md` **§AG1.5**
   (семантика агрегации partial-close). Состав
   расширен многоэпизодностью: сверх кумулятивности внутри `posId`
   проверяется поведение окна с **несколькими** записями (`AG1.9`).
2. **Непустой список исключений сверки для OKX** — **гейтит** (кейс
   `AG6.2`; хвост `integrator`, содержание перечня;
   `docs/models/mapping/DealCashFlow.md` §«Область сверки»).
3. Закрыт (`RISK-Q4`): дом операндов планового риска — только `orders`;
   входной алго-тропы не существует.
4. Закрыт (`ANOM-Q5`): идемпотентность `STATE`-отчёта — незавершённым
   статусом (`docs/models/domain/other/AnomalyReport.md`).
5. **Носитель курса cross-ccy** — **гейтит** (кейс `MG7.5`; хэнд-офф `integrator`:
   эндпоинт свечей, разрешение, правило деградации;
   `docs/components/RefreshBillsExecutor.md` §«Носитель курса»).
6. **Оси адресации записи positions-history без `posId`** — **гейтит**
   (кейс `AG1.6`); содержание — только грунт источника (хвост
   `integrator`).
7. **Знак и горизонт накопления `fundingFee`** (`AG1.7`) — **гейтит**
   (`docs/models/mapping/PositionCloseResult.md` §«Знак `fundingFee`»,
   `docs/decisions/per-trade-risk-policy.md` §H25). Знак
   `fee`/`liqPenalty` — тем же кейсом, **не гейтит** (правится по факту
   прогона; `PositionCloseResult.md` §«Знак `fee` и `liqPenalty`»).
8. **Калибратор допуска и условие выхода из разведочного режима** —
   **закрыт, не гейтит** (решение держателя, `GAPS_CLOSE_19`). Прежняя
   формулировка («не закрыт: названный калибратор допуск на омиссию
   данных API произвести не может») снята; ярлык гейта, которого пункт
   не нёс вовсе, проставлен: **не гейтит**. В `CODE` выходим с
   некалиброванным допуском; калибровка — интеграторским прогоном по
   реальному API **до боевой торговли**, механизм и осуществимость —
   `docs/rules/pnl-reconciliation.md` §«Разведочный режим допуска».
   Пока допуск не калиброван, `MISMATCHED` лестницу не триггерит, то
   есть некалиброванная величина ничего не блокирует.
9. **Посылка «биржевой net не содержит издержку вне settle-ccy»**
   (`AG3.4`) — **гейтит**: при ложности cross-ccy-слагаемое считается
   дважды.
10. **Посылка «след частичного принудительного эпизода — строки
    `LIQ_PENALTY`»** (`AG1.8`) — **гейтит**; закрывается и исходом
    `OBSERVED_ABSENT` — с явно записанным допущением
    (`docs/models/mapping/DealCashFlow.md` §«Резолв категории»,
    `docs/models/domain/aggregate/Deal.md` §«Принудительный эпизод»).
11. **Значения `direction` записи positions-history** — хвост
    `integrator`, **не гейтит** (меняется таблица маппинга, не
    конструкция; `PositionCloseResult.md` §«Резолв направления»).
12. **Носитель конфига «список исключений сверки по бирже» — решён:**
    `@ConfigurationProperties` per-exchange, непустой стартовый набор в
    конфиге по умолчанию (`docs/models/mapping/DealCashFlow.md`
    §«Область сверки задаётся списком исключений по бирже»). Гейтом
    остаётся только содержание (п. 2).
13. **Клеймы полноты шага непроверяемы до реализации системного слоя**
    — названное ограничение, не гейт: «чисто» на `DOCS_CHECK` значит
    «доки не противоречат друг другу», не «спецификация реализуема»;
    пять разрывов уходят дельтой `CODE`.
14. **Форма пустого значения несобытийных полей записи
    positions-history** — **гейтит** (`AG1.7`): конвенция «пусто = 0
    для несобытийного поля» применяется **до** проверки обязательности
    (`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`
    §Конвертация), иначе контракт границы
    (`ExternalInvariantViolationException` ⇒ биржевая ступень 2
    `Exchange.TRADE_BLOCKED` + flatten, ручное снятие) реджектил бы
    каждую нормально закрывшуюся сделку.

15. **Посылка «переоткрытая позиция получает новый `posId` внутри окна
    линковки»** — **гейтит** (кейс `AG1.9`, вторая половина). Она несёт
    **дискриминатор смены эпизода** и ключ идемпотентности
    `uk_position_deal_external`; при ложности ключ ловит **легитимный**
    второй эпизод как дубль, то есть даёт отказ вставки на **штатной**
    тропе (`docs/integrations/okx/contracts/position.md` §«Что офдок
    этим НЕ утверждает», `docs/lifecycles/Position.md` §«Смена эпизода
    (многоэпизодная сделка)»). Отдельным пунктом, а не расширением п. 1 (B6
    `DOCS_CHECK_21`): п. 1 назван инвариантом **агрегации**, предмет
    ключа в него не помещается, а слияние двух посылок в один гейт —
    ровно тот дефект, который реестр уже ловил на `AG1.5`. Кейс
    посылку покрывает; недоставало **регистрации** — клейм «реестр
    сквозной» без неё был ложен.

Негейтящие: `AG3.5` (гранулярность bills, fee-эхо) и **`AG3.6`**
(фактический состав полей bill-записи, B10 `DOCS_CHECK_20`) —
рассмотрены, **не гейтят**; прогон запланирован до `CODE`.

**`AG1.5` из этого перечня убран** (N2 `DOCS_CHECK_20`). Он числился
здесь с предметом «нижняя граница окна», а этот предмет у кейса
**снят**: развилка закрыта решением (единственный писатель
`SubmitOrderExecutor`, H9 `DOCS_CHECK_16`), и `AG1.5` с тех пор несёт
**семантику агрегации partial-close** — то есть ровно предмет
предусловия п. 1, которое **гейтит**. Строка утверждала о кейсе и
другой предмет, и другой статус, а список гейтов в
`.claude/work/backlog.md`, пересобранный «по реестру и разметке плана»,
взял разметку плана и с этой строкой разошёлся. Довод «окно накрывает
entry-fee по построению» сохраняет силу как обоснование **закрытия
развилки о границе**, не как разметка кейса — он живёт в
`docs/models/domain/aggregate/Deal.md` §«Почему у нижней границы один
писатель».

## Форвард-фокус: ось упущенных возможностей (фаза 3)

Реакции проекта на отказы — преимущественно «запрет новых входов +
ручное снятие»: цена отказа конвертируется в упущенные возможности,
которые система не измеряет (входы, не дошедшие до создания `Deal`, не
счётны ни одним механизмом). Смежное — разрешимость R-выборки. Обе оси
— **названное ограничение фазы 1**, содержательный разбор — фаза 3;
развёрнутый текст — в архивной редакции (§История).

## История

Полная редакция на момент распила по домам (хроника находок
`DOCS_CHECK`/`GAPS_CLOSE`, снятые формулировки, развёрнутые доводы,
CODE/schema-дельта) — архив
`.claude/work/history/2026-08-26-policy-home-split/pnl-finalization-mechanics-full.md`.

## Связи

- Принцип «один носитель-дом» — `.claude/rules/policy-home.md`.
- Источник числа — `docs/decisions/result-profit-source.md`.
- Сверка (дом политики) — `docs/rules/pnl-reconciliation.md`.
- Терминальный контракт — `docs/lifecycles/Deal.md` §«Терминальный
  контракт финализации».
- Риск-политика и комиссии — `docs/decisions/per-trade-risk-policy.md`.
- Биржевая лестница — `docs/rules/exchange-hold.md`,
  `docs/decisions/exchange-safety-ladder.md`.
- Инструмент-холд — `docs/rules/instrument-hold.md`.
- Error-градация — `docs/rules/error-handling-policy.md`,
  `docs/rules/runtime-error-classification.md`.
- Финализационная механика шага 6 —
  `docs/decisions/deal-finalization-state-materialization.md`.
