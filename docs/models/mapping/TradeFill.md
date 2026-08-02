# TradeFill — mapping между слоями (стаб; OKX-Q1 закрыт — не вводится)

## На какой вопрос отвечает этот файл

Как нативные fills источников легли бы на доменный `TradeFill`,
если бы он вводился (в фазе 1 — не вводится).

## Статус

**Закрыт (не вводится).** `TradeFill` как persisted entity в фазе 1 **не
материализуется** — **OKX-Q1 закрыт** на `GAPS_CLOSE_1` шага 7 (2026-07-03,
`docs/decisions/result-profit-source.md`): пофилловый аудит вне фазы 1;
число `resultProfit` берётся net'ом из positions-history, разбивка — из bills
(`DealCashFlow`), fills для этого не нужны. Order-fill-метрики
(`accFillSz`/`avgPx`) агрегируются в `Order` прямо из `OkxOrderResponse` при
`REFRESH_ORDER_COMMAND`; отдельная команда `REFRESH_FILLS` и её executor **сняты** на
шаге 7 (`docs/decisions/pnl-finalization-mechanics.md` реш.1).

Файл оставлен как исторический стаб; при будущей потребности в пофилловой
модели (вне фазы 1) mapping заполняется здесь.

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
- Order-fill-метрики (`accumulatedFillSize`, `averagePrice`, `fee`)
  приходят в `Order` **готовыми агрегатами** прямо из `OkxOrderResponse`
  (`accFillSz`/`avgPx`/`fee`) при `REFRESH_ORDER_COMMAND` — отдельного прохода по
  fills не нужно; ack-not-runtime-truth применяется
  (`docs/rules/ack-not-runtime-truth.md`).

## OKX (отложено)

Когда `TradeFill` будет введён — таблица native→snapshot/domain
заполняется по полям из `docs/models/integrations/okx/OkxFillResponse.md`.
До материализации поля DTO зафиксированы там; маппинг здесь —
заглушка.
