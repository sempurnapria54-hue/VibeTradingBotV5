# OKX contracts: публичные сделки рынка

## На какой вопрос отвечает этот файл

Каков контракт операций чтения публичных сделок инструмента.

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Order
Book Trading → Market Data», секции «GET / Trades», «GET / Trades
history»). Процедура сверки и приоритет офдока —
`.claude/rules/external-source-sync.md`. Последняя сверка: 2026-06-11
(поле-уровневая дистилляция).

## Статус использования

Не используется (стратегия фазы 1 — на свечах; публичная лента
сделок не нужна). Не путать с приватными fills аккаунта
(`fills.md`).

## GET /api/v5/market/trades

Rate limit 100 req / 2 s по IP. Query: `instId` (обяз.), `limit`
≤ 500 (default 100).

### Поля элемента

| Поле | Семантика |
|---|---|
| `instId` | Инструмент. |
| `tradeId` | ID сделки. |
| `px` / `sz` | Цена / количество (спот — base ccy; деривативы — контракты). |
| `side` | Сторона **тейкера**: buy / sell. |
| `source` | Источник: `0` обычный ордер / `1` ELP-ордер. |
| `ts` | Время сделки (ms). |

## GET /api/v5/market/history-trades

Rate limit 20 req / 2 s по IP. Глубина — 3 месяца. Query: `instId`
(обяз.), `type` — тип пагинации (`1` по `tradeId` — default, `2` по
timestamp), `after` (tradeId или ts), `before` (только tradeId;
одиночный `before` отдаёт свежайшие), `limit` ≤ 100. Поля элементов —
как у `trades`.
