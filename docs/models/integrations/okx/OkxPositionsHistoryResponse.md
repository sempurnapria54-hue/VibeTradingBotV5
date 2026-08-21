# OkxPositionsHistoryResponse (OKX positions-history)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели OKX positions-history response и какие из
них использует bot.

## Контекст

Нативная модель источника OKX. Возвращается `GET
/api/v5/account/positions-history` (элемент `data[]`). Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Добывается **второй ногой команды `REFRESH_POSITION_COMMAND`** (evidence-cycle
live → positions-history внутри одной команды; наполняет
`PositionCloseResultExternalSnapshot`, который приземляется полями
положения закрытия на `Position` — H1/H3 `GAPS_CLOSE_7`,
`docs/decisions/pnl-finalization-mechanics.md` реш.1). Отдельной команды
`REFRESH_POSITIONS_HISTORY` нет.

Mapping в `PositionCloseResultExternalSnapshot` и далее в `Position` →
`Deal.resultProfit`
— `docs/models/mapping/PositionCloseResult.md`. Контракт endpoint'а / rate
limits / история закрытых позиций — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа `resultProfit` (что за данные) —
`docs/decisions/result-profit-source.md`.

Отличие от live `/positions` (native `OkxPositionResponse`): positions-history
несёт **realized**-факты закрытой позиции (`realizedPnl`, `closeAvgPx` и т. д.),
которых нет у live-DTO. Средняя цена **входа** за жизнь позиции
(`openAvgPx`) в used-набор **не входит** (H23 — потребителя нет,
`Position.externalAverageEntryPrice` пишет только live-нога, см.
§«Не используется»); средняя цена **выхода** (`closeAvgPx`) — **входит**
(H26 `DOCS_CHECK_10`: потребитель — калибровка запаса на проскок на тропе
attached-SL, H21 `DOCS_CHECK_11`).

## Инвентарь полей

### Используемые

Used-минимум для числа `resultProfit`: готовый net берётся одним полем
`realizedPnl`; своих слагаемых не складываем. Сверх числа used-набор несёт
операнды **create-тропы** (позиция впервые увидена уже закрытой) и
готовый funding.

