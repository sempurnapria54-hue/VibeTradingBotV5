# Прогресс: миграция Position

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности Position и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/domain/models/Position.md` +
`.../mapping/okx/OKX_Position_mapping.md`.

`Position` несёт собственную статусную FSM (`ACTIVE`/`CLOSED`/
`ERROR`) → разнесена на модель + lifecycle.

## Созданные / изменённые файлы

- `docs/models/core/Position.md` — модель (создан).
- `docs/lifecycles/Position.md` — lifecycle (создан).
- `docs/rules/no-partial-close.md` — сквозное правило (создан).
- `docs/rules/ack-not-runtime-truth.md` — сквозное правило (создан).
- `docs/client/okx/models/OkxPositionResponse.md` — поля OKX (создан).
- `docs/client/okx/rules/okx-position-mapping.md` — OKX mapping
  (создан).
- `.claude/work/questions/tasks/position.md` — форвард-заметки
  (создан).

## Отчёт по фрагментам

Область у всех — **продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Назначение `Position` (live-risk runtime-сущность в Deal) | модель | `Position.md` §Назначение |
| Ф2 | Атрибуты `Position` | модель | `Position.md` §Структура |
| Ф3 | Инварианты принадлежности (dealId, ≤1 Position/Deal, не хранит instrumentId/exchangeId/…) | модель | `Position.md` §Инварианты |
| Ф4 | `externalId` = OKX `posId`, не stable id | модель + правило биржи | Доменно → `Position.md`; OKX-связь → `okx-position-mapping.md` |
| Ф5 | Енум `Direction` (LONG/SHORT, NET не используется) | модель | `Position.md` §Енумы |
| Ф6 | Енум `Status` (ACTIVE/CLOSED/ERROR), почему минимальный набор | lifecycle | `Position.md` §Енумы (перечень) + `Position lifecycle` §Статусы |
| Ф7 | Енум `CloseReason` + разделение с `Deal.CloseReason` | модель | `Position.md` §Енумы; write-once → lifecycle |
| Ф8 | Формула live risk (`hasLiveRisk`) | модель (правило сущности) | `Position.md` §Live risk (первоисточник — `rule-source-of-truth.md`) |
| Ф9 | Active/Closed/Live-risk semantics, `externalSize==0` stays ACTIVE | lifecycle | `Position lifecycle` §Status vs live risk + §Переходы |
| Ф10 | `PositionExternalSnapshot` | модель (раздел, снапшот) | `Position.md` §PositionExternalSnapshot (по `model-granularity.md`) |
| Ф11 | `PositionStatusResolver` + `PositionStatusResolveResult` | компонент + RVO | Логика → `Position lifecycle`; компонент/RVO отложены → POS-Q2 |
| Ф12 | `REFRESH_POSITION` policy + executor-правила | компонент + команда | Доменные правила → `Position lifecycle`; компоненты отложены → POS-Q1 |
| Ф13 | Легитимное окно появления позиции | lifecycle | `Position lifecycle` §Легитимное окно |
| Ф14 | `CLOSE_POSITION` semantics (полное закрытие, minimal checks, RiskValidator не вызывается) | команда + сквозное правило | Partial-ban → `no-partial-close.md`; ACK → `ack-not-runtime-truth.md`; command-детали → POS-Q1; RiskValidator → POS-Q3 |
| Ф15 | Partial close запрещён; partial exit через reduce-only | сквозное правило | `docs/rules/no-partial-close.md` (`rule-source-of-truth.md`: сквозное) |
| Ф16 | ACK не runtime truth; подтверждение через REFRESH | сквозное правило | `docs/rules/ack-not-runtime-truth.md` (`rule-source-of-truth.md`: сквозное) |
| Ф17 | `Position` и fills/PnL (не хранит, не считает) | модель | Позитив → `Position.md` §Что не хранит; PnL-правило (Deal) → POS-Q5 |
| Ф18 | `Position` и `DealContext` (состав, ≤1 Position, ENTRY_SUBMITTED) | RVO (shared) | Инвариант ≤1 → модель/lifecycle; состав DealContext → POS-Q4 |
| Ф19 | Recovery-сценарий после рестарта | lifecycle (Position-часть) + Deal-flow | Position-правило → `Position lifecycle` §Recovery; полный контур → POS-Q6 |
| Ф20 | OKX endpoints (positions, close-position) | правило биржи | `okx-position-mapping.md` §Endpoints |
| Ф21 | OKX ClientService constants/policy (instType/mgnMode/posSide/ccy/autoCxl) | правило биржи | `okx-position-mapping.md` §ClientService constants |
| Ф22 | OKX response validation + invariant violation reaction | правило биржи | `okx-position-mapping.md` §Validation; ERROR-переход → `Position lifecycle` |
| Ф23 | OKX контракт snapshot/null/exception | правило биржи | `okx-position-mapping.md` §Контракт |
| Ф24 | OKX mapping fields | правило биржи | `okx-position-mapping.md` §Mapping fields |
| Ф25 | OKX direction mapping | правило биржи | `okx-position-mapping.md` §Direction; доменно → `Position.md` §Енумы |
| Ф26 | close-position request mapping + reason | правило биржи | `okx-position-mapping.md` §Close-position request / §Close reason |
| Ф27 | close-position ACK policy | правило биржи + сквозное | `okx-position-mapping.md` + `ack-not-runtime-truth.md` |
| Ф28 | OKX поля не в Position (availPos, bePx, tradeId, realizedPnl, …) | модель API биржи | `OkxPositionResponse.md` §Поля не в Position |
| Ф29 | `PositionMapper` (mapper-компонент) | компонент | Отложен → POS-Q2; существо → `okx-position-mapping.md` |
| Ф30 | `AnomalyJob` (active position без Deal) | компонент (shared safety) | Отложен → POS-Q7; ссылка в `Position lifecycle` |

## Итог по Position

- Размещено в `docs/`: 6 файлов (модель, lifecycle, 2 сквозных
  правила, 2 client/okx). Два сквозных правила переиспользуются
  Order/AlgoOrder/Deal.
- Отброшены/свёрнуты к позитиву: отрицательные перечни «не хранит»
  (Ф3, Ф17, Ф28) — позитив зафиксирован, чистые отрицания свёрнуты
  по `negative-statements-not-fixated.md`.
- В форвард-заметки: POS-Q1…Q7 (command-подсистема, resolver/mapper
  компоненты, RiskValidator, DealContext, Deal PnL, recovery,
  AnomalyJob).
- Продуктовых открытых вопросов по Position нет.
