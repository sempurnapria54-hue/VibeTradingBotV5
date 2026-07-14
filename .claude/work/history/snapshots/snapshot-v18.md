# Snapshot v18

**Дата:** 2026-05-30.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после `GAPS_CLOSE_2` шага 1
Фазы 1 + вшивания критерия материализации процесса в скиллы и
выноса процесса `candle-loading`). Подготовлен под переезд в новый
чат.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 (поток рыночных данных) — прошёл
`TOOLING` → `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2` →
`GAPS_CLOSE_2`. Исполнение по
`.claude/processes/roadmap-step-execution.md`. Следующее действие —
**`DOCS_CHECK_3`** (повторная сквозная проверка концепции стадийным
обходом `concept-review`).

## Что изменилось относительно v17

### Шаг 1: DOCS_CHECK_2 → GAPS_CLOSE_2

- Статус шага 1: `DOCS_CHECK_2` → `GAPS_CLOSE_2` (`phase-1.md`).
  Ролляп фазы 1 — `IN_PROGRESS` без изменений.
- `DOCS_CHECK_2` дал gap-отчёт `phase-1-step-1-docs-check-2.md`
  (Н1, Н2, Н3, N1, N2, Q1; эскалации Э(2-1)…Э(2-3)).
- `GAPS_CLOSE_2` (`phase-1-step-1-gaps-close-2.md`) закрыл Н1, Н3,
  N1, N2, Q1; Н2 (тикер `instType`) — осознанно не трогаем (не
  блокер, тикер отложен в зону FSM).

### Решения GAPS_CLOSE_2 (применены)

- **Скоуп инструмента (Н1, N2).** `InstrumentExternalRules`
  отложена за пределы шага 1 (округление/sizing/риск; backlog п.9);
  base/quote/settle из неё убраны (дубль снят). Слой шага 1 =
  `Instrument` (идентичность + статус + `plannedCandleStartDate`) +
  `InstrumentExternalSnapshot` (транзиентная граница). snapshot↔domain
  шага 1 = только идентичность.
- **Lifecycle инструмента (N1).** Материализован онбординг-путь
  `CREATED → SYNC → CANDLES_LOADING → ACTIVE` + координация с
  `CandleGroup`; периферийные статусы отложены (backlog п.9).
- **DTO-граница свечей (Н3).** Введён `CandleExternalSnapshot`:
  OKX-массив → `CandleExternalSnapshot` → domain `Candle`; добавлен
  в перечень граничных снапшотов.
- **Политика целостности свечей (Q1).** `CandleGroup`:
  `actualFirst/actualLast/count` + density-инвариант; политика
  (горизонт на инструмент, BACKFILL/SYNC/CHECK/REPAIR, бинарный
  поиск, реконсиляция count, CRON) в lifecycle/CandleJob/процессе.
  Числа — на `CODE`.

### Новые доки GAPS_CLOSE_2 (2)

- `docs/models/mapping/Instrument.md` (snapshot↔domain = идентичность).
- `docs/lifecycles/Instrument.md` (онбординг-путь + координация).

### Изменённые доки GAPS_CLOSE_2 (10)

`Instrument.md` (+`plannedCandleStartDate`, lifecycle-ссылка,
переписана нота разграничения), `InstrumentExternalRules.md`
(отложена, валюты убраны), `CandleGroup.md` (actual*/count/density),
`lifecycles/CandleGroup.md` (политика), `CandleJob.md` (CRON/count),
`market-data-calculation.md`, `mapping/Candle.md`
(`CandleExternalSnapshot`), `raw-exchange-dto-boundary.md`,
`contracts/candle.md` (стоп → `plannedCandleStartDate`),
`OkxInstrumentResponse.md` (нота дедупа).

### Пайплайн: критерий материализации процесса вшит в скиллы

- `process-materialization-criterion.md` — снята пометка «скиллы
  пока не правим»; критерий встроен.
- `recognize-knowledge.md` — добавлен признак кандидата в процесс
  (поток/оркестрация над несколькими сущностями, координация джобы;
  триггер срабатывает и для потока, встроенного в другую задачу).
- `classify-type.md` (§«Компонент vs процесс») — материализация
  процесса не автоматическая, прогон критерия при размещении.
- `place-knowledge.md` (§«Кандидат в процесс») — двухусловный
  критерий перед созданием `docs/processes/<X>.md`; иначе
  распределение по владельцам.

### Продукт: вынос процесса candle-loading

