# GAPS_CLOSE_8 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Как закрыты пробелы `DOCS_CHECK_8` шага 7 (в объёме решённого — узлы
1/3/4, выводимые находки, переименование уровней) и что осталось открытым.

## Контекст

Под-шаг `GAPS_CLOSE_8` материализует решения развилки «команда ↔
действие» (4 валидации пользователя,
`progress/phase-1-step-7-command-action-boundary.md`) и выводимые находки
`DOCS_CHECK_8`. Центральный носитель «почему» —
**`docs/decisions/command-action-boundary.md`** (новый).

## Закрыто

### Узел 1 (H1) — окно линковки bills

Границы окна — собственные поля **`Deal.billsWindowBegin` /
`billsWindowEnd`**, пишет наблюдатель факта (live-нога / нога 2
`REFRESH_POSITION_COMMAND`); `end` пуст → привязка ждёт; фолбэк
`Deal.modifiedAt` снят (смешение часовых доменов). Цена названа: после
терминала неподобранные bills не подберутся никогда. Правлено:
`Deal.md` (+2 поля, §«Окно линковки bills»), `DealCashFlow.md`
§Линковка, `RefreshBillsExecutor.md`, `RefreshPositionExecutor.md`,
`lifecycles/Position.md`, `Position.md` (core),
`mapping/PositionCloseResult.md`.

### Узел 3 (H7) — анкер попыток добывающих команд

Анкер — строка исполнения **`REFRESH_DEAL_CONTEXT_ACTION`** (вид SYSTEM)
в общей таблице `deal_action_states`; `DealFinalizationState` +
lifecycle + `DealFinalizationCommandFactory.md` **упразднены** (git rm),
взамен — `docs/components/SystemActionExecutor.md`. Топология V2:
nullable `strategy_action_id`, `action_kind`, `system_action_type`,
target-колонки, **частичные уникальные ключи живых исполнений** (строка =
исполнение; заодно чинится дефект ключа шага 4 — второму исполнению узла
грида некуда было лечь). **Обязательное условие проведено:** handler'ы
(`ExitPendingHandler`, `EntrySubmittedHandler`, `ErrorHandler`,
`ManagingHandler`) добывающие `REFRESH_*` эмитят только звеньями
действия; cleanup — напрямую, без анкера. Правлено: `DealActionState.md`
(переработан) + lifecycle (матрица SYSTEM), `ServiceCommand.md` (один
анкер), `command-lifecycle.md` (критерий атомарности — три клаузы),
`ServiceCommandExecutor.md`, `RetryPolicyService.md` («предел — по
команде, счётчик — по исполнению» + находка о пустой секции конфига),
`DealContext.md`/`DealContextService.md` (одна коллекция; служебная
сборка — не действие), `fsm-execution-layering.md` (слой
`SystemActionExecutor`; статусные рёбра пишут звенья),
`DealStateMachine.md`, `DealOrchestratorJob.md`, `deal-management.md`,
`risk-validator-scope.md`, `idempotency-via-unique.md` (NULL-семантика,
«уникальность среди живых»), `persistence-representation.md` (операнды
ключа — из jsonb в колонки), ревизия-ноты в
`deal-finalization-state-materialization.md` и
`deal-action-state-materialization.md`.

### Узел 4 (H8) — исход финализации при пустых фактах

Вариант (а): `FINALIZE_DEAL_EXIT_COMMAND` эмитится по терминальному
исходу добычи и **не завершается без числа**; исчерпание бюджета →
ошибочная тропа + **холд инструмента** (действующее правило
управление-сайда, довод отладки + условие пересмотра —
`instrument-hold.md` §«Серия неудач», новый подраздел). Асимметрия
аварийной тропы сохранена. Правлено: `pnl-finalization-mechanics.md` §5a
(анкер, реакция), `FinalizeDealExitExecutor.md`, `MarkDeal*Executor.md`
(анкеры + идемпотентность по фактам `Deal`), `lifecycles/Deal.md`
§Терминальный контракт, `ExitPendingHandler.md` §Выходные проверки.

