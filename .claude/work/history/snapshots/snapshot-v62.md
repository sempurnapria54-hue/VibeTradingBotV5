# Snapshot v62

**Дата:** 2026-07-02.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — две вещи поверх v61: (1) брошенный
на середине рефактор исполнения (Stage 2/3) доведён до зелёного (компиляция +
test-compile + live boot); (2) проведено агентское ревью всей кодовой базы
(420 файлов, 20 подсистем) — 32 находки (2 blocker, 4 major, 26 minor),
правки НЕ вносились.** Сменяет v61.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `CODE`** (Stage 1+2 построены,
Stage 3 частично; дерево **зелёное**), шаги 7-11 `HOLD`. Ветка `claude-audit`.

Дельта поверх `HEAD = 0eb1a50 (ROADMAP 1-6-4 CODE_3)`: **54 файла,
+1750 / −1004** (src java — 45 файлов, +1151 / −918). Частично staged, частично
нет (CC не коммитит; ручной стейджинг в IDEA).

## Как сюда пришли (эта сессия)

**Вход:** v61 зафиксировал шаг 6 CODE как компилируемый/бутающийся, но дерево
успело уйти вперёд — начатый рефактор исполнения (Stage 2/3) был брошен на
середине и **не компилировался**.

1. **Stage 2/3 доведены до зелёного.** Компайл-блокер: `StrategyPositionAction`
   удалён по слоям (Stage 3 «снять вырожденный CLOSE_FULL»; выход = условие-перехода
   `MANAGING → EXIT_PENDING`), но `PriceCalculator` его ещё держал. Правки (4 файла):
   - `PriceCalculator.java` — убран импорт + `case StrategyPositionAction` + осиротевший
     `closeReferencePrice()`;
   - `StrategyPricePurpose.java` — удалена осиротевшая `POSITION_CLOSE_REFERENCE_PRICE`;
   - `CalculatedSize.java` / `StrategyCreateRequestValidator.java` — стейл-javadoc
     (ссылки на `StrategyPositionAction`/`CLOSE_FULL`) выровнены под Stage 3-модель.
2. **Агентское ревью всей кодовой базы** (пользователь выбрал полный охват, не дельту).

## Stage 2/3 — что построено (было форвардом в v61, теперь в дереве)

- **Stage 2 (построен):** `DealActionPlanner` + `ServiceCommandFactory` удалены →
  пакет `domain.deal.action`: интерфейс `StrategyActionExecutor` + per-type
  `CreateOrderActionExecutor`/`CreateAlgoOrderActionExecutor` + диспетчер
  `StrategyActionOrchestrator` (гейтит повтор RETRY_PENDING, маршрутизирует по
  `supports(action)`); `DealFinalizationCommandFactory` выделен отдельно.
- **Stage 3 (частично):** `StrategyPositionAction` вычищен по api/persistence/
  domain/mapper; `StrategyActionType` = CREATE/REPLACE/CANCEL (полного закрытия
  как действия нет); `StrategyActionApiModel` без POSITION-сабтипа.

## Верификация зелёного (в самой среде)

- `mvn -o -DskipTests compile` (+showDeprecation/Warnings) → **EXIT=0**, чисто.
- `mvn -o -DskipTests test-compile` (64 тест-файла) → **EXIT=0**.
- **Boot:** test-профиль, все джобы `*_ENABLED=false`, Vault → **`Started
  TradingBotApplication in 6.695s`**, datasource из Vault (`:5441`), контекст со
  Stage 2-бинами связан без DI-цикла, Tomcat 8080.
- Грепом: ссылок на `StrategyPositionAction` в `src/` нет; тесты не ссылаются на
  удалённые Stage 2-классы (`ServiceCommandFactory`/`DealActionPlanner`/
  `OrchestratorPassLock`).

## Агентское ревью (детали — `.claude/notes/2026-07-02-code-review-full-codebase.md`)

Фан-аут 20 ревьюеров по подсистемам (эффорт `high`, читают `codestyle`/`tech-radar`)
→ адверсариальная верификация каждой находки (скептик, refute при сомнении).
Покрытие 100% (3 перезапуска из-за инфра-обрывов; `deal` дожат разбиением на 3).
**Правки НЕ вносились — чистое ревью.**

**32 выжило (2 blocker · 4 major · 26 minor), 3 отсеяно.**

**🔴 BLOCKER (оба в ядре шага 6, связаны — защита позиции):**
- **B1** `deal/action/CreateAlgoOrderActionExecutor.java:68` — algo-`Condition`
  строится только по `conditionType`; calculated SL/TP/trailing выбрасываются →
  стоп-лосс уходит на биржу **без триггера** (не сработает).
- **B2** `deal/handler/EntryFinalizedHandler.java:128` — переход в MANAGING по
  **наличию** приложенного algo, без проверки активности → отклонённый биржей
  стоп не ловится → бесстоповая live-позиция в MANAGING (должна → ERROR/
  `markErrorStopless`). Фикс B2 «оживит» неиспользуемые `AttachedAlgoOrder.
  isActiveLike()/isTerminal()`.

