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

## Где применяется

- `OkxIntegrationService` (создание/отмена ордеров, close-position);
- adapter validation при refresh (request rejected, если received
  значение ≠ ожидаемого).

## Связанные mapping-доки

- `docs/models/mapping/Order.md` (adapter constants — `isolated`
  → `tdMode`, `net` → `posSide`).
- `docs/models/mapping/AlgoOrder.md` (то же).
- `docs/models/mapping/Position.md` (close-position body).

## Связано с

- `docs/rules/raw-exchange-dto-boundary.md` — adapter изолирует
  source-policy от domain.
