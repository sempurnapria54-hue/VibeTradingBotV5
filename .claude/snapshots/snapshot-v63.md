# Snapshot v63

**Дата:** 2026-07-02.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — применены фиксы по агентскому ревью
всей кодовой базы (v62): 30 из 32 находок исправлены (2 blocker, 4 major, 24
minor), 2 minor осознанно отложены; дельта компилируется, test-компилируется и
бутается вживую.** Сменяет v62.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-5 `DONE`, **шаг 6 `CODE`** (Stage 1+2 построены,
Stage 3 частично; дерево **зелёное**; находки ревью закрыты), шаги 7-11 `HOLD`.
Ветка `claude-audit`.

`HEAD = 9573c1d (ROADMAP 1-6-4 CODE_4)` — сюда закоммичены green Stage 2/3 +
заметка ревью `.claude/notes/2026-07-02-code-review-full-codebase.md` + снапшот v62.
**Фикс-дельта по ревью — staged поверх HEAD, не закоммичена: 31 файл,
+171 / −176.**

## Как сюда пришли (эта сессия)

v62 зафиксировал: агентское ревью 420 файлов дало 32 находки, правки **не
вносились**. Здесь — прошли по находкам в рекомендованном порядке (B1+B2 → M3 →
M4 → M1+M2 → minor) и исправили.

## Что исправлено (30/32)

### 🔴 Блокеры (оба CONFIRMED, ядро шага 6)
- **B1** `deal/action/CreateAlgoOrderActionExecutor` — добавлен `buildCondition(...)`:
  собирает дерево `Condition` (`Trigger`/`Trailing` с рассчитанными SL/TP/trailing
  ценами из `CalculatedPrice` по типу условия). Стоп больше не уходит на биржу без
  триггера.
- **B2** `deal/handler/EntryFinalizedHandler` — переход в MANAGING через новый
  rich-предикат `Order.hasActiveAttachedProtection()` (проверяет active-like, не
  наличие); отклонённый стоп больше не пропускает бесстоповую позицию в MANAGING.

### 🟠 Мажоры (все correctness, CONFIRMED)
- **M1** `StrategyDetailApiModel` → `List<@Valid StrategyStepApiModel>` (каскад в
  элементы).
- **M2** `GlobalExceptionHandler` → `@ExceptionHandler(ResponseStatusException.class)`
  (400/422 больше не 500).
- **M3** `ServiceCommandExecutor` — возвращённый (не брошенный) failure идёт через
  retry/terminal-учёт (рефактор `applyFailureAccounting`); `DealOrchestratorJob`
  инспектирует результат и `break`'ает после сбоя. ACK-реджект больше не застревает.
- **M4** `MarketPhaseService` — `buildContext` заполняет `.price(...)`; сигнатура
  `getCurrentPhase(Instrument, Strategy)` + правка `EntryScannerJob`. PRICE-операнды
  фазовых правил больше не всегда-false.

### 🟡 Минор (24)
- **Мёртвый код (9):** `DealFsmSupport.refreshFillsCommand`, `IndicatorDataService.
  findCheckpoint` + `MarketStructureDataService.findCheckpoint` (+осиротевшие
  repo-запросы `findMaxCandleTimestamp`/`findMaxWindowEndAt` + импорты),
  `PriceRoundingPolicy` (удалён файлом), `Order.isNotLive`, `AlgoOrder.isNotLive`/
  `toPartiallyComplete`, `AttachedAlgoOrder.isTerminal`/`hasExternalType`
  (+`TERMINAL_STATUSES`). `AttachedAlgoOrder.isActiveLike` — **оставлен**, теперь
  используется фиксом B2.
- **Избыточные `@Mapping` (5 мапперов, 7 строк):** OrderMapper, AlgoOrderMapper,
  CandleGroupMapper, InstrumentMapper, StrategyMapper — удалены; авто-биндинг
  одноимённого параметра подтверждён по сгенерированному коду.