| OKX field | Тип | Семантика |
|---|---|---|
| `realizedPnl` | string-decimal | готовый net realized P&L = `pnl` + `fee` + `fundingFee` + `liqPenalty` (посчитан биржей) → `Position.externalRealizedProfit` → `Deal.resultProfit` |
| `ccy` | string | валюта, в которой посчитан `realizedPnl` → `Position.externalResultCurrency`. В `Deal.resultProfitCurrency` **не переходит** — авторитет валюты результата — расчётная валюта инструмента, а это поле **проверяемый признак** (H10 `DOCS_CHECK_10`, `docs/models/domain/aggregate/Deal.md` §«Валюта результата: один авторитет») |
| `closeAvgPx` | string-decimal | средняя цена фактического выхода → `Position.externalCloseAveragePrice`; потребитель — калибровка запаса на проскок на тропе attached-SL (H26 `DOCS_CHECK_10`, операнд уточнён H21 `DOCS_CHECK_11`) |
| `pnl` | string-decimal | реализованный P&L **до** издержек → `Position.externalRealizedProfitGross`; потребитель — **первая пара** раздельной сверки разбивки по категориям (H19 `DOCS_CHECK_12`) |
| `fee` | string-decimal | знаковая комиссионная компонента (минус — комиссия, плюс — ребейт; **сырой знак**) → `Position.externalFee`; потребитель — **вторая пара** раздельной сверки (H19 `DOCS_CHECK_12`) |
| `fundingFee` | string-decimal | накопленный funding закрытой позиции → `Position.externalFundingCost`, **со снятием знака при маппинге** (ниже — издержка, положительна когда фондирование уплачено; H20 `DOCS_CHECK_12`, единственное место приведения — `docs/models/mapping/PositionCloseResult.md` §«Знак `fundingFee`»). Потребители — де-микширование R-мультипликатора (`docs/decisions/per-trade-risk-policy.md` §H25) и **третья пара** сверки. `FUNDING`-строки `DealCashFlow` — **сверка** этого числа, не источник (H20 `DOCS_CHECK_11`) |
| `liqPenalty` | string-decimal | ликвидационный штраф (**сырой знак**) → `Position.externalLiquidationPenalty`; потребитель — **четвёртая пара** раздельной сверки против категории `LIQ_PENALTY` (H7 `DOCS_CHECK_13`) |
| `type` | string | тип последнего закрытия (`1` частичное / `2` полное / `3` ликвидация / `4` частичная ликвидация / `5` ADL не полностью / `6` ADL полностью) → `Position.externalCloseType`; провенанс аварийного терминала **и** операнд `Deal.closeOutcome` (`1,2` → `NORMAL_EXIT`; `3,4` → `LIQUIDATION`; `5,6` → `FORCED_REDUCTION`; пусто либо вне `1..6` → `UNDETERMINED`; H2 `GAPS_CLOSE_13`) |
| `instId` | string | биржевой идентификатор инструмента записи → `externalInstrumentId` снапшота; **операнд структурной валидации** «запись относится к запрошенному инструменту» (H18 `DOCS_CHECK_14`). Без него корректность чтения держалась бы только фильтром запроса — знанием вызывающего, а не фактом ответа, и при снятии ограничения «один инструмент в контуре» молча давала бы чужую запись |
| `posId` | string | биржевой id позиции (ключ адресации записи; истекает ~30 дней после полного закрытия). На **update**-тропе сверяется с `Position.externalId`; на **create**-тропе — **пишется** в него (H4 `DOCS_CHECK_11`) |
| `direction` | string | направление закрытой позиции → `Position.direction` **только на create-тропе** (на update-тропе поле уже заполнено live-ногой). Без него create-тропа материализует `Position` без направления, а `positions.direction` nullable ⇒ отказ был бы тихим (H4 `DOCS_CHECK_11`). **Резолв сырого значения в доменный `Position.Direction` — в слое интеграции**, снапшот несёт уже доменное значение; незнакомое либо пустое — `ExternalInvariantViolationException` (H7 `DOCS_CHECK_15`, `docs/models/mapping/PositionCloseResult.md` §«Резолв направления»). **Какие значения источник фактически отдаёт — открытый хвост `integrator`** (перечень заводится в `docs/integrations/okx/contracts/position.md` §История) |
| `cTime` | string-ms | время создания записи → `Position.externalCreatedAt` (наследуется от `Auditable`) и далее **нижняя граница окна линковки** `Deal.billsWindowBegin` на create-тропе (H4 `DOCS_CHECK_11`) |
| `uTime` | string-ms | время обновления записи → `Position.externalModifiedAt` (сортировка/пагинация positions-history — тоже по `uTime`) |

### Не используется bot'ом (отбрасывается на маппинге)

Числом не потребляются: net берётся готовым `realizedPnl`; категорийная
разбивка (комиссия / funding / rebate / штраф) — из bills → `DealCashFlow`
(`docs/models/mapping/DealCashFlow.md`), не из этих полей.