### Валидация 4 — окно консолидации входа

`ENTRY_FINALIZED` пишет **само звено** в одной транзакции со своим
завершением (второй экземпляр паттерна N7; обобщённая клауза — decision
§5). Правлено: `FinalizeDealEntryExecutor.md` (переработан),
`EntrySubmittedHandler.md` (handler гейтит эмиссию, не двигает статус),
`fsm-execution-layering.md` §Handler.

### Переименование уровней (В2.2/В3.3)

Свип `_COMMAND` по `docs/` — regex с word boundaries, 78 файлов;
двойных суффиксов и остатков нет (проверено грепом); снятые/исторические
имена (`REFRESH_FILLS`, `EXECUTE_KILL_SWITCH`, `REFRESH_POSITIONS_HISTORY`,
bulk) намеренно не тронуты — их нет в целевом реестре. Групповые маски в
прозе (`REFRESH_*`, `MARK_*`) — короткие. `StrategyActionType` →
`CREATE_ACTION`/`REPLACE_ACTION`/`CANCEL_ACTION` в каноне (`Strategy.md`
§Действия) с нотой о миграции значений; правило — `naming.md`
§«Разведение уровней» (внесено ранее). Код/миграция/пример JSON —
`backlog.md` §Шаг 7 (CODE).

### Выводимые находки

- **H17** — «net из `PositionCloseResultExternalSnapshot`» приведено к
  «net из `Position.externalRealizedProfit`» в первоисточнике (`Deal.md`
  §Итоговый PnL) и `MarkDealClosedExecutor.md`.
- **H19** — список наследников `Auditable.md` §Связи согласован с
  §Назначение (убран `InstrumentExternalRules`, добавлены
  `Position`/`Deal`).
- **H20** — закрыт снятием носителя: таблица `deal_finalization_states`
  упразднена вместе с ключом, имя которого расходилось.
- **H21** — полная schema-дельта шага 7 собрана в
  `pnl-finalization-mechanics.md` §Следствия (всё помечено `ALTER`/новое);
  `Deal.md` §Персистентность помечает колонки шага 7 как `ALTER`.
- **H23** — `openAvgPx` выведен из снапшота и маппинга (потребителя нет,
  H22-правило); `Position.externalAverageEntryPrice` пишет только
  live-нога — двуписьменность снята (`mapping/PositionCloseResult.md`,
  `Position.md`).
- **H25** — вопрос файла `mapping/PositionCloseResult.md` сведён к одному
  («Как положение закрытой позиции источника ложится на `Position`?»).

## Оставлено открытым (ожидаемо)

- Узлы **2** (знаменатель `R`), **5** (cross-ccy), **6** (H12/H13),
  **7** (свежесть ключа группы), **8** (чувствительность сверки) —
  решений пользователя нет.
- **§6.4 проработки** — метка подтверждения звена добычи: гейтится узлом
  7; правка по добыче ушла заведомо неполной (помечено в
  `SystemActionExecutor.md` и decision §Отложено).
- **§5.2** — пустая секция `service-command-retry` (NPE) — дефект кода,
  пункт `CODE` в `backlog.md`.
- H16, H18, H22, H24, H26, H27 — прочие находки `DOCS_CHECK_8` вне объёма
  этой правки (не гейтят / зависимые / у других владельцев).

## Сводка

Новые файлы: `docs/decisions/command-action-boundary.md`,
`docs/components/SystemActionExecutor.md`. Удалены:
`docs/models/domain/other/DealFinalizationState.md`,
`docs/lifecycles/DealFinalizationState.md`,
`docs/components/DealFinalizationCommandFactory.md`. Правлено
содержательно ~30 доков + суффиксный свип по 78. Рабочие файлы:
`backlog.md` §Шаг 7 (CODE-пункт границы переписан, окно, retry-конфиг,
отложенная политика очистки), хроника, леджер (валидация 4), дерево.
