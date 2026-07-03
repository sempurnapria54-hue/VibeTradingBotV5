# Snapshot v64

**Дата:** 2026-07-02.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — выполнен `SYNC_DOCS_FROM_CODE` шага 6:
продуктовые доки выровнены под as-built код (Stage 2/3-рефактор + фиксы ревью),
52 дока (46 изменено, 5 новых компонент-доков, 1 удалён). Аппрув `CODE` дан.**
Сменяет v63.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `CODE` аппрувнут → `SYNC_DOCS_FROM_CODE`
выполнен**; остаётся пост-хок концепт-гейт §6a → `DONE`. Шаги 7-11 `HOLD`. Ветка
`claude-audit`.

`HEAD = cf1e257 (ROADMAP 1-6-4 CODE_5)` — сюда закоммичены: green Stage 2/3, дельта
фиксов ревью (30/32 находок), снапшот v63, перф-запись в backlog. **SYNC-доки —
staged поверх HEAD, не закоммичено: 52 файла, +532 / −303** (46 изменено, 5 новых,
1 удалён).

## Как сюда пришли (эта сессия)

Пользователь аппрувнул `CODE` и запустил `SYNC_DOCS_FROM_CODE` (фокус `divergence`,
направление docs←code: код утверждён = истина). Выравнивание docs под as-built
после Stage 2/3-рефактора и фиксов ревью.

## Что сделано (SYNC)

**Метод:** оркестрация 6 реконсиляторов (5 по областям + 1 на остаток),
непересекающиеся наборы доков, единый as-built брифинг; CC верифицировал сам
(grep-sweep + спот-чек + проверка ссылок).

**Дивергенции по классам:**
- **remove:** `docs/components/ServiceCommandFactory.md` УДАЛЁН (класс распилен на
  `DealFinalizationCommandFactory` + action-executor'ы). Вычищены ссылки на
  `StrategyPositionAction`/`CLOSE_FULL`/`PriceRoundingPolicy`/
  `POSITION_CLOSE_REFERENCE_PRICE`/`EXECUTE_KILL_SWITCH` и удалённые методы
  (`isNotLive`/`toPartiallyComplete`/`isTerminal`/`hasExternalType`).
- **add:** 5 новых компонент-доков — `StrategyActionExecutor.md`,
  `StrategyActionOrchestrator.md`, `CreateOrderActionExecutor.md`,
  `CreateAlgoOrderActionExecutor.md`, `DealFinalizationCommandFactory.md`.
- **change (~40 доков):** эмиссия команд `ServiceCommandFactory` →
  `StrategyActionOrchestrator`+per-type `StrategyActionExecutor` (action) /
  `DealFinalizationCommandFactory` (финализация); `OrchestratorPassLock` →
  `JobExecutionGuard`; 3-метод контракт handler'ов; B2 (переход в MANAGING по
  active-like защите `Order.hasActiveAttachedProtection()`); `foreignLiveRisk` в
  фасад `DealFsmSupport`; kill-switch package-move (`domain.command.action`) +
  реактивность (не команда, через `HoldSignal`→`SafetyHoldCoordinator`); HOLD-Q1
  (снят квалификатор severity в `controlled-exchange-exceptions.md`); M2/M3/M4;
  `IndicatorParams` ручной резолв подтипа (StrategyJsonConverter); Strategy
  валидация 12→11 + таблица `strategy_position_actions` убрана.
- **Бонус (предсуществующие расхождения, закрытые кодом-истиной):**
  `IndicatorService.md` `getLatestAtr`→реальные `getLatestValue`/`getPreviousValue`;
  выправлено L4/L3-противоречие `risk-creating-entry-protection.md` ↔
  `instrument-hold.md` (бесстоповая позиция = L3, controlled-violation доминирует до L4).

## Верификация SYNC

- Сплошной grep по устаревшим символам: остались **только** в историко-корректных
  файлах («тип EXECUTE_KILL_SWITCH убран», «обобщает прежние DealActionPlanner+
  ServiceCommandFactory», «был реализован как OrchestratorPassLock», «CLOSE_FULL —
  избыточный подтип»).
- Битых path-ссылок на удалённый `ServiceCommandFactory.md` нет; ссылки на 5 новых
  доков резолвятся (7/8/5/1/1 упоминаний).
- 5 новых компонент-доков спот-проверены на точность к коду (стадии executor'ов,
  гейт повтора, сборка дерева `Condition`, type→команда маппинг) — совпадают.
- **Код не тронут** (агенты docs-only): дерево зелёное (compile/test-compile/boot
  как в v63).

## Loose ends

- SYNC-доки в staging для ревью/коммита в IDEA (CC не коммитит).
- Часть Stage 2/3-файлов кода могла оставаться unstaged ранее — вошла в коммит CODE_5.
- `strategy-examples/trend-following-ema.json` — всё ещё `actionKind: POSITION` /
  `CLOSE_FULL` (не грузится кодом; Stage 3 хвост — пример переписать на
  exit-as-transition, отдельно от doc-sync).
- `tradingbot.iml`, `vault.hcl` — untracked, не трогать.

## Что дальше

1. **Пост-хок концепт-гейт §6a** — `concept-review` концепт-инкрементов по
   пост-sync докам: placeholder-PnL `MarkDealClosed` (ZERO; реальный PnL — шаг 7),
   retry re-arm RETRY_PENDING→производная стадия, частичный unique-index
   `uk_deal_active_instrument`, set-leverage inline-представление, D-B3
   representation, дизайн реактивных холдов + слоистость FSM. Чисто → `DONE`.
2. Жёсткие гейты `DONE` (D-B3 / D-M1) — оба built.

## Открытые вопросы

Без изменений (HOLD-Q1 закрыт ранее). Ни один не гейтит §6a/`DONE`.

## Среда

Тулчейн/инфра — без изменений (см. v62/v63): `mvn` 3.9.11 из wrapper-dist,
`JAVA_HOME=~/.jdks/corretto-25`; boot — `SPRING_PROFILES_ACTIVE=test`, джобы
`*_ENABLED=false`, `VAULT_TOKEN`/`VAULT_URI` из `.env.vault.test.local`; docker
postgres:16 (5441 test), vault (8200).

## После коммита

Обновить PK (v64 заменяет v63).
