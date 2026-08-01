# DealCashFlow

## На какой вопрос отвечает этот файл

Что это за модель `DealCashFlow`: структура, енум `CashFlowCategory`,
персистентность, линковка к `Deal`.

## Назначение

`DealCashFlow` — **persisted** доменная модель `other`: журнал одного
денежного движения по аккаунту, отнесённого к сделке. Носитель
**категорийной разбивки** результата сделки (торговая комиссия / funding /
rebate / ликвидационный штраф / реализованный pnl / прочее) — из bills
(`GET /api/v5/account/bills[-archive]`, доменно наполняется командой
`REFRESH_BILLS`).

Число `resultProfit` — **не отсюда**: заголовочное число берётся готовым
**net'ом** из positions-history (`realizedPnl`), не из `sum(bills)`
(`docs/decisions/result-profit-source.md`). Роль `DealCashFlow` —
**разбивка** (категорийная атрибуция: сколько комиссии vs funding vs штраф)
плюс **независимая сверка** суммы flows с net из positions-history (контроль
целостности). Механика финализации, сверка и cross-ccy —
`docs/decisions/pnl-finalization-mechanics.md` (реш.1 `REFRESH_BILLS`, реш.5
сверка/cross-ccy, «Носители»).

Не торговая бизнес-сущность (бизнес-циклом сделки владеет `Deal`), а
журналируемый факт денежного движения для аудита/разбивки — поэтому
`docs/models/domain/other/`, по аналогии с `AnomalyReport` /
`DealActionState` (`.claude/decisions/models-core-vs-other.md`).

## Структура

Java-модель, наследует поля аудита от `Auditable`.

| Поле | Тип | Обязательно | Назначение |
|---|---|---|---|
| `id` | `Long` | да | Внутренний идентификатор в БД. |
| `dealId` | `Long` | нет | Сделка-владелец движения. Проставляется `RefreshBillsExecutor` при сохранении (bills не несут `dealId`, см. «Линковка к `Deal`»). **В текущем потоке персистится только сматченное движение, поэтому в БД колонка фактически всегда заполнена** — включая cross-ccy-запись, которая тоже линкуется (H5, `GAPS_CLOSE_6`). Nullability — форвард-слот под будущего производителя, персистящего движения аккаунта вне сделки (аккаунт-широкий скан `AnomalyJob`, шаг 8), а не наблюдаемое состояние фазы 1. |
| `category` | `CashFlowCategory` | да | Категория движения (доменный enum). Резолвится **из `type`/`subType`** bill-записи — по типу операции, не по знаку `externalFee` (`docs/models/mapping/DealCashFlow.md` §«Резолв категории»). |
| `amount` | `BigDecimal` | да | Знаковая сумма движения (минус — списание, плюс — начисление). Отвечает на вопрос «**сходится ли сумма**»: Σ`amount` сверяется с net из positions-history. |
| `externalFee` | `BigDecimal` | нет | Знаковая **комиссионная компонента** записи (минус — комиссия, плюс — ребейт; сырой `fee` bill-записи, **без нормализации знака** — в отличие от прогнозной ставки `TradeFeeRate`: `docs/models/mapping/DealCashFlow.md` §«Знак `externalFee` — сырой»). Отвечает на вопрос «**сколько комиссии**»: комиссия сделки = Σ`externalFee` по торговым движениям. `null` — у события комиссионной компоненты не бывает (например funding); `0` — бывает и нулевая. Взята явно, а не выведена из знака `amount` — иначе комбинированная запись (`amount` = pnl + комиссия) исказит разбивку. |
| `ccy` | `String` | да | Валюта движения (штатно — `USDT`). **Обязательно** — без валюты cross-ccy движение теряется молча при сверке (`pnl-finalization-mechanics.md` реш.5). Чужая `ccy` — нарушение инварианта «комиссии только в settle-ccy» (`docs/rules/trading-constraints.md`), а не рабочий режим: строка **персистится и линкуется**, помечается `AnomalyReport` и входит в число USDT-эквивалентом по курсу закрытия (H5, `GAPS_CLOSE_6`). **Критерием матчинга к сделке не является** — только проверяемым атрибутом. |
| `externalBillId` | `String` | да | `billId` bill-записи. Ключ идемпотентности/дедупа `REFRESH_BILLS` (см. «Персистентность»). |
| `externalType` | `String` | да | Сырой `type` bill-записи (интерпретируется в `category`). |
| `externalSubType` | `String` | нет | Сырой `subType` bill-записи (для funding: `173` expense / `174` income). |
| `externalOrderId` | `String` | нет | `ordId`, если bill связан с ордером. `null` для движений без ордера (например funding). |
| `externalTs` | `OffsetDateTime` | да | Время bill-события (Unix ms источника → доменное время). |

## Енум `CashFlowCategory`

Категория = **тип события источника** (`type`/`subType`), не знак числа
(`docs/models/mapping/DealCashFlow.md` §«Резолв категории»):

