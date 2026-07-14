# Snapshot v13

**Дата:** 2026-05-28.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после закрытия миграции
API-кластера OKX, backlog п.10).

## Состояние

Миграция API-кластера OKX (26 REST endpoint-доков из
`.claude-archive/2026-05-21/docs/api/okx/`, кроме Playbooks v1) —
**завершена и закрыта**. После миграции архивные api-файлы больше не
используются как источник истины (`.claude/decisions/migration-triad.md`).

## Что изменилось относительно v12

**Создано в `docs/client/okx/` (9 новых файлов):**
- `rules/`: `okx-candle-mapping.md`, `okx-fills-mapping.md`,
  `okx-fills-archive-mapping.md`, `okx-account-bills-mapping.md`,
  `okx-ws-limits.md`, `okx-service-urls.md`.
- `models/`: `OkxFillResponse.md`, `OkxFillsArchiveResponse.md`,
  `OkxAccountBillResponse.md`.

**Дополнено в существующих `docs/client/okx/`:**
- `rules/okx-order-mapping.md` — endpoint'ы с rate-limit/permission,
  body amend (`cxlOnFail`/`pxAmendType`/`attachAlgoOrds`-mod), response
  ACK секции (create/amend/cancel), pagination, `tag`/`stpMode`/`ccy`
  policy.
- `rules/okx-algo-order-mapping.md` — endpoint'ы с rate-limit,
  response ACK, ordType-specific body
  (`conditional`/`oco`/`trigger`/`move_order_stop`), history с
  обязательным `ordType`.
- `rules/okx-position-mapping.md` — endpoint'ы с
  permission/rate-limit, close-position body
  (`autoCxl`/`mgnMode`/`posSide`/`ccy`), подтверждение факта закрытия.
- `rules/okx-instrument-mapping.md` — endpoint permission/rate-limit,
  WS-альтернатива, таблица используемых полей response, sizing-формула.
- `rules/okx-market-price-data-mapping.md` — endpoint, WS-канал
  `tickers`, таблица полей response.
- `models/OkxOrderResponse.md` — `cTime`/`uTime` → snapshot, секция
  validation/диагностики, расширенные attached protection поля.
- `models/OkxAlgoOrderResponse.md` — `cTime`/`uTime`, секция
  iceberg/TWAP/диагностики, attached в algo.
- `models/OkxPositionResponse.md` — close-position response details,
  `closeOrderAlgo[]` поля.

**Новые open-questions** (`open-questions.md`): **OKX-Q1**
(persisted `TradeFill` модель и executor финализации), **OKX-Q2**
(`TradeFillsArchive` + async-флоу выгрузки), **OKX-Q3** (bills как
источник `DealCashFlow` / финализации `Deal`), **OKX-Q4** (WS-каналы
OKX как отдельный заход). Все самодостаточны (формулировка, цитаты
архива, варианты, обратные ссылки).

**Backlog:** п.10 «API-кластер OKX» свёрнут как закрытый; шапка
дополнена строкой про миграцию OKX; «Связанные открытые вопросы»
дополнены OKX-Q1..Q4.

**История:** прогресс-файл `okx-api-разведка.md` удалён из
`progress/`; summary — `.claude/work/history/2026-05-28-миграция-api-okx.md`.

## Структурные решения по развилкам

Зафиксированы в файлах (не вынесены в decisions/, чтобы не плодить
ad-hoc decisions на каждое локальное решение):
- **Свечи vs ticker:** отдельный `okx-candle-mapping.md` — «один
  mapping = одна доменная роль». Альтернатива (объединить с
  `market-price-data`) помечена в файле.
- **Request-DTO:** не заводим `Okx*Request.md`; request-структура
  остаётся в `Domain → request mapping` существующих mapping.
- **Connectivity:** два файла `okx-ws-limits.md` +
  `okx-service-urls.md` — «один файл = одна забота». Альтернатива
  (объединённый `okx-connectivity.md`) помечена в каждом файле.

## Текущая структура

См. `.claude/rules/structure.md`. Каталоги не менялись.
`docs/client/okx/rules/` теперь содержит 13 файлов;
`docs/client/okx/models/` — 7 файлов. `progress/` и `questions/tasks/`
пусты.

## Активные задачи

Нет активных задач исполнения. Следующий шаг — на выбор пользователя из
cross-cutting пунктов backlog: п.2 (mappers/checker), п.6 (аудит +
финализация PnL + TradeFill — связано с OKX-Q1/Q3), п.7
(ReconciliationJob / kill-switch flow / TradeRuleValidator), п.8
(валидатор стратегии + API examples), п.9 (Exchange/Instrument модели).

## Открытые общие вопросы

`open-questions.md`: PROC-Q1, RISK-Q1, ENUM-Q1, DEAL-Q3, TIME-Q1, CMD-Q1
(из миграции процессов); DEAL-Q1, DEAL-Q2 (продуктовая финализация
`Deal`); **OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4** (из миграции API-кластера
OKX). Остальные закрыты ранее; история — в соответствующих decisions.

## Что в работе

- Ничего в активной работе. Project Knowledge (claude.ai) требует
  обновления: добавлен `snapshot-v13.md`, изменены `backlog.md`,
  `open-questions.md`; PK должен указывать на последний snapshot.
