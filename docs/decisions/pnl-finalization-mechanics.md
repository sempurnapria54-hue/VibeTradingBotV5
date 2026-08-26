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
`PNL_RECONCILIATION_MISMATCH`, финализация не блокируется. Инвариант
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
⇒ биржевая заморозка (`Exchange.HOLD`, ступень 1 лестницы), параллельно
с ошибочным терминалом. Исполнитель приравнивания —
`ServiceCommandExecutor` (предикат `Deal.status = ERROR`).

- **Отвергнуто:** разнести тропы отдельными типами действия
  (дискриминатор уже существует durable-полем `Deal.status`); «жёсткий
  отказ ≡ пусто» с нулевыми попытками (исход без durable-носителя,
  проглатывание нарушения контракта); признать холд на аварийной тропе
  штатным (довод радиуса).
- **Дом:** `docs/rules/pnl-reconciliation.md` §«Асимметрия троп отказа
  добычи»; ветка исполнителя —
  `docs/components/ServiceCommandExecutor.md` §«Ветка
  `Deal.status = ERROR`».

### 6. Инвариант агрегации positions-history — рантайм-верификация (N11)

Инвариант: одна сделка ↔ один `posId` ↔ одна финализированная запись
positions-history с `realizedPnl`, кумулятивным по всем
partial-закрытиям; читается финализированной. До рантайм-верификации —
**предположение**, гейтит корректность числа ⇒ верификация до `CODE`
(предусловие п. 1).

- **Дома:** `docs/models/mapping/PositionCloseResult.md`,
  `docs/integrations/okx/contracts/position.md` §«Инвариант агрегации»,
  `.claude/tests/source-api/okx/plan.md` §AG1.

## Носители (стадия 2)

`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`,
`docs/models/mapping/PositionCloseResult.md` (снапшот приземляется на
`Position`), `docs/models/domain/other/DealCashFlow.md` +
`docs/models/mapping/DealCashFlow.md`.

## Следствия

Целевая дельта `CODE` шага 7 (новые команды/executor'ы, не-схемная
дельта, полная schema-дельта) зафиксирована в архивной редакции этого
решения (§Следствия архива — см. §История ниже); рабочий носитель
дельты — `.claude/work/backlog.md` §Шаг 7. Биржевые реакции дельты
читаются по лестнице `docs/rules/exchange-hold.md` (`Exchange.HOLD` —
ступень 1, без kill-switch).

## Предусловия `CODE` шага 7

Реестр сквозной: гейты и «рассмотрено — не гейтит» в одном месте.
Развёрнутые доводы — в архивной редакции (§История) и в названных
носителях.

1. **Инвариант агрегации positions-history** (реш.6) — **гейтит**;
   рантайм-прогон `.claude/tests/source-api/okx/plan.md` §AG1.
2. **Непустой список исключений сверки для OKX** — **гейтит** (хвост
   `integrator`, содержание перечня;
   `docs/models/mapping/DealCashFlow.md` §«Область сверки»).
3. Закрыт (`RISK-Q4`): дом операндов планового риска — только `orders`;
   входной алго-тропы не существует.
4. Закрыт (`ANOM-Q5`): идемпотентность `STATE`-отчёта — незавершённым
   статусом (`docs/models/domain/other/AnomalyReport.md`).
5. **Носитель курса cross-ccy** — **гейтит** (хэнд-офф `integrator`:
   эндпоинт свечей, разрешение, правило деградации;
   `docs/components/RefreshBillsExecutor.md` §«Носитель курса»).
6. **Оси адресации записи positions-history без `posId`** — гейт
   остаётся, содержание — только грунт источника (хвост `integrator`).
7. **Знак и горизонт накопления `fundingFee`** (`AG1.7`) — **гейтит**
   (`docs/models/mapping/PositionCloseResult.md` §«Знак `fundingFee`»,
   `docs/decisions/per-trade-risk-policy.md` §H25). Знак
   `fee`/`liqPenalty` — тем же кейсом, **не гейтит** (правится по факту
   прогона; `PositionCloseResult.md` §«Знак `fee` и `liqPenalty`»).
8. **Калибратор `k` и условие выхода из разведочного режима** — **не
   закрыт**: названный калибратор («пользователь/бэктест») допуск на
   омиссию данных API произвести не может.
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
    (`ExternalInvariantViolationException` ⇒ биржевая заморозка
    `Exchange.HOLD`, ступень 1, ручное снятие) реджектил бы каждую
    нормально закрывшуюся сделку.

Негейтящие: `AG1.5` (нижняя граница окна: единственный писатель —
`SubmitOrderExecutor`, окно накрывает entry-fee по построению) и
`AG3.5` (гранулярность bills, fee-эхо) — рассмотрены, **не гейтят**;
прогон запланирован до `CODE`.

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
