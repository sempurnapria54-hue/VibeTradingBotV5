# Snapshot v12

**Дата:** 2026-05-28.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после закрытия прохода 2 миграции
архивных процессов).

## Состояние

Миграция архивных процессов (8 доков `Audit/`/`Calculation/`/`Deal
management/`) **завершена и закрыта**. Проход 2 (создание файлов в
`docs/`) выполнен; архив `.claude-archive/2026-05-21/` после прохода 2
больше не используется как источник истины (по
`.claude/decisions/migration-triad.md`).

## Что изменилось относительно v11

**Создано в `docs/` (86 новых файлов + 5 расширений правил):**
- `processes/` (4): `market-data-calculation`, `deal-management`
  (композиционный корень), `strategy-action-calculation`,
  `risk-evaluation`.
- `components/` (53): market-data jobs/services, калькуляторы, risk-layer
  (`RiskValidator`/`RiskBlockResolver`), command-layer (`ServiceCommand*`,
  `ClientService`, `RetryPolicyService`, 14 executor'ов), resolver'ы,
  `AnomalyJob`, `KillSwitchExecutor`, orchestration (`EntryScannerJob`,
  `DealOpeningService`, `DealOrchestratorJob`, `DealContextService`),
  `DealStateMachine`, `StrategyConditionEvaluator`, 7 FSM handlers.
- `components/models/` RVO (15): Market/Calc/Risk/Command/Deal RVO.
- `models/other/` (4): `InstrumentExternalRules`, `IndicatorValue`,
  `MarketStructure` (+ `MarketPriceLevel`), `MarketPhase`.
- `rules/` новые (7): `market-data-freshness`, `risk-validator-scope`,
  `command-lifecycle`, `runtime-error-classification`,
  `controlled-exchange-exceptions`, `trading-constraints`,
  `audit-not-runtime-source`. Расширены 5: `ack-not-runtime-truth`,
  `no-partial-close`, `external-status-resolution`, `exchange-hold`
  (DISABLED), `raw-exchange-dto-boundary`.
- `client/okx/rules/` новые (3): `okx-timeframe-mapping`,
  `okx-instrument-mapping`, `okx-market-price-data-mapping`. OKX order/
  algo/position/balance mapping и статусные модели/lifecycles были уже
  полны после model-кластера — не дублировались.

**Новые/дополненные open-questions** (`open-questions.md`): TIME-Q1,
RISK-Q1, ENUM-Q1, CMD-Q1, DEAL-Q3 (все самодостаточны — формулировка,
цитаты архива, варианты, обратные ссылки); PROC-Q1 дополнен цитатами.

**Backlog:** зонтичный пункт активной миграции снят; cross-cutting
пункты 1, 3, 4, 5 закрыты; 2, 6, 7, 8 обновлены (что мигрировано/осталось);
9, 10 уточнены. Указатели на форвард-заметки переведены на подпапки
`history/`.

**История:** прогресс- и tasks-файлы (10 + 8) перемещены в
`.claude/work/history/2026-05-28-миграция-процессов/`; summary —
`.claude/work/history/2026-05-28-миграция-процессов.md`.

## Текущая структура

См. `.claude/rules/structure.md`. Каталоги не менялись. `docs/processes/`,
`docs/components/`, `docs/components/models/` впервые наполнены.
`progress/` и `questions/tasks/` сейчас пусты (наполняются при старте
новых задач).

## Активные задачи

Нет активных задач исполнения. Следующий шаг — на выбор пользователя из
cross-cutting пунктов backlog: п.2 (mappers/checker), п.6 (аудит +
финализация PnL + TradeFill), п.7 (ReconciliationJob / kill-switch flow /
TradeRuleValidator), п.8 (валидатор стратегии + API examples), п.9
(Exchange/Instrument модели), п.10 (API-кластер OKX).

## Открытые общие вопросы

`open-questions.md`: PROC-Q1, RISK-Q1, ENUM-Q1, DEAL-Q3, TIME-Q1, CMD-Q1
(из миграции процессов); DEAL-Q1, DEAL-Q2 (продуктовая финализация
`Deal`). Остальные закрыты ранее; история — в соответствующих decisions.

## Что в работе

- Ничего в активной работе. Project Knowledge (claude.ai) требует
  обновления: добавлен `snapshot-v12.md`, изменены `backlog.md`,
  `open-questions.md`; PK должен указывать на последний snapshot.
