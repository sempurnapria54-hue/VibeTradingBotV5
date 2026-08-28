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
`EXIT_PENDING`; main protection **покрывает позицию** (предикат покрытия —
`docs/rules/live-risk-protection.md`; проверяется на
выходе, см.); ≤1 живая позиция; нет чужих
live orders/algo; нет критичного расхождения и borrow/debt.

| `positionReopenAllowed` | Классификация `ACTIVE && externalSize == 0` |
|---|---|
| `true` | **штатное состояние**, не аномалия: живые входные ноги остаются, их филл открывает новый эпизод (`docs/lifecycles/Position.md`). Handler ничего не снимает и остаётся в `MANAGING` |
| `false` | **гейт срабатывает**: живые **входные** (не reduce-only) ноги снимаются cleanup-командами напрямую — тем же порядком и тем же инвариантом, что на выходе (`docs/rules/exit-teardown-order.md`). Остаток — cleanup/retry/anomaly по прежним признакам |

- **Наблюдатель — этот handler.** Смену `externalSize` добывает нога 1
  `REFRESH_POSITION_COMMAND`, но исполнитель команды параметров
  стратегии не читает по построению
  (`docs/components/ServiceCommandExecutor.md`), поэтому
  реакция живёт там же, где остальные решения `MANAGING`: факт durable
  (`Position.externalSize` на строке), читается из `DealContext`
  следующим проходом.
- **Названная цена.** Снятие идёт cleanup-командами, у которых нет
  исполнения-действия, значит нет и бюджета отказов
  (`docs/rules/command-lifecycle.md`): неснятая нога не
  даёт ни ретрая, ни холда — её подберёт следующий проход. Цена уже
  принята проектом для всего cleanup; учёт — форвард на `TradeGuardJob`.
- **Гейт — не новый тип шага стратегии.** Отдельный шаг потребовал бы
  объявления в `stepsByStatus`, а необъявленный шаг молча не работает —
  ровно то, чего избегает обязательность параметра
  (`docs/models/domain/aggregate/Strategy.md`).

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
cancel-команды. «Живой позиции нет» — по ветке `positionReopenAllowed`
независимо от того, каким наблюдением это выяснилось : и `REFRESH_POSITION_COMMAND` без позиции, и
`ACTIVE && externalSize == 0` — один и тот же факт «эпизода нет»; fail-safe → emergency.

## Выходные проверки

`→ EXIT_PENDING`, если стратегия инициировала выход / есть команда
закрытия / нужно дочистить хвосты — **либо** живого эпизода нет и
выполнено хотя бы одно из двух: `positionReopenAllowed = false` **или**
живых входных ног у сделки не осталось. `→ ERROR`, если защита
потеряна без безопасного восстановления, активный риск без контроля,
опасное расхождение, >1 позиция, borrow/debt, небезопасный recovery.
Иначе остаётся в `MANAGING`.

**Четвёртая точка предиката покрытия — здесь**. При живом эпизоде выходная проверка считает
покрытие живых защит (формула — дом,
`docs/rules/live-risk-protection.md`) и сверяет его с
`Position.externalSize`. Покрытие ниже — **нарушение инварианта системы**:
`Exchange.TRADE_BLOCKED`, ступень 2 (`docs/rules/exchange-hold.md`), не
локальный `ERROR` сделки.

- **Что это закрывает.** `MANAGING` — единственный статус, где экспозиция
  растёт добором, а основная защита пересчитывается шагом стратегии; до
  этой правки покрытие между гейтом входа и терминалом не проверял **никто**,
  и дом инварианта сам признавал, что недоразмеренную основную защиту
  система не детектирует. Сюда же попадает **снятие ступени лестницы без
  замены**: `CANCEL_ALGO_ORDER_COMMAND` выведен из scope преконтроля, и
  другой точки, где падение покрытия видно, нет.
- **Достижимость ступени 2 в горячем пути — принятая цена**, не побочный
  эффект: реакция, сработавшая от собственной арифметики системы, —
  явный сигнал, что арифметику надо пересматривать; тихая деградация в этой
  роли отвергнута держателем.
- **Операнды уже в графе прохода** — новых чтений и полей не вводится.

- **Выход для `true` назван, и он обязателен.** Сделка с
  `positionReopenAllowed = true`, у которой нет ни живого эпизода, ни
  живых входных ног, переоткрыть позицию уже не может — ждать нечего.
  Без второго дизъюнкта она удерживала бы слот
  `uk_deal_active_instrument` бессрочно, то есть блокировала бы весь
  контур (инструмент один, активная сделка одна). Поэтому «живых
  входных ног нет» — самостоятельное условие ухода в `EXIT_PENDING`.
- **Дискриминатор не меняется.** Смену эпизода по-прежнему различает
  `posId`, а не размер (`docs/lifecycles/Position.md`);
  правка касается только того, при каком условии отсутствие живого
  эпизода означает **выход сделки**, а не паузу между эпизодами.

## Допустимые StrategyStep

Steps: `PROTECTION_ADJUSTMENT`, `PARTIAL_EXIT`, `GRID_MANAGEMENT`, `EXIT`,
`FAIL_SAFE`. Перечень команд handler-док не держит: состав команд —
собственность действий (`docs/processes/fsm-execution-layering.md`; реестры звеньев —
`docs/rules/command-lifecycle.md`,
`docs/components/SystemActionExecutor.md`). Ремодел защиты
(`PROTECTION_ADJUSTMENT`) — REPLACE-оркестрацией
(place-new → факт → cancel-old; `docs/rules/replace-not-amend.md`),
амендных команд нет.

**Доборная нога приходит со своим attached SL, и он временный**. Шаги,
создающие новую ногу входа в `MANAGING` (`GRID_MANAGEMENT` и пирамидинг), —
risk-creating, значит нога ставится со встроенной защитой по общему правилу
(`docs/rules/live-risk-protection.md`). После её
исполнения **основная standalone-защита пересчитывается под увеличенную
позицию** (`PROTECTION_ADJUSTMENT`, `REPLACE`), и подтверждение новой
основной защиты **снимает attached SL доборной ноги**
(`closeReason = SWITCHED_BY_STRATEGY`).

**Триггер пересчёта — шаг стратегии**: `PROTECTION_ADJUSTMENT` с условием «позиция
увеличилась». Системный слой и совмещение пересчёта с тем же пакетом
действий, что создал добор, **отвергнуты**. Разбор —
`docs/rules/live-risk-protection.md`.