- **Слагаемые net и производные PnL** (net берётся готовым `realizedPnl`):
  `settledPnl` (cross-FUTURES), `pnlRatio`.
  **`pnl` и `fee` в used возвращены** (H19 `DOCS_CHECK_12`, решение
  пользователя): контроль целостности расширен до **раздельных пар по
  категориям** (Σ по категории разбивки против соответствующего числа
  биржи), и эти два поля — правые операнды двух из четырёх пар. Это **прямая
  цена** выбранной формы контроля, названная при закрытии.
  **`liqPenalty` в used возвращён** (H7 `DOCS_CHECK_13`, решение
  пользователя): у категории `LIQ_PENALTY` появился правый операнд —
  **четвёртая** пара. Прежняя запись («остаётся выведенным… отдельной
  парой не сверяется») стояла на доводе «отдельного числа биржи под неё
  нет», который **ложен**: поле названо контрактом источника
  (`docs/integrations/okx/contracts/position.md` §История) и этим
  инвентарём. Единственная непокрытая категория жила при этом в левом
  хвосте распределения.
  **`fundingFee` в used возвращён** (H20 `DOCS_CHECK_11`, решение
  пользователя): потребитель существует и записан **в другом доке** —
  де-микширование R-мультипликатора (`per-trade-risk-policy.md` §H25).
  Тот же довод, которым в used возвращён `closeAvgPx`; прежняя запись
  «потребителя в фазе 1 нет» выбирала слабейший из двух источников
  (best-effort bills против авторитетного числа биржи) и делала это
  молча.
- **Объёмы / прочие цены:** `openMaxPos` (максимум позиции), `closeTotalPos`
  (накопленный закрытый объём), `nonSettleAvgPx` (cross-FUTURES).
- **Выведен из used на `GAPS_CLOSE_7` (H22)** — потребителя в фазе 1 нет,
  поля без потребителя не заводим (codestyle §«Неиспользуемый код»):
  `triggerPx` (цена триггера ликвидации/ADL) — кандидат в носители
  провенанса ликвидации/ADL, вопрос открыт (`PNL-Q1`). Побочно снят
  остаток H19: расхождение доков о применимости `triggerPx` больше ничего
  не нагружает — поле не маппится.
  **`closeAvgPx` в used возвращён** (H26 `DOCS_CHECK_10`): у него назван
  потребитель — калибровка запаса на проскок; правило «поле вместе с
  потребителем» соблюдено, а не обойдено.
- **Выведен из used на `DOCS_CHECK_8` (H23)**: `openAvgPx` (средняя цена
  входа за жизнь позиции). Маппинг `openAvgPx →
  Position.externalAverageEntryPrice` делал колонку двуписьменной (live
  `avgPx` — текущая средняя, `openAvgPx` — средняя за жизнь; при доборах
  расходятся, провенанс поля неоднозначен) —
  `Position.externalAverageEntryPrice` пишет **только live-нога**
  (`docs/models/mapping/PositionCloseResult.md`).
- **Идентификация / атрибуты позиции** (не нужны числу; USDT-SWAP net /
  isolated фиксированы адаптером): `mgnMode`, `posSide`, `lever`, `uly`,
  **`instType`** (H18 `DOCS_CHECK_14`, внесён в unused **с доводом**:
  тип инструмента дублирует ось запроса `instType=SWAP`, а идентичность
  записи проверяется по `instId`, которому тип не нужен; прежде
  `instId`/`instType` не значились **ни в одной** секции инвентаря —
  не «отброшены», а пропущены). **`instId` — в used** (та же находка):
  операнд структурной валидации, см. §Используемые.
  **Посылка о наличии `instId`/`instType` в `data[]` взята из
  контракт-дока проекта, не из офдока** — сверка с первоисточником за
  `integrator`; если источник инструментной оси в ответе не даёт,
  валидация вырождается в записанное ограничение «корректность держит
  фильтр запроса».
  **`cTime` и `direction` в used возвращены** (H4 `DOCS_CHECK_11`):
  create-тропа (позиция впервые увидена уже закрытой) материализует
  `Position` из этой записи и заполняет ею нижнюю границу окна линковки —
  оба поля её операнды, потребитель ратифицирован (H9 `GAPS_CLOSE_10`).
  `cTime` попал в unused ещё и вопреки правилу `Auditable`: время
  создания и обновления, отдаваемое биржей, в unused не выводится —
  у него всегда есть наследуемый носитель
  (`docs/models/domain/other/Auditable.md` §«Ревизия инвентаря
  источника»).

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp string →
epoch millis → `OffsetDateTime` (конвенция типов времени проекта;
`docs/models/mapping/PositionCloseResult.md`).
