# ManagingHandler

## На какой вопрос отвечает этот файл

Что делает FSM handler статуса `MANAGING` (компонент): проверки, логика,
шаги, команды.

## Назначение

Сопровождает открытую позицию по стратегии — основной рабочий статус
после входа и защиты. Конструкция handler'а —
`docs/components/DealStateMachine.md`; статусная механика —
`docs/lifecycles/Deal.md`.

## Входные проверки

`Deal.status = MANAGING`; pinned `StrategyDetail`; позиция активна с live
risk **или** есть факты, что позиция закрыта и нужен переход в
`EXIT_PENDING`; main protection актуальна; ≤1 живая позиция; нет чужих
live orders/algo; нет критичного расхождения и borrow/debt.

**`ACTIVE && externalSize == 0` — не normal `CLOSED`, и дальше ветка
зависит от `StrategyDetail.positionReopenAllowed`** (A9
`DOCS_CHECK_20`). Прежняя редакция относила этот факт к
cleanup/retry/anomaly безусловно — то есть трактовала схлопывание в ноль
как дефект и при `true`, где оно легитимно:

| `positionReopenAllowed` | Классификация `ACTIVE && externalSize == 0` |
|---|---|
| `true` | **штатное состояние**, не аномалия: живые входные ноги остаются, их филл открывает новый эпизод (`docs/lifecycles/Position.md` §«Смена эпизода»). Handler ничего не снимает и остаётся в `MANAGING` |
| `false` | **гейт срабатывает**: живые **входные** (не reduce-only) ноги снимаются cleanup-командами напрямую — тем же порядком и тем же инвариантом, что на выходе (`docs/rules/exit-teardown-order.md` §«Гейт в `MANAGING`»). Остаток — cleanup/retry/anomaly по прежним признакам |

- **Наблюдатель — этот handler.** Смену `externalSize` добывает нога 1
  `REFRESH_POSITION_COMMAND`, но исполнитель команды параметров
  стратегии не читает по построению
  (`docs/components/ServiceCommandExecutor.md` §Назначение), поэтому
  реакция живёт там же, где остальные решения `MANAGING`: факт durable
  (`Position.externalSize` на строке), читается из `DealContext`
  следующим проходом.
- **Названная цена.** Снятие идёт cleanup-командами, у которых нет
  исполнения-действия, значит нет и бюджета отказов
  (`docs/decisions/command-action-boundary.md` §2): неснятая нога не
  даёт ни ретрая, ни холда — её подберёт следующий проход. Цена уже
  принята проектом для всего cleanup; учёт — форвард на `TradeGuardJob`
  (H16 `DOCS_CHECK_11`).
- **Гейт — не новый тип шага стратегии.** Отдельный шаг потребовал бы
  объявления в `stepsByStatus`, а необъявленный шаг молча не работает —
  ровно то, чего избегает обязательность параметра
  (`docs/models/domain/aggregate/Strategy.md`
  §«`positionReopenAllowed` обязателен у торгуемой детали»).

## Рабочая логика

Обновить позицию/live-сущности при необходимости — добывающие
`REFRESH_*` идут звеньями `REFRESH_DEAL_CONTEXT_ACTION`
(`docs/components/SystemActionExecutor.md`), не прямой эмиссией; взять
`stepsByStatus[MANAGING]` (`PROTECTION_ADJUSTMENT`, `PARTIAL_EXIT`,
`GRID_MANAGEMENT`, `EXIT`, `FAIL_SAFE`); для data-dependent step —
freshness (`checkForStep`) → при устаревании `marketDataExpiredSetting`;
для fresh — `StrategyCondition`; для применимых — actions →
`DealActionState` → `StrategyActionCalculator` → нужные `ServiceCommand`.
Risk-creating actions — через risk-layer; reduce-only partial exit — без
`RiskValidator`, через safety/invariant checks (см.
`docs/rules/risk-validator-scope.md`). Полный выход → `CLOSE_POSITION_COMMAND` /
cancel-команды. `REFRESH_POSITION_COMMAND` без позиции → `EXIT_PENDING`;
`ACTIVE && externalSize==0` → по ветке `positionReopenAllowed`
(§«Входные проверки»); fail-safe → emergency.

## Выходные проверки

`→ EXIT_PENDING`, если стратегия инициировала выход / позиция
закрывается или закрыта / есть команда закрытия или факт через
`REFRESH_POSITION_COMMAND` / нужно дочистить хвосты. `→ ERROR`, если защита
потеряна без безопасного восстановления, активный риск без контроля,
опасное расхождение, >1 позиция, borrow/debt, небезопасный recovery.
Иначе остаётся в `MANAGING`.

## Допустимые StrategyStep

Steps: `PROTECTION_ADJUSTMENT`, `PARTIAL_EXIT`, `GRID_MANAGEMENT`, `EXIT`,
`FAIL_SAFE`. Перечень команд handler-док не держит: состав команд —
собственность действий (`docs/decisions/fsm-execution-layering.md`
§«Handler исполняет действия»; реестры звеньев —
`docs/decisions/command-action-boundary.md` §2,
`docs/components/SystemActionExecutor.md`). Ремодел защиты
(`PROTECTION_ADJUSTMENT`) — REPLACE-оркестрацией
(place-new → факт → cancel-old; `docs/decisions/replace-not-amend.md`),
амендных команд нет.

**Доборная нога приходит со своим attached SL, и он временный** (H4
`DOCS_CHECK_16`; редакция — решение держателя `GAPS_CLOSE_16`, Р3). Шаги,
создающие новую ногу входа в `MANAGING` (`GRID_MANAGEMENT` и пирамидинг), —
risk-creating, значит нога ставится со встроенной защитой по общему правилу
(`docs/rules/risk-creating-entry-protection.md` §Правило). После её
исполнения **основная standalone-защита пересчитывается под увеличенную
позицию** (`PROTECTION_ADJUSTMENT`, `REPLACE`), и подтверждение новой
основной защиты **снимает attached SL доборной ноги**
(`closeReason = SWITCHED_BY_STRATEGY`).

Окно двойной защиты в `MANAGING` — **переходное**, а не штатное: оно живёт
от филла доборной ноги до подтверждения пересчитанной основной, аномалией
не флагается и заканчивается снятием встроенной. Прежняя редакция
(«сосуществуют штатно, ремодела доборной защиты нет») **снята**.

**Триггер пересчёта — шаг стратегии** (решение держателя, позиция С2
валидации `GAPS_CLOSE_16`): `PROTECTION_ADJUSTMENT` с условием «позиция
увеличилась». Системный слой и совмещение пересчёта с тем же пакетом
действий, что создал добор, **отвергнуты**. Разбор —
`docs/rules/risk-creating-entry-protection.md` §«Защита доборной ноги
снимается после пересчёта основной».