- **Прочее:** `Objects.equals` в `DealFsmSupport` (×4), `IndicatorCalculator.
  effectiveWarmup` `int`→`Integer`, `@Schema` +EFFICIENCY_RATIO, стейл-javadoc
  (`ExchangeIntegrationException`, `Retryable`), `RetryPolicyService` overflow-clamp
  (`1L << min(attempts-1, 30)`), `RiskBlockResolver` −3 мёртвых параметра (+call-site
  `CreateOrderActionExecutor`), `PrecheckHandler` → `foreignLiveRisk` перенесён в
  фасад `DealFsmSupport` (инъекция `IntegrationService` убрана из handler'а),
  `IndicatorParams` инертный `@JsonSubTypes` удалён (десериализация — ручная в
  `StrategyJsonConverter`, подтверждено).

## Отложено осознанно (2 minor, с флагом)

- **`ExternalStatusReason:8` (enum вне домена)** — перенос спорен: integration-концепт,
  привязанный к `ControlledExchangeException`; правило целит модельные enum'ы,
  пересекающие слои через MapStruct. Нужна codestyle-exception либо решение о доме
  (развилка владельца правил, не механическая правка).
- **`MarketPhaseService:62` (2 запроса на indicator setting)** — чистый фикс требует
  нового value-типа (`findLatestTwo` + freshness к элементу 0) и правки 2 фабрик;
  ради marginal-выигрыша в фазе 1 с риском задеть freshness-семантику → backlog
  (как перф-миноры M2-M5 оригинального ревью).

## Верификация

- `mvn -o -DskipTests compile` (+showDeprecation/Warnings) → **EXIT=0**, чисто.
- `mvn -o -DskipTests test-compile` (64 тест-файла) → **EXIT=0**.
- **Boot:** test-профиль, джобы off, Vault → **`Started TradingBotApplication in
  ~6.7s`**; `DealFsmSupport`+`IntegrationService` и все изменённые бины связаны без
  DI-цикла.
- Сгенерированные мапперы: `setInstId`×4 / `setExchangeInternalId` /
  `setInstrumentInternalId`×2 присутствуют — авто-биндинг работает.

## Loose ends / doc-sync (для `SYNC_DOCS_FROM_CODE`)

- `docs/components/models/CalculatedPrice.md §PriceRoundingPolicy` — enum удалён.
- `docs/components/RiskBlockResolver.md` — сигнатура `resolve` сузилась (−3 параметра).
- `strategy-examples/trend-following-ema.json` — всё ещё `actionKind: POSITION` /
  `CLOSE_FULL` (не грузится кодом; Stage 3 хвост, ещё из v62).
- Прочие javadoc-упоминания удалённых методов/enum'ов при большом SYNC.
- Часть Stage 2/3-файлов остаётся unstaged в рабочем дереве (см. v62) — стейджинг в IDEA.

## Что дальше

1. **Повторное ревью фикс-дельты** (31 файл) — на предмет регрессий/новых дефектов,
   внесённых правками (запускается следом).
2. Финальный аппрув `CODE` + большой `SYNC_DOCS_FROM_CODE` + пост-хок концепт-гейт
   §6a → `DONE`. Жёсткие гейты (D-B3/D-M1) — оба built.

## Открытые вопросы

Без изменений (HOLD-Q1 закрыт ранее). Ни один не гейтит текущий заход.

## Среда

Тулчейн/инфра — без изменений (см. v62): `mvn` 3.9.11 из wrapper-dist,
`JAVA_HOME=~/.jdks/corretto-25`; boot — `SPRING_PROFILES_ACTIVE=test`, джобы
`*_ENABLED=false`, `VAULT_TOKEN`/`VAULT_URI` из `.env.vault.test.local`; docker
postgres:16 (5441 test), vault (8200).

## После коммита

Обновить PK (v63 заменяет v62).
