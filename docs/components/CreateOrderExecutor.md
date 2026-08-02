# CreateOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ORDER_COMMAND`. Создаёт локальный `Order` со статусом `CREATED`,
генерирует `internalId`, сохраняет рассчитанные параметры, создаёт
attached protection внутри order (если есть), обновляет target-колонки
`DealActionState` (`targetEntityType = ORDER`, `targetEntityId = orderId`
— объект `RuntimeTarget` расплющен в колонки,
`docs/decisions/command-action-boundary.md` §3) и
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

**Вместе с риском пишутся его операнды** (H6 `DOCS_CHECK_10`): той же
транзакцией — `Deal.plannedEntryPrice` (reference-цена входа, по которой
считался риск) и `Deal.plannedSizeContracts` (заявленный размер). Обе
величины уже лежат в payload (`price`, `sizeContracts`), но
`plannedEntryPrice` **нельзя брать с `Order.price`**: при market-входе
executor заполняет его только когда `sendPriceToExchange` истинно, то есть
reference-цена в сущности ордера не остаётся. Без этих двух чисел разрыв
«заявленный риск ↔ взятый» неизмерим постфактум
(`docs/models/domain/aggregate/Deal.md` §«Плановый риск»). Имена полей
предварительные.

**Write-once:** уже заполненный плановый риск не перетирается — ни
REPLACE-ремоделом стопа, ни добором; то же для его операндов. `R` — риск
**на входе**, бенчмарк измерения результата
(`docs/models/domain/aggregate/Deal.md` §«Плановый риск»). Для не-входных
`CREATE_ORDER_COMMAND` (защита, reduce-only) поля не пишутся. Правило
агрегации при многоногом входе (`GRID_ENTRY`/пирамидинг) — открытый вопрос
`RISK-Q3`.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / target-колонки — `docs/models/domain/other/DealActionState.md`.

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