- `TRADE_FEE` — торговая комиссия **отдельной записью** (источник эмитит
  списание комиссии самостоятельным bill'ом).
- `FUNDING` — funding-платёж по SWAP (списание/начисление; `subType` `173`
  expense / `174` income).
- `REBATE` — ребейт отдельной записью (возврат/скидка комиссии).
- `LIQ_PENALTY` — ликвидационный штраф (издержка принудительного закрытия
  позиции биржей).
- `REALIZED_PNL` — торговое движение по факту закрытия/частичного закрытия
  позиции. **Сюда же — комбинированная запись** (`amount` = pnl + комиссия):
  событие торговое, комиссия едет компонентой `externalFee` на той же строке.
- `OTHER` — прочее cashflow, не отнесённое к перечисленным категориям.

`TRADE_FEE`/`REBATE` против `REALIZED_PNL` — вопрос **гранулярности
источника** (отдельная fee-запись или комбинированная), а не знака: комиссия
как **число** достаётся из `externalFee` при любой гранулярности. Фактическая
гранулярность — рантайм-вопрос (`.claude/tests/source-api/okx/plan.md`
**§AG3.5**; фикстура общая с §AG1.5, но вопрос гранулярности — в AG3.5,
H11 `GAPS_CLOSE_6`); категорийная ось от ответа не зависит.

## Персистентность

Реляционная таблица `deal_cash_flows` (множественное число, по codestyle
§Схема БД):

- FK-колонка `deal_id` (`null` до матчинга; проставляется при сохранении).
- **`UNIQUE(external_bill_id)`** — идемпотентность/дедуп `REFRESH_BILLS`:
  повторный проход команды не задваивает движение (модель — место истины
  ключа уникальности, `docs/rules/idempotency-via-unique.md`; upsert по
  `external_bill_id`).
- **Одна строка на bill-запись.** Следствие ключа выше: комбинированная
  запись источника (`amount` = pnl + комиссия) **не разбирается** на две
  строки — комиссия едет компонентой `external_fee` на той же строке. Две
  строки задвоили бы движение в Σ`amount` (сломав сверку) и не имели бы
  второго `billId` под `UNIQUE`.
- Индекс по `deal_id` — выборка разбивки сделки и сверка.
- `category` хранится **строкой** (значение = `name()` доменного enum),
  persistence-поле — `String` без `@Enumerated` (codestyle §Слои моделей и
  enum'ы).

**Реляционно, а не JSONB-навес.** По `docs/rules/persistence-representation.md`
представление определяет характер связей, не число полей. Здесь на строку
завязаны: дедуп по адресуемому `external_bill_id` (`UNIQUE`), сверка суммы
flows и запросы категорийной разбивки по `deal_id`. Каждое движение —
адресуемая строка с собственным ключом уникальности и индексом выборки; у
JSONB-навеса нет цели для `UNIQUE(external_bill_id)` и индекса `deal_id`.
Поэтому `DealCashFlow` — собственная таблица, а не JSONB-коллекция на строке
`Deal`.

## Линковка к `Deal`

Bills **не несут** `dealId` — только `instId`, `ccy`, `ts`, `ordId`.
`RefreshBillsExecutor` матчит движение к сделке по **окну сделки** +
`instId` (== `Deal.instrument.externalId`) и **проставляет `deal_id`** при
сохранении (`docs/integrations/okx/contracts/account-bills.md`
§Использование).

**Операнды окна названы** (H14, `GAPS_CLOSE_6`): `begin` =
`Position.externalCreatedAt` сделки (биржевое `cTime` открытия позиции);
позиции нет (сделка до неё не доехала) — `externalCreatedAt` первого
отправленного `Order`. `end` = `externalUpdatedAt` снапшота
positions-history (`uTime` финализированной записи); недоступен (аварийная
тропа без записи) — момент терминализации сделки. Границы **включительные**;
искусственного зазора не вводим. Остаточный риск «движение с `ts` на границе
окна» ловится сверкой: недобранное движение даёт расхождение сверх epsilon →
`AnomalyReport` (§Сверка в `docs/models/mapping/DealCashFlow.md`);
рантайм-наблюдение — фикстура §AG1.5.

**Валюта критерием матчинга не является** (H5, `GAPS_CLOSE_6`). Прежняя
редакция включала `ccy == Deal.resultProfitCurrency` в предикат линковки —
вместе с фильтром `ccy` в самом запросе это делало cross-ccy guard мёртвым.
Теперь `ccy` — **проверяемый атрибут**: не совпала → строка всё равно
персистится и линкуется, плюс `AnomalyReport`
(`docs/models/mapping/DealCashFlow.md` §«Guard оживлён»).

## Rich-модель

Предикаты собственных данных — по потребности `CODE`, например `isFee()`
(`category == TRADE_FEE`), `isFunding()`. Заводятся, когда появляется
вызывающий их код, не превентивно (codestyle §Неиспользуемый код).

## Связи

- Источник числа и роль bills (разбивка + сверка, не первоисточник) —
  `docs/decisions/result-profit-source.md`.
- Механика финализации (`REFRESH_BILLS`, сверка, cross-ccy, носители) —
  `docs/decisions/pnl-finalization-mechanics.md`.
- Контракт bills и поток применения —
  `docs/integrations/okx/contracts/account-bills.md`.
- Native bill-поля — `docs/models/integrations/okx/OkxAccountBillResponse.md`.
- Mapping bills → `DealCashFlow` — `docs/models/mapping/DealCashFlow.md`.
- Правило представления в БД — `docs/rules/persistence-representation.md`.
- Сверка сверх epsilon и cross-ccy → `AnomalyReport`
  (`docs/models/domain/other/AnomalyReport.md`).