- `docs/processes/candle-loading.md` (новый) — добыча и целостность
  свечей: цикл BACKFILL/SYNC/CHECK/REPAIR, оркестрация `CandleJob`,
  density-политика, координация онбординга инструмента.
- `market-data-calculation.md` — сужен до вычисления
  (индикаторы/структура/фаза) поверх загруженных свечей; использует
  `candle-loading` как поставщика. Кросс-ссылки (`CandleJob`,
  `CandleGroup` модель/lifecycle, `Instrument` lifecycle,
  `mapping/Candle`) переведены на `candle-loading`.
- `InstrumentExternalRulesSyncJob` убран из активной оркестрации
  `market-data-calculation` (это вычисление, не подготовка спеков
  инструмента; к загрузке свечей тоже не относится) и помечен
  отложенным вместе с `InstrumentExternalRules` (backlog п.9 /
  отложенная rules-подсистема); материализуется с правилами на
  поздних шагах. Компонент-док помечен «Отложено за пределы шага 1».

### Open-questions

- **INSTR-Q1** (новый, из `GAPS_CLOSE_2`): как снапшот-концепция
  ляжет на `InstrumentExternalRules` и не нужен ли ренейм rules.
- **ORCH-Q1** (новый, из выноса `candle-loading`): владелец
  оркестрации онбординга инструмента и загрузки свечей (драйвер
  `Instrument.Status` / `CandleGroup.Status`). Поглотил вопрос про
  владельца `Instrument.Status`, поднятый в `GAPS_CLOSE_2`.
- Всего открыто **14** (было 12 + INSTR-Q1 + ORCH-Q1). TIME-Q1 и
  OKX-Q4 по-прежнему открыты/сужены без изменений.

### Backlog

- П.9 обновлён (GAPS_CLOSE_2): онбординг-lifecycle `Instrument`
  материализован; разграничение `Instrument`/снапшот/rules для
  шага 1 закрыто; остаётся периферия статусов, `Exchange`/`Account`
  lifecycle, материализация rules → INSTR-Q1. «Связанные открытые
  вопросы» дополнены INSTR-Q1, ORCH-Q1.

## Активные задачи

Шаг 1 Фазы 1 (поток рыночных данных): `TOOLING` → `DOCS_CHECK_1` →
`GAPS_CLOSE_1` → `DOCS_CHECK_2` → `GAPS_CLOSE_2` пройдены; активна —
следующее `DOCS_CHECK_3`. Прочих активных задач нет. Прогресс-файлы:
`phase-1-step-1-docs-check-1.md`, `phase-1-step-1-gaps-close-1.md`,
`phase-1-step-1-docs-check-2.md`, `phase-1-step-1-gaps-close-2.md`.

## Текущий фронтир / следующее действие

- **`DOCS_CHECK_3`** — повторная сквозная проверка концепции под код
  шага 1 (стадийный обход `concept-review`). На что смотреть после
  правок: связки `Instrument` ↔ `lifecycles/Instrument` ↔
  `mapping/Instrument` ↔ `CandleGroup`; согласованность
  `count`/`actual*` во всех доках свечной подсистемы; корректность
  разделения `candle-loading` (добыча) vs `market-data-calculation`
  (вычисление); Н2 (тикер `instType`) остаётся открытым по решению
  (не блокер); INSTR-Q1 / ORCH-Q1 — шаг 1 не блокируют.
- Тулинг `concept-review`/`reviewer` — в обкатке.

## Открытые общие вопросы

`open-questions.md`: DEAL-Q1, DEAL-Q2, PROC-Q1, RISK-Q1, TIME-Q1
(сужен), INSTR-Q1, ORCH-Q1, ENUM-Q1, CMD-Q1, OKX-Q1, OKX-Q2,
OKX-Q3, OKX-Q4 (разблокирован для шага 1), DEAL-Q3 — все 14 открыты.

## Что в работе

- Шаг 1 Фазы 1: `GAPS_CLOSE_2` пройден; следующее — `DOCS_CHECK_3`.
  Project Knowledge требует обновления: последний снапшот теперь
  `snapshot-v18` (заменяет v17 в префлайте). Затронуты `docs/`
  (новые `candle-loading.md`, `lifecycles/Instrument.md`,
  `mapping/Instrument.md`; ряд изменённых), скиллы классификации,
  `process-materialization-criterion.md`, `open-questions.md`,
  `backlog.md` (п.9 + связанные вопросы), `phase-1.md`, прогресс.
