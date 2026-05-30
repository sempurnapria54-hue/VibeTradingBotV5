# Snapshot v19

**Дата:** 2026-05-30.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после `DOCS_CHECK_3` →
`GAPS_CLOSE_3` шага 1 Фазы 1: ревизия модели инструмента — биржевые
поля на `Instrument`). Подготовлен под переезд в новый чат.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 (поток рыночных данных) — прошёл
`TOOLING` → `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2` →
`GAPS_CLOSE_2` → `DOCS_CHECK_3` → **`GAPS_CLOSE_3`**. Исполнение по
`.claude/processes/roadmap-step-execution.md`. Следующее действие —
решение пользователя: `DOCS_CHECK_4` (полный прогон `concept-review`)
либо переход к **`CODE`** (после `GAPS_CLOSE_3` сделана
подтверждающая проверка затронутой области — чисто; к `CODE` по
заданию пока не переходили).

## Что изменилось относительно v18

### Шаг 1: DOCS_CHECK_3 → GAPS_CLOSE_3

- Статус шага 1: `GAPS_CLOSE_2` → `DOCS_CHECK_3` → `GAPS_CLOSE_3`
  (`phase-1.md`). Ролляп фазы 1 — `IN_PROGRESS` без изменений.
- `DOCS_CHECK_3` дал gap-отчёт `phase-1-step-1-docs-check-3.md`:
  почти чисто, две несогласованности в граничной модели снапшота
  инструмента (Н(3-1), Н(3-2)), эскалаций 0. Фокусы `GAPS_CLOSE_2`
  (count/actual/density; `candle-loading` vs `market-data-calculation`)
  — чисто.
- `GAPS_CLOSE_3` (`phase-1-step-1-gaps-close-3.md`) закрыл Н(3-1),
  Н(3-2) **плюс** применил ревизию модели инструмента, согласованную
  в чате.

### Ревизия модели инструмента (GAPS_CLOSE_3, ключевое изменение)

**Дополнение к `GAPS_CLOSE_2`, не противоречие.** Прежняя
формулировка «snapshot↔domain шага 1 = только идентичность»
**заменена**: шаг 1 `Instrument` несёт и биржевые поля.

- `Instrument` (шаг 1) = идентичность + онбординг-`Status`
  (`CREATED→SYNC→CANDLES_LOADING→ACTIVE`, без изменений) +
  `plannedCandleStartDate` + биржевые `externalStatus` (`String`,
  источник OKX `state`) + `externalLeverage` (`String`, источник OKX
  `lever`) + рабочее `leverage` (`Integer`, **задаётся при
  создании**, не из снапшота).
- `externalStatus`/`externalLeverage` **персистятся** из снапшота;
  `state`/`lever` имеют единственный шаг-1 дом — `Instrument`.
- snapshot↔domain (`InstrumentExternalSnapshot`, шаг 1) =
  идентичность + `externalStatus` + `externalLeverage`. Справочные
  sizing/rounding-поля (base/quote/settle, `lotSz`/`minSz`/`ctVal`/
  `ctMult`/`tickSz`) — транзиентны, персистентного дома в шаге 1 нет
  (INSTR-Q1).
- `InstrumentExternalRules` (sizing/rounding) — **отложена,
  структурно не менялась**; downstream (`trading-constraints.md`
  лимит плеча через `externalMaxLeverage`; `Service`/`SyncJob`) не
  трогался. Сорсинг rules-полей `externalState`/`externalMaxLeverage`/
  `Status` и их соотнесение с биржевыми полями `Instrument`
  (дубль/удаление) — вынесено в открытые вопросы INSTR-Q2 / INSTR-Q1.

### Изменённые доки GAPS_CLOSE_3 (8) + 1 новый прогресс

`domain/core/Instrument.md` (+`externalStatus`/`externalLeverage`,
персистентность, нота разграничения, связь INSTR-Q2),
`mapping/Instrument.md` (mapping-flow / «что персистится» / OKX-
таблица переписаны), `OkxInstrumentResponse.md` (`state`/`lever` → в
DTO), `mapping/InstrumentExternalRules.md` (`lever`/`state` сняты с
rules-маппинга, +§разграничение), `domain/other/InstrumentExternalRules.md`
(пометки в deferred-блок и §Status; структура не менялась),
`raw-exchange-dto-boundary.md` (+`InstrumentExternalSnapshot` в
перечень граничных), `lifecycles/Instrument.md` (`SYNC` + биржевые
поля), `open-questions.md` (INSTR-Q1 обновлён, INSTR-Q2 заведён).
Новый прогресс — `phase-1-step-1-gaps-close-3.md`.

