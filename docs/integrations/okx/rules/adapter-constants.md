# OKX adapter constants: tdMode / posSide

## На какой вопрос отвечает этот файл

Какие константы OKX adapter выставляет сам, не из доменных моделей.

## Правило

`OkxIntegrationService` сам выставляет в request body всех операций
`Order`/`AlgoOrder`/`Position`:

- `tdMode = isolated` — режим торговли;
- `posSide = net` — сторона позиции (net-режим аккаунта; в `Order`/
  `AlgoOrder`/`Position` доменно не хранится).

Эти значения **не приходят из domain** и **не передаются как
аргументы** — это adapter-policy. Domain-уровень не знает про режимы
OKX.

## Почему

Текущая конфигурация бота:
- Один инструмент `ETH-USDT-SWAP` в режиме `isolated`/`net`.
- Расширения (cross / long-short hedge mode) не планируются на этом
  этапе.

Жёсткая константа избавляет от размазывания adapter-policy по domain
и от ситуаций, когда adapter и domain не согласованы. Сверка в
response (`tdMode == isolated`, `posSide == net`) — invariant check,
нарушение → safety-каскад.

## Где применяется

- `OkxIntegrationService` (создание/отмена ордеров, close-position);
- adapter validation при refresh (request rejected, если received
  значение ≠ ожидаемого).

## Связанные mapping-доки

- `docs/models/mapping/Order.md` §OKX (adapter constants — `isolated`
  → `tdMode`, `net` → `posSide`).
- `docs/models/mapping/AlgoOrder.md` §OKX (то же).
- `docs/models/mapping/Position.md` §OKX (close-position body).

## Связано с

- `docs/rules/raw-exchange-dto-boundary.md` — adapter изолирует
  source-policy от domain.
