# Raw exchange DTO не выходит за adapter-layer

## На какой вопрос отвечает этот файл

Какое правило системы ограничивает распространение raw exchange
DTO по слоям.

## Правило

Raw exchange DTO (полный сырой response/DTO биржи) не выходит за
пределы `IntegrationService` / adapter-layer.

- `IntegrationService` получает raw response, валидирует структуру и
  обязательные поля, проверяет exchange-specific invariants и маппит
  **только** runtime-useful поля в validated **normalized external
  snapshot**.
- Наружу (в executor / domain / risk-layer) выходит только
  normalized snapshot, не raw DTO.
- Validation-only поля биржи используются внутри `IntegrationService` и в
  normalized snapshot не попадают.

### Nullable contract IntegrationService

Для read/refresh общий контракт: snapshot найден → `ExternalSnapshot`;
успешно, но не найден → `null`; ошибка API / parse / invariant →
exception. `null` означает «не найдено в этом источнике», а не ошибку;
трактовка зависит от сущности (для `Position` `null` = позиции нет; для
`Order`/`AlgoOrder` последний `null` после полного evidence-cycle может
быть error/recovery). См. `docs/components/IntegrationService.md`,
`docs/rules/external-status-resolution.md`.

### Граничные `*ExternalSnapshot`

Маппер из client-модели возвращает validated `*ExternalSnapshot`
(`InstrumentExternalSnapshot`, `InstrumentExternalRulesExternalSnapshot`,
`MarketPriceDataExternalSnapshot`, `BalanceContainerExternalSnapshot`,
`CandleExternalSnapshot`, order/algo/position external snapshots;
**сущности шага 7** — `PositionCloseResultExternalSnapshot`,
`TradeFeeRateExternalSnapshot`, `DealCashFlowExternalSnapshot`) —
external-поля модели, без доменных enum/нормализаций (они
резолвятся при материализации). Это и есть единственное, что
выходит за `IntegrationService`.

**Реестр обязан пополняться вместе с сущностью** (H21 `GAPS_CLOSE_13`):
клейм единственности превращает пропуск в инструкцию — писатель, не нашедший
свою сущность в перечне, читает это как «у неё границы нет». Три снапшота
шага 7 в перечень не попадали, и у одного из них (bills) звена снапшота
не существовало вовсе: цепочка шла `raw → domain`, то есть за границу
выходила persisted доменная модель с незаполненными `NOT NULL`-полями.
Составы транзитных снапшотов живут в mapping-доках своих сущностей
(`docs/models/mapping/DealCashFlow.md`,
`docs/models/mapping/PositionCloseResult.md`,
`docs/models/mapping/TradeFeeRate.md`) — отдельного `*ExternalSnapshot.md`
они не требуют. Для свечей это означает: OKX-массив
проходит границу как `CandleExternalSnapshot`, а не сырым массивом
(`docs/models/mapping/Candle.md`). Граница онбординга инструмента
(шаг 1) — `InstrumentExternalSnapshot`: идентичность + биржевые
`externalStatus`/`externalLeverage` (персистятся на `Instrument`) +
справочные sizing-поля (транзиентны в шаге 1); см.
`docs/models/mapping/Instrument.md`.
`InstrumentExternalRulesExternalSnapshot` относится к отложенной
rules-модели (backlog п.9).

### Balance — без normal null

Для `REFRESH_BALANCE_COMMAND` normal `null` contract не применяется: успешный
refresh обязан вернуть валидный `BalanceContainerExternalSnapshot` с
обязательной `settleCurrency`; пустой response / нет settleCurrency /
invalid fields → controlled external/account error (не `null`). См.
`docs/models/domain/core/BalanceContainer.md`,
`docs/models/mapping/Balance.md`.

## Почему

Normalized external snapshot содержит только поля, которые обновляют
доменную модель. Это держит домен независимым от формата конкретного
источника и не даёт source-specific деталям протекать в торговую
логику (source-specific факты живут в `docs/integrations/{name}/` —
см. `.claude/decisions/model-layer-ontology.md`).

## Где применяется

Сквозное правило, действует для всех refresh-flow всех сущностей
(balance, position, order, algo-order и т. д.). Конкретные маппинги —
в `docs/models/mapping/<Сущность>.md` (per-source подразделами).
Первоисточник правила — здесь (сквозной слой, см.
`.claude/decisions/rule-source-of-truth.md`).
