# Snapshot v10

**Дата:** 2026-05-27.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез на дату).

## Состояние

С момента `snapshot-v9.md` (2026-05-27) выполнена и закрыта **миграция
6 торговых сущностей** в `docs/`. `docs/` из заготовок с `.gitkeep`
стал реально наполненным продуктовым слоем. Каркас классификации
применён в боевом режиме на большом материале.

## Что изменилось относительно v9

**Продуктовая документация (`docs/`) — впервые наполнена (24 файла):**
- `models/core/` (6): `BalanceContainer`, `Position`, `Order`,
  `AlgoOrder`, `Deal`, `Strategy`.
- `lifecycles/` (5): `Position`, `Order`, `AlgoOrder`, `Deal`,
  `Strategy`.
- `rules/` (5, сквозные): `raw-exchange-dto-boundary`,
  `no-partial-close`, `ack-not-runtime-truth`,
  `external-status-resolution`, `exchange-hold`.
- `client/okx/models/` (4) и `client/okx/rules/` (4) — OKX mapping
  для balance/position/order/algo. Каталог `docs/client/okx/`
  заведён в этой миграции.

**Решения (новые):**
- `.claude/decisions/cross-cutting-parking.md` — при миграции
  сущности создаётся только её владение; cross-cutting (компоненты,
  RVO, jobs, commands, calc/risk, anomaly, audit) паркуются как
  форвард-заметки, не материализуются из частичного обзора.
- `.claude/decisions/forward-notes-after-task-closure.md` — после
  закрытия задачи прогресс/tasks-файлы архивируются в
  `history/YYYY-MM-DD-<задача>/`, указатели на форвард-заметки
  переезжают в backlog.

**Правила/структура:**
- `.claude/rules/structure.md` — строки `work/progress/`,
  `work/questions/tasks/`, `work/history/` обновлены под механизм
  архивирования завершённых задач (подпапка в `history/`).

**Backlog:**
- Пункт «Миграция архивных торговых сущностей» закрыт (summary в
  `history/`). Снят преждевременный пункт «активно использовать
  `docs/components/`». Добавлены 10 пунктов cross-cutting миграций
  (Deal management, resolver/mapper, калькуляторы+RVO, risk-слой,
  расчёт индикаторов/market-data, аудит/finalization, anomaly/safety,
  Strategy enforcement/validator, Exchange, API-OKX) с указателями на
  архивные форвард-заметки.

**Открытые вопросы:**
- В `open-questions.md` — DEAL-Q1 (retry-state финализации сделки),
  DEAL-Q2 (недосчитанный `resultProfit` после исчерпания retry),
  перенесены из архивного `Deal.md` §15.
- BAL-Q8 (OKX balance endpoint) закрыт: верный путь
  `/api/v5/account/balance` зафиксирован в `okx-balance-mapping.md`.
  `balanceExternalSnapshot` был опечаткой (артефакт архивного
  replace `balance`→`balanceExternalSnapshot`); по запросу пользователя
  путь исправлен и в активных доках, и во всех архивных API-путях (7
  файлов). Остаточные не-path вхождения `balanceExternalSnapshot` в
  архиве (имена полей/переменных, проза) намеренно не тронуты.

**История:**
- `.claude/work/history/2026-05-27-миграция-торговых-сущностей.md` —
  summary; детальные артефакты (6 `progress-*` + 6 `tasks-*`) — в
  одноимённой подпапке.

## Текущая структура

См. `.claude/rules/structure.md`. Каталоги: добавлен
`docs/client/okx/{models,rules}/`; в `history/` появились подпапки
для детальных артефактов завершённых задач. Скиллы классификации не
менялись.

## Активные задачи

- Активных задач в `progress/` нет (миграция закрыта).
- В backlog — 10 cross-cutting миграций (запуск по мере приоритета).

## Открытые общие вопросы

- DEAL-Q1, DEAL-Q2 (продуктовые, финализация сделки). Остальные
  (Q1-Q4, NQ-A…NQ-H) закрыты ранее; история — в соответствующих
  decisions.

## Что в работе

- Каркас классификации держится целиком; добавлены
  `cross-cutting-parking` и `forward-notes-after-task-closure`.
- Следующие крупные шаги — cross-cutting миграции из backlog;
  основной источник для них — непокинутый `.claude-archive/` (модели
  + процессные доки Deal management / Calculation / Audit / context /
  api).
