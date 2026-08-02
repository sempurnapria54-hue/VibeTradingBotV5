# CreateOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ORDER_COMMAND`. Создаёт локальный `Order` со статусом `CREATED`,
генерирует `internalId`, сохраняет рассчитанные параметры, создаёт
attached protection внутри order (если есть), обновляет
`DealActionState.target = RuntimeTarget(ORDER, orderId)` и
`DealActionState.status = CREATED` — всё одной транзакцией. На биржу не
ходит, цену не пересчитывает, условия не проверяет.

## Плановый риск сделки (`R`)

Для **входного** действия executor той же транзакцией пишет
`Deal.plannedRiskAmount` / `plannedRiskCurrency` — величину `risk amount`,
посчитанную `RiskValidator` при преконтроле **этого же** действия
(`|entry − stop| × contracts × ctVal + commissions`). Валидация и создание
идут одним проходом (`docs/rules/risk-validator-scope.md`: валидатор
вызывается после расчёта цены/размера и **до** создания команды), поэтому
метрика доезжает без durable-слота между проходами.

**Write-once:** уже заполненный плановый риск не перетирается — ни
REPLACE-ремоделом стопа, ни добором. `R` — риск **на входе**, бенчмарк
измерения результата (`docs/models/domain/aggregate/Deal.md` §«Плановый
риск»). Для не-входных `CREATE_ORDER_COMMAND` (защита, reduce-only) поле не
пишется. Правило агрегации при многоногом входе
(`GRID_ENTRY`/пирамидинг) — открытый вопрос `RISK-Q3`.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / `RuntimeTarget` — `docs/models/domain/other/DealActionState.md`.

## CreateOrderCommandPayload

`orderType` (`Order.Type`), `strategyDirection` (`StrategyTradeDirection`),
`side` (buy/sell), `positionSide`, `instrumentExternalId`, `marginMode`,
`executionType`, `sizeContracts`, `price`, `sendPriceToExchange`,
`positionReducingOnly` (доменное намерение → OKX `reduceOnly` в adapter),
`attachedProtection` (`AttachedProtectionPayload`, если order создаётся со
стартовым SL/TP).

Хранит минимум для создания; client id (`internalId`), external id берутся
из создаваемой сущности. `positionSide`/`marginMode` — generic command-level
intent; OKX adapter всё равно ставит `tdMode=isolated`, `posSide=net` и
валидирует response (см. `docs/models/mapping/Order.md`).

### AttachedProtectionPayload

Параметры attached protection при создании order со стартовым SL/TP
(вложен в `CreateOrderCommandPayload.attachedProtection`). Структура
attached protection — `docs/models/domain/core/Order.md`
(`AttachedAlgoOrder`).
