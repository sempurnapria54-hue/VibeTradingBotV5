# TradeFill — mapping между слоями (стаб)

## На какой вопрос отвечает этот файл

Как нативные fills источников ложатся на доменный `TradeFill`,
если он будет введён.

## Статус

**Стаб.** `TradeFill` как persisted entity на первом этапе **не
введён** (см. **OKX-Q1** в
`.claude/work/questions/open-questions.md`). `RefreshFillsExecutor`
агрегирует filled-метрики в существующие `Order`/`AlgoOrder`/
`Position` без отдельной persisted-сущности
(`docs/components/RefreshFillsExecutor.md`).

Файл создан как placeholder для будущего mapping при материализации
`TradeFill` (после закрытия OKX-Q1).

## Контекст

Сквозные правила — `docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'ов
— `docs/integrations/<name>/contracts/fills.md`. Поля native —
`docs/models/integrations/<name>/...FillResponse.md` (для OKX:
`OkxFillResponse.md`).

## Существующие связи

Уже сейчас, без materialised `TradeFill`:

- `ordId` ↔ известный `Order.externalId` /
  `AlgoOrder.linkedOrderExternalIds`;
- `clOrdId` ↔ `Order.internalId`;
- Совокупный `fillSz`, `fillPx`, `fee` по `ordId` агрегируется в
  `Order` (`accumulatedFillSize`, `averagePrice`, накопленная `fee`)
  при refresh-контуре; ack-not-runtime-truth применяется
  (`docs/rules/ack-not-runtime-truth.md`).
- Идемпотентность `RefreshFillsExecutor` гарантирует, что повторный
  вызов не задваивает агрегаты
  (`docs/components/RefreshFillsExecutor.md`).

## OKX (отложено)

Когда `TradeFill` будет введён — таблица native→snapshot/domain
заполняется по полям из `docs/models/integrations/okx/OkxFillResponse.md`.
До материализации поля DTO зафиксированы там; маппинг здесь —
заглушка.
