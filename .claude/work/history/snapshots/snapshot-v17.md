# Snapshot v17

**Дата:** 2026-05-29.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после `GAPS_CLOSE_1` шага 1
Фазы 1 — материализация концепции рыночных данных под код шага).

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 (поток рыночных данных) — прошёл
`TOOLING` → `DOCS_CHECK_1` → `GAPS_CLOSE_1`. Исполнение по
`.claude/processes/roadmap-step-execution.md`. На `DOCS_CHECK_1`
получен gap-отчёт (`phase-1-step-1-docs-check-1.md`); на
`GAPS_CLOSE_1` разобраны 6 эскалаций (в чате) и закрыты пробелы:
материализованы доменные модели рыночных данных, починены
несогласованности, обновлены открытые вопросы. Следующее действие —
`DOCS_CHECK_2` (повторная сквозная проверка стадийным обходом
`concept-review`).

## Что изменилось относительно v16

### Шаг 1: DOCS_CHECK_1 → GAPS_CLOSE_1

- Статус шага 1: `DOCS_CHECK_1` → `GAPS_CLOSE_1` (`phase-1.md`).
  Ролляп фазы 1 — `IN_PROGRESS` без изменений.

### Решения по эскалациям (применены)

- **Э1:** шаг 1 — **REST-first**, WS отложен до рефакторинга на
  микросервисы. Контракты рыночных данных приведены к REST-first;
  OKX-Q4 — не блокер шага 1.
- **Э2 / живая цена:** свежесть шаг 1 только производит (таймстемпы +
  `Auditable.externalCreatedAt/ModifiedAt`); `MarketPriceDataService`
  и тикер-фетч — зона FSM, вне кода шага 1.
- **Э3/Э4:** `Instrument`/`Exchange`/`Candle`/`CandleGroup` —
  материализованы из доменных классов.
- **Э5:** заведены сырые OKX DTO-инвентари (ticker/instrument/candle).
- **Э6:** lifecycle `CandleGroup` + раздел backfill/целостности в
  процессе на уровне классов; детали политики — на `DOCS_CHECK_2`.

### Новые доки (9)

- Модели: `docs/models/domain/core/Instrument.md`,
  `Exchange.md`; `docs/models/domain/other/Candle.md`,
  `CandleGroup.md`, `Auditable.md`.
- Lifecycle: `docs/lifecycles/CandleGroup.md` (8 статусов загрузки
  свечей).
- Сырые OKX DTO: `docs/models/integrations/okx/OkxTickerResponse.md`,
  `OkxInstrumentResponse.md`, `OkxCandleResponse.md`.

Схема хранения — в разделах «Персистентность» моделей (из `*Entity`);
слой `docs/models/persistence/` остаётся скаффолдом.

### Изменённые доки (7)

`market-data-calculation.md` (Н1 + backfill), `CandleJob.md`
(lifecycle загрузки), `mapping/Candle.md` (`→ domain`),
`mapping/InstrumentExternalRules.md` (N1: base/quote/settle →
snapshot), `mapping/MarketPriceData.md` + `contracts/market-price-data.md`
(Э1 REST-first), `mapping/TimeFrame.md` (TIME-Q1 канон → CandleGroup).

### Несогласованности — починены

- **Н1:** битая ссылка backfill→backlog п.8 в
  `market-data-calculation.md` заменена на lifecycle `CandleGroup`.
- **Н2:** битые указатели backlog п.5/п.6 на `docs/deprecated/`
  поправлены на `.claude-archive/2026-05-21/...`; для Candle/Instrument
  источник теперь — доменные классы.

### Decisions / правила

- `model-layer-ontology.md`: состав `domain/core` дополнен
  reference-core `Instrument`/`Exchange` (классификация на ревью —
  Java-пакет `core.*` + дискриминатор «биржевое воплощение»). Без
  supersede.

### Open-questions

- **TIME-Q1** — сужен: канон enum `TimeFrame` размещён в
  `CandleGroup.md` §TimeFrame; остаток — свёртка раздела в
  `Strategy.md` (шаг 2). Для кода шага 1 закрыт. Остаётся открытым.
- **OKX-Q4** — снят статус «потенциальный блокер шага 1» (REST-first);
  якорь пересмотра — микросервисы. Остаётся открытым.
- Прочие 10 — без изменений. Всего открыто 12 (2 обновлены).

## Активные задачи

Шаг 1 Фазы 1 (поток рыночных данных): `TOOLING` → `DOCS_CHECK_1` →
`GAPS_CLOSE_1` пройдены; активна — следующее `DOCS_CHECK_2`. Прочих
активных задач нет. Прогресс-файлы: `phase-1-step-1-docs-check-1.md`,
`phase-1-step-1-gaps-close-1.md`.

## Текущий фронтир / следующее действие

- **`DOCS_CHECK_2`** — повторная сквозная проверка концепции под код
  шага 1 (стадийный обход `concept-review`). Открытые пункты для
  проверки (из `GAPS_CLOSE_1`): разграничение `Instrument` ↔
  `InstrumentExternalSnapshot` ↔ `InstrumentExternalRules`
  (base/quote/settle, дедуп; `mapping/Instrument.md`); детали
  backfill/repair и глубины истории; приведение `timeframe`/статусов
  к enum в коде (на `CODE`); свёртка `TimeFrame` в `Strategy.md`
  (шаг 2).
- Тулинг `concept-review`/`reviewer` — в обкатке.

## Открытые общие вопросы

`open-questions.md`: DEAL-Q1, DEAL-Q2, PROC-Q1, RISK-Q1, TIME-Q1
(сужен), ENUM-Q1, CMD-Q1, OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4
(разблокирован для шага 1), DEAL-Q3 — все 12 открыты.

## Что в работе

- Шаг 1 Фазы 1: `GAPS_CLOSE_1` пройден; следующее — `DOCS_CHECK_2`.
  Project Knowledge требует обновления: последний снапшот теперь
  `snapshot-v17` (заменяет v16 в префлайте). Затронуты `docs/`
  (9 новых + 7 изменённых), `open-questions.md`, `backlog.md`
  (п.5/6/9), `phase-1.md`, `model-layer-ontology.md`.
