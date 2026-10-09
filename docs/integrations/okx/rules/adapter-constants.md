# OKX adapter constants: tdMode / posSide

## На какой вопрос отвечает этот файл

Какие константы OKX adapter выставляет сам, не из доменных моделей.

## Правило

Коннектор сам выставляет в request body всех операций
`Order`/`AlgoOrder`/`Position`:

- `tdMode = isolated` — режим торговли;
- `posSide = net` — сторона позиции (net-режим аккаунта; в `Order`/
  `AlgoOrder`/`Position` доменно не хранится).

Эти значения **не приходят из domain** и **не передаются как
аргументы** — это adapter-policy. Domain-уровень не знает про режимы
OKX. **Исключение одно — режим маржи закрытия позиции:** снятие риска вне
графа сделок закрывает запись иного режима её собственным режимом и
приносит его аргументом — доменным значением режима маржи, которое
коннектор переводит в `mgnMode`; пустой аргумент читается константой
(`docs/components/KillSwitchExecutor.md`, `docs/models/mapping/Position.md`).
Наш писатель от этого режима не меняет: заявки по-прежнему уходят одним
`tdMode`.

## Где применяется

- мапперы запроса `OrderMapper` и `AlgoOrderMapper` (постановка заявки
  и algo-заявки) и читатель источника `OkxSourceReader` (close-position,
  установка плеча);
- живое чтение позиции: `isolated` — операнд **отбора** записи контура по
  `mgnMode`, а не сверки — запись иного режима есть чужая позиция, не
  отказ (площадка держит изолированную и кросс-позицию одного инструмента
  рядом; правило отбора — `docs/models/mapping/Position.md`). Ответы
  чтения заявок и условных заявок с константами не сверяются.

## Связанные mapping-доки

- `docs/models/mapping/Order.md` (adapter constants — `isolated`
  → `tdMode`, `net` → `posSide`).
- `docs/models/mapping/AlgoOrder.md` (то же).
- `docs/models/mapping/Position.md` (close-position body).

## Связано с

- `docs/rules/raw-exchange-dto-boundary.md` — adapter изолирует
  source-policy от domain.