### Open-questions

- **INSTR-Q2** (новый, из `GAPS_CLOSE_3`): валидация рабочего плеча
  (рабочее `leverage` ≤ конфигового максимума; превышение →
  инструмент не выпускается на биржу, `HOLD` как нарушение торгового
  правила). Внутри: роль `externalLeverage` как биржевого потолка;
  состояние/действие `HOLD` в lifecycle инструмента (в текущем
  онбординг-пути его нет). **Шаг 1 не блокирует.**
- **INSTR-Q1** приведён к новой модели: `state`/`lever` персистятся
  на `Instrument`, не на rules; открыто — персистентный дом
  sizing-полей и снапшот-концепция rules.
- Всего открыто **15** (было 14 + INSTR-Q2).

## Активные задачи

Шаг 1 Фазы 1 (поток рыночных данных): `TOOLING` → `DOCS_CHECK_1` →
`GAPS_CLOSE_1` → `DOCS_CHECK_2` → `GAPS_CLOSE_2` → `DOCS_CHECK_3` →
`GAPS_CLOSE_3` пройдены; активна — решение по следующему под-шагу
(`DOCS_CHECK_4` или `CODE`). Прочих активных задач нет. Прогресс-
файлы: `phase-1-step-1-docs-check-1.md`, `…-gaps-close-1.md`,
`…-docs-check-2.md`, `…-gaps-close-2.md`, `…-docs-check-3.md`,
`…-gaps-close-3.md`.

## Текущий фронтир / следующее действие

- **Развилка под-шага.** `GAPS_CLOSE_3` + подтверждающая проверка
  затронутой области пройдены чисто. Варианты: (1) `DOCS_CHECK_4` —
  полный сквозной прогон `concept-review` для финальной уверенности
  перед кодом; (2) `CODE` — концепция шага 1 проработана, перейти к
  написанию кода с синхронизацией доков. По заданию `GAPS_CLOSE_3` к
  `CODE` не переходили; выбор за пользователем.
- **На что смотреть при `DOCS_CHECK_4`** (особый фокус — то, что
  менялось в `GAPS_CLOSE_3`): граничная модель снапшота инструмента
  (`Instrument`/`mapping/Instrument`/`OkxInstrumentResponse`/
  `raw-exchange-dto-boundary`) — состав `InstrumentExternalSnapshot`
  = идентичность + `externalStatus` + `externalLeverage`; единый
  шаг-1 дом `state`/`lever`; различение `leverage` (рабочее) ↔
  `externalLeverage` (биржевое). Остаток rules-подсистемы помечен
  INSTR-Q2/INSTR-Q1 (не несогласованность, открытый вопрос).
- Тулинг `concept-review`/`reviewer` — в обкатке.

## Открытые общие вопросы

`open-questions.md`: DEAL-Q1, DEAL-Q2, PROC-Q1, RISK-Q1, TIME-Q1
(сужен), INSTR-Q1, **INSTR-Q2** (новый), ORCH-Q1, ENUM-Q1, CMD-Q1,
OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4 (разблокирован для шага 1), DEAL-Q3 —
все 15 открыты. Шаг 1 не блокирует ни один (INSTR-Q1/INSTR-Q2/
ORCH-Q1 — отложенные детали, не гейты).

## Что в работе

- Шаг 1 Фазы 1: `GAPS_CLOSE_3` пройден + подтверждающая проверка;
  следующее — `DOCS_CHECK_4` либо `CODE` (решение пользователя).
  Project Knowledge требует обновления: последний снапшот теперь
  **`snapshot-v19`** (заменяет v18 в префлайте). Затронуты `docs/`
  (граничная модель снапшота инструмента — 7 доков),
  `open-questions.md` (+INSTR-Q2, INSTR-Q1 обновлён), `phase-1.md`
  (статус), прогресс (`…-gaps-close-3.md`).