**🟠 MAJOR (все correctness):**
- **M1** `StrategyDetailApiModel.java:41` — вложенный `@Valid` не каскадит в
  элементы списка → шаги минуют create-валидацию (нужен `List<@Valid …>`).
- **M2** `GlobalExceptionHandler.java:71` — catch-all глотает
  `ResponseStatusException` → 400/422 становятся 500.
- **M3** `ServiceCommandExecutor.java:64` — ACK-реджект **возвращается**, не
  бросается → retry/FAILED-учёт минуется → сделка пере-сабмитит каждый тик.
- **M4** `MarketPhaseService.java:72` — `buildContext` без `.price(...)` →
  PRICE-операнды фазовых правил всегда false → неверная фаза/выбор стратегии.

**Minor (26):** мёртвый код (§Неиспользуемый: `refreshFillsCommand`, два
`findCheckpoint`+repo-запросы, `PriceRoundingPolicy` enum, `isNotLive`×2,
`toPartiallyComplete`, `AttachedAlgoOrder`-предикаты), избыточные same-name
`@Mapping` (×7), `CONST.equals(var)`, примитив на контракте, enum вне домена,
стейл-javadoc (×3), overflow бэкоффа при maxAttempts≥64, и т.д.

**Отсеяно (3):** `RiskValidator:220` (null-риск — намеренный opt-in),
`CalculationContextFactory:104` (посылка неверна), `SizeMode.FULL_CLOSE`
(задокументировано).

## Loose ends

- **`strategy-examples/trend-following-ema.json`** ещё держит `actionKind: POSITION`
  / `actionType: CLOSE_FULL` — кодом **не грузится** (не блокер compile/boot), но стал
  невалидным против Stage 3-модели. Переписать выход как условие-перехода — хвост Stage 3.
- **Часть Stage 2/3-файлов unstaged** (`StrategyActionApiModel`, `CalculatedStrategyAction`,
  `SizeCalculator`, `StrategyActionType`, `StrategyMapper` — ` M`). Дожать стейджинг в IDEA.
- **Progress `phase-1-step-6-code.md` устарел:** описывает `OrchestratorPassLock`/
  `DealActionPlanner`/`ServiceCommandFactory`/kill-switch-как-команду и Stage 2 как
  форвард. Выровнять на `SYNC`.
- `tradingbot.iml`, `vault.hcl` — untracked, не трогать.

## Что дальше (хвост шага 6)

1. **Разбор находок ревью** — начать с блокеров (B1+B2 вместе), затем M3 → M4 →
   M1+M2 → minor пакетно. Порядок и что чинить — за пользователем.
2. **Финальный аппрув `CODE`** (scope-complete на уровне Stage 1+2) — за пользователем.
3. **Stage 3 хвост** — transition-conditions в модели стратегии + пример JSON.
4. **Большой `SYNC_DOCS_FROM_CODE`** (docs←code): компонент-доки handler'ов +
   `DealStateMachine.md`, action-executor'ы/оркестратор, kill-switch, выравнивание
   `controlled-exchange-exceptions.md`/`exchange-hold.md`/`instrument-hold.md`,
   §6a-концепт-инкременты → пост-хок концепт-гейт → `DONE`.

**Жёсткие гейты `DONE`:** **D-B3** (SUBMIT recovery-by-clientId) — built; **D-M1**
(per-pass concurrency-guard) — удовлетворён in-process `JobExecutionGuard` (одиночный
инстанс; распределённый лок — фаза 3, `tech-radar` Raw-JDBC → `hold`).

## Открытые вопросы

Без изменений (HOLD-Q1 закрыт ранее). INSTR-Q2 остаток, ORCH-Q1, CMD-Q4→шаг 8,
OKX-Q1/Q2/Q3→шаг 7, OKX-Q4 WS, STRAT-Q4, IND-Q1, STRUCT-Q1, PHASE-Q1/Q2. Ни один
не гейтит текущий заход.

## Принципы (без изменений)

Docs-first; шаг 6 композиционный; механика финализации — шаг 6, расчёт PnL —
шаг 7; числа провизорны/не выдумываются; инфраструктура от потребности.
Kill-switch как аварийный тормоз не зависит от исправности петли.

## Среда

**Тулчейн:** `mvn` — Maven 3.9.11 из wrapper-dist
(`~/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn`);
`JAVA_HOME` = `~/.jdks/corretto-25`. Компиляция: `mvn -o -q -DskipTests compile`.
Boot: `SPRING_PROFILES_ACTIVE=test`, все джобы `*_ENABLED=false`, `VAULT_TOKEN`/
`VAULT_URI` из `.env.vault.test.local`; инфра в docker — postgres:16 (5440 prod /
**5441 test**), vault:1.15 (8200). Прод автономно **не** поднимаем; test — demo,
`x-simulated-trading=1`.

## После коммита

Обновить PK (v62 заменяет v61).
