# Instrument lifecycle

## На какой вопрос отвечает этот файл

Через какие состояния проходит онбординг инструмента (`Instrument`) в
шаге 1.

Структура модели — `docs/models/domain/core/Instrument.md`.

## Охват

Материализован **только онбординг-путь шага 1**:
`CREATED → SYNC → CANDLES_LOADING → ACTIVE`. Периферийные статусы
(онбординговый `HOLD`, `ERROR`-recovery, повторный онбординг, `CLOSED`) для
онбординга **отложены** и здесь не описываются.

**Safety-статусы вне этого охвата.** `TRADE_BLOCKED` (холд с kill-switch) и
`ENTRY_BLOCKED` (мягкий запрет новых входов) — не онбординговые: их пишет
реактивный/safety-контур, а не онбординг, и снимаются они **вручную** в
`ACTIVE`. **Множества входа у них разные**:

```text
ACTIVE            -> ENTRY_BLOCKED   (мягкая блокировка — только из ACTIVE)
любой статус      -> TRADE_BLOCKED   (авария может застать в любом статусе)
любой статус      -> CLOSED | ERROR
ENTRY_BLOCKED     -> TRADE_BLOCKED   (эскалация мягкого в полный)
```

Онбординговые статусы (`CREATED`/`SYNC`/`CANDLES_LOADING`) мягкой
блокировкой **не затираются** — входов из них и так нет. Жёсткая блокировка
их затереть может: авария приоритетнее онбординга. Отсюда открытый вопрос: в какой статус возвращает ручное снятие, если инструмент был
заблокирован из онбординга (сегодня снятие ведёт в `ACTIVE`).
Рёбра и семантика — `docs/rules/instrument-hold.md` и
`docs/models/domain/core/Instrument.md`; координация всех троп в
одном lifecycle — отложено.

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
handler'ы по образцу Deal, или иной механизм), — **открытый вопрос**; семантика
переходов и координации зафиксирована здесь.

## `Instrument.Status` (онбординг-путь шага 1)

| Статус | В торговле | Смысл |
|---|---|---|
| `CREATED` | нет | Запись инструмента заведена; спецификация не синхронизирована, групп свечей нет. |
| `SYNC` | нет | Синхронизация спецификации инструмента с биржей (идентичность + биржевые `externalStatus`/`externalLeverage` + **валюты base/quote/settle** из `InstrumentExternalSnapshot`). |
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
  - **Валюты инструмента пишет эта тропа** (с шага 7):
    `externalSettlementCurrency` / `externalBaseCurrency` /
    `externalQuoteCurrency` заполняются здесь же, из того же ответа
    `/public/instruments`. Значения считаются неизменными ⇒ второго писателя и
    периодического обновления у них нет; ежечасный
    `InstrumentExternalRulesSyncJob` валют не касается.
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
поздний слой (`docs/processes/market-data-calculation.md`); здесь — готовность
самого **инструмента**.

## Связи

- Модель — `docs/models/domain/core/Instrument.md`.
- Lifecycle групп свечей — `docs/lifecycles/CandleGroup.md`.
- Процесс / оркестрация / производитель свечей —
  `docs/processes/candle-loading.md`, `docs/components/CandleJob.md`.
- Граница спецификации — `docs/models/mapping/Instrument.md`,
  `docs/components/IntegrationService.md`.
- Владелец оркестрации не назначен — вопрос открыт.
- Остальные статусы и полный lifecycle — отложены.
