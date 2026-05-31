# Instrument lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит онбординг инструмента (`Instrument`)
в шаге 1 и кто ими управляет.

Структура модели — `docs/models/domain/core/Instrument.md`.

## Охват

Материализован **только онбординг-путь шага 1**:
`CREATED → SYNC → CANDLES_LOADING → ACTIVE`. Периферийные статусы
(`HOLD`, `ERROR`-recovery, повторный онбординг, `CLOSED`) для
шага 1 **отложены** — backlog п.9; здесь не описываются.

## Кто управляет

Онбординг инструмента идёт внутри процесса загрузки свечей
(`docs/processes/candle-loading.md`):

- синхронизацию спецификации (`SYNC`) обеспечивает запрос
  инструмента у биржи через `docs/components/IntegrationService.md`
  (результат — транзиентный `InstrumentExternalSnapshot`, см.
  `docs/models/mapping/Instrument.md`);
- готовность свечных данных (`CANDLES_LOADING` → `ACTIVE`) ведут
  группы свечей инструмента под управлением
  `docs/components/CandleJob.md` (`docs/lifecycles/CandleGroup.md`).

Конкретный компонент, владеющий записью `Instrument.Status` и
координирующий её с `CandleGroup.Status` (отдельный orchestrator +
handler'ы по образцу Deal, или иной механизм), — открытый вопрос
**ORCH-Q1** (`.claude/work/questions/open-questions.md`); семантика
переходов и координации зафиксирована здесь.

## `Instrument.Status` (онбординг-путь шага 1)

| Статус | В торговле | Смысл |
|---|---|---|
| `CREATED` | нет | Запись инструмента заведена; спецификация не синхронизирована, групп свечей нет. |
| `SYNC` | нет | Синхронизация спецификации инструмента с биржей (идентичность + биржевые `externalStatus`/`externalLeverage` из `InstrumentExternalSnapshot`). |
| `CANDLES_LOADING` | нет | Группы свечей созданы и грузят историю/докачивают/проверяют целостность; хотя бы одна группа ещё не `ACTIVE`. |
| `ACTIVE` | да | Спецификация синхронизирована и **все** группы свечей инструмента в `ACTIVE` (история покрыта и плотна). |

## Переходы и триггеры

```text
CREATED
  -> SYNC              (инструмент взят в онбординг; запрос спецификации у биржи)
SYNC
  -> CANDLES_LOADING   (спецификация синхронизирована; созданы CandleGroup по нужным таймфреймам, запущен BACKFILL)
CANDLES_LOADING
  -> ACTIVE            (все CandleGroup инструмента достигли ACTIVE)
```

- `CREATED → SYNC`: инструмент взят в онбординг; `IntegrationService`
  тянет спецификацию (идентичность + биржевые `externalStatus`/
  `externalLeverage` → domain, справочные sizing-поля транзиентно;
  `docs/models/mapping/Instrument.md`).
- `SYNC → CANDLES_LOADING`: спецификация получена; для нужных
  таймфреймов созданы `CandleGroup`, `CandleJob` начал `BACKFILL`.
- `CANDLES_LOADING → ACTIVE`: достигнута готовность свечных данных
  (см. координацию ниже).

## Координация `Instrument.Status` ↔ `CandleGroup.Status`

Готовность инструмента определяется готовностью его групп свечей
(`docs/lifecycles/CandleGroup.md`):

- инструмент `ACTIVE` ⟺ **все** его `CandleGroup` в `ACTIVE`
  (покрытие до планового горизонта подтверждено, ряд плотен по
  count);
- пока хотя бы одна группа в рабочем статусе загрузки
  (`BACKFILL`/`SYNC`/`CHECK`/`REPAIR`) — инструмент остаётся в
  `CANDLES_LOADING`;
- плановый горизонт истории — `Instrument.plannedCandleStartDate`
  (общий для всех таймфреймов инструмента; per-ТФ фактические
  границы — `actualFirstUtcMillis`/`actualLastUtcMillis` на
  `CandleGroup`).

Готовность данных для активации **стратегии** — отдельный, более
поздний слой (`docs/processes/market-data-calculation.md`
§«Активация стратегии и готовность данных»); здесь — готовность
самого **инструмента**.

## Связи

- Модель — `docs/models/domain/core/Instrument.md`.
- Lifecycle групп свечей — `docs/lifecycles/CandleGroup.md`.
- Процесс / оркестрация / производитель свечей —
  `docs/processes/candle-loading.md`, `docs/components/CandleJob.md`.
- Граница спецификации — `docs/models/mapping/Instrument.md`,
  `docs/components/IntegrationService.md`.
- Владелец оркестрации — открытый вопрос ORCH-Q1.
- Отложенные статусы и полный lifecycle — backlog п.9.
