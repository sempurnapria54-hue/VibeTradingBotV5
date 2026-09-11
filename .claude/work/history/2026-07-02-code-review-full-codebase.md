<!-- anchor-check: описывает прошлое — ревью на 2026-07-02: §-адреса ведут в разделы кода и доков того момента -->

# Агентское ревью кодовой базы (полный охват) — 2026-07-02

## На какой вопрос отвечает этот файл

Что показало агентское ревью всей кодовой базы 2026-07-02 (находки со
статусом верификации).

**Ветка:** `claude-audit` · **Охват:** вся кодовая база — 420 main Java-файлов
(~25K строк), 20 подсистем. Правки по ревью **не вносились** — чистое ревью.

## Методология

- **Фан-аут:** 20 ревьюеров, по одному на когерентную подсистему; каждый читал
  `.claude/rules/codestyle.md` + `.claude/rules/tech-radar.md` как источник правды
  для conventions и проходил по 4 дименшенам: **correctness / conventions /
  performance / design** (эффорт `high`).
- **Адверсариальная верификация:** каждая находка ушла независимому скептику
  (эффорт `medium`), по умолчанию refute при сомнении и с учётом задокументированных
  исключений codestyle. Ряд verdict'ов подтверждён эмпирически (перекомпиляция
  MapStruct, воспроизведение на Hibernate Validator 9).
- **Покрытие 100%:** подсистемы `deal`/`model-strategy-core`/`safety` падали по
  обрывам соединения (инфра-флак); перезапущены, `deal` дожат разбиением на 3 части.
  Финально покрыты все 420 файлов.

## Итог

| Severity | Кол-во |
|---|---|
| 🔴 blocker | 2 |
| 🟠 major | 4 |
| 🟡 minor | 26 |
| **Выжило** | **32** |
| ⚪ отсеяно верификацией | 3 |

По дименшенам (выжившие): correctness 7 · conventions 21 · design 3 · performance 1.

Чисто (0 находок): `config-util`, `model-strategy-core`, `safety`,
`model-strategy-settings` (кроме 1), большинство integration/persistence-моделей.

---

## 🔴 BLOCKER (2) — оба CONFIRMED, оба в ядре шага 6

### B1. `domain/deal/action/CreateAlgoOrderActionExecutor.java:68` — стоп-лосс уходит на биржу без триггерной цены
`createAlgoCommand` строит `Condition condition = new Condition(); condition.setType(action.getConditionType());` и **никогда не заполняет** `condition.trigger` / `condition.trailing`. Результат калькулятора (`getCalculatedAction().getCalculatedPrice()` — несёт `stopLossPrice`/`takeProfitPrice`/`trailingPrice`) игнорируется: читается только `getCalculatedSize()`. Контракт payload (`CreateAlgoOrderCommandPayload` javadoc) явно требует «готовое дерево condition (trigger/trailing с рассчитанными ценами)». Ниже `CreateAlgoOrderExecutor.buildAlgoOrder` сохраняет condition как есть; `validateConditionProjection` проверяет только `conditionType==condition.type` и пропускает. На submit `AlgoOrderMapper` гейтит на `nonNull(condition.getTrigger()...getStopLoss().getValue())` — теперь false → OKX-запрос уходит **без триггера**.

**Сценарий:** CREATE-действие для стоп-лосса (conditionType=SL) с вычисленным триггером 25000 → algo-ордер создаётся и сабмитится с `trigger=null` — стоп, который никогда не сработает, позиция без защиты.

**Фикс:** маппить `getCalculatedPrice()` (SL/TP/trailing) в полностью заполненный `Trigger`/`Trailing` на `Condition` перед сборкой payload (или чтобы калькулятор возвращал готовое дерево condition).

### B2. `domain/deal/handler/EntryFinalizedHandler.java:128` — бесстоповая позиция проходит в MANAGING
Гейт `toManagingIfProtected`: `nonNull(entry) && isNotEmpty(entry.getAttachedAlgoOrders())`. Но `AttachedAlgoOrder` может быть терминальным (CANCELED/ERROR/COMPLETED — см. `AttachedAlgoOrder.isTerminal`/`ACTIVE_LIKE_STATUSES`). Проверяется только **наличие**, не активность.

**Сценарий:** entry-ордер налился в живую позицию, но приложенный стоп был отклонён/отменён биржей (ERROR/CANCELED). `getAttachedAlgoOrders()` всё ещё возвращает терминальную запись → `isNotEmpty` = true → сделка уходит в MANAGING. `ManagingHandler.checkEntry:51` перепроверяет только `positionLiveRisk`, защиту — никогда. Итог: live-risk позиция в MANAGING без активной защиты — ровно тот «stopless-position-postfactum» кейс, который class javadoc и `docs/rules/risk-creating-entry-protection.md` требуют уводить в ERROR через `markErrorStopless` (§8.C, L3-hold).

**Фикс:** требовать хотя бы один активный приложенный algo перед переходом — `entry.getAttachedAlgoOrders().stream().anyMatch(a -> isTrue(a.isActiveLike()))`; иначе `markErrorStopless(dealContext)`.

> Связка: B1 создаёт стоп без триггера → на бирже он отвергается → B2 не ловит отсутствие активной защиты и пускает позицию в MANAGING бесстоповой. Вместе дают незащищённую live-позицию, которую петля считает нормальной.

---

## 🟠 MAJOR (4) — все CONFIRMED, все correctness

### M1. `api/model/strategy/StrategyDetailApiModel.java:41` — шаги стратегии минуют create-валидацию
`stepsByStatus: Map<String, List<StrategyStepApiModel>>` помечен только field-level `@Valid`. В Hibernate Validator (SB4/HV9) `@Valid` каскадит на один уровень контейнера (значения мапы = списки), но **не рекурсит в элементы списка**. Все constraints внутри `StrategyStepApiModel` (`@NotBlank stepType`, `@NotNull condition`, `@NotEmpty actions`, `@NotNull marketDataExpiredSetting`) не проверяются. POST с blank stepType / null condition проходит 400-валидацию, ломается позже на материализации. *Воспроизведено эмпирически на HV 9.0.1.*
**Фикс:** `Map<String, List<@Valid StrategyStepApiModel>> stepsByStatus` (field-level `@Valid` оставить для мапы).

### M2. `api/GlobalExceptionHandler.java:71` — 400/422 превращаются в 500
Catch-all `@ExceptionHandler(Exception.class)` без хендлера `ResponseStatusException`. `ExceptionHandlerExceptionResolver` (@ControllerAdvice) приоритетнее встроенного `ResponseStatusExceptionResolver`, поэтому `ResponseStatusException(400/422)` из `StrategyCreateRequestValidator`/`StrategyService` ловится fallback'ом → клиент получает `500 "Internal error"` вместо 400/422 со списком нарушений.
**Фикс:** добавить `@ExceptionHandler(ResponseStatusException.class)` (маппить `getStatusCode()`/`getReason()`) либо наследовать `ResponseEntityExceptionHandler`.

### M3. `domain/command/executor/ServiceCommandExecutor.java:64` — ACK-реджект застревает навечно
Executor'ы сигналят реджект биржи, **возвращая** `failure(VALIDATION_ERROR)`, а не бросая (SubmitOrderExecutor:61, SubmitAlgoOrderExecutor:58, CancelOrderExecutor:52, CancelAlgoOrderExecutor:54, ClosePositionExecutor:53). Retry/FAILED-учёт (`handleFailure`/`recordAttempt`) достижим только из `catch(RuntimeException)`. Возвращённый failure (строка 64) отдаётся как есть → `DealActionState` не переходит в FAILED, `attemptCount` не растёт, `DealOrchestratorJob:76` результат игнорирует → **FSM пере-сабмитит тот же реджекнутый ордер каждый тик**, сделка не прогрессирует и не падает в ERROR. Асимметрия с путём `ControlledExchangeException` (терминалит).
**Фикс:** гнать non-success результат через тот же retry/terminal-учёт (или бросать `ControlledExchangeException` на ACK-реджекте); оркестратору инспектировать результат и не исполнять следующие команды перехода после сбоя.

### M4. `domain/service/market/MarketPhaseService.java:72` — PRICE-операнды в фазовых правилах всегда false
`buildContext()` не заполняет `.price(...)`, хотя `StrategyConditionEvaluator` вайтлистит PRICE для классификации фазы (javadoc:37, resolveScalar:131), а `evaluateCompare` возвращает false при null-операнде. Deal-сторона (`MarketConditionContextFactory:58`) price заполняет — фазовая нет. Правило `price > EMA` молча не матчится → фаза деградирует в UNKNOWN → неверный выбор `StrategyDetail` в `EntryScannerJob`.
**Фикс:** заполнять `.price(...)` в `buildContext` (как `MarketConditionContextFactory`) либо явно реджектить PRICE-операнды в фазовых правилах, чтобы дыра не была молчаливой.

---

## 🟡 MINOR (26)

### Мёртвый / преждевременный код (§Неиспользуемый код) — CONFIRMED
| Файл:строка | Что |
|---|---|
| `domain/deal/DealFsmSupport.java:153` | `refreshFillsCommand` (REFRESH_FILLS) — 0 вызовов, задел под шаг 7 |
| `persistence/service/IndicatorDataService.java:64` | `findCheckpoint` + осиротевший repo-запрос `findMaxCandleTimestamp` |
| `persistence/service/MarketStructureDataService.java:42` | `findCheckpoint` + осиротевший `findMaxWindowEndAt` |
| `domain/command/calc/PriceRoundingPolicy.java:8` | enum целиком не используется (спекулятивный) |
| `domain/model/core/order/Order.java:89` | `isNotLive()` — 0 вызовов |
| `domain/model/core/algo_order/AlgoOrder.java:101` | `isNotLive()` — 0 вызовов |
| `domain/model/core/algo_order/AlgoOrder.java:116` | `toPartiallyComplete()` — 0 вызовов |
| `domain/model/core/order/AttachedAlgoOrder.java:76` | `isActiveLike()` — 0 вызовов |
| `domain/model/core/order/AttachedAlgoOrder.java:81` | `isTerminal()` — 0 вызовов |
| `domain/model/core/order/AttachedAlgoOrder.java:86` | `hasExternalType()` — 0 вызовов |

> Примечание: `AttachedAlgoOrder.isActiveLike()`/`isTerminal()` числятся неиспользуемыми — но фикс B2 как раз должен их звать. При исправлении B2 эти находки закрываются использованием, а не удалением.

### Избыточный same-name `@Mapping` (§Маппинг) — CONFIRMED (эмпирически перекомпилировано)
`mapping/OrderMapper.java:81, 93` · `mapping/AlgoOrderMapper.java:99, 120` ·
`mapping/CandleGroupMapper.java:27` · `mapping/InstrumentMapper.java:73` ·
`mapping/StrategyMapper.java:136`.

### Прочие conventions/correctness
| Файл:строка | Находка | Verdict |
|---|---|---|
| `domain/deal/DealFsmSupport.java:133` | `CONST.equals(var)` вместо `Objects.equals` (также 192-193, 283) | CONFIRMED |
| `domain/service/market/indicator/IndicatorCalculator.java:38` | примитив `int` на контрактной поверхности (`effectiveWarmup`) → `Integer` | CONFIRMED |
| `integration/service/ExternalStatusReason.java:8` | enum вне доменного слоя | PLAUSIBLE (правило абсолютно; интент — про модельные enum'ы) |
| `api/model/strategy/StrategyIndicatorSettingApiModel.java:27` | `@Schema` без `EFFICIENCY_RATIO` (есть в `@JsonSubTypes`) | CONFIRMED |
| `integration/service/ExchangeIntegrationException.java:6` | стейл-javadoc «error-policy TBD» (снят) + «placeholder» | PLAUSIBLE |
| `domain/command/Retryable.java:8` | javadoc «наследует только DealActionState» — ещё и `DealFinalizationState` | PLAUSIBLE |
| `domain/command/RetryPolicyService.java:51` | `1L << (attempts-1)` overflow'ит в Long.MIN при attempts≥63 → отрицательный delay обходит maxDelay-cap | PLAUSIBLE (только maxAttempts≥64) |
| `domain/command/risk/RiskBlockResolver.java:39` | 3 неиспользуемых параметра `resolve(...)` | PLAUSIBLE (правило про методы, не параметры) |

### design / performance
| Файл:строка | Находка | Verdict |
|---|---|---|
| `domain/deal/handler/PrecheckHandler.java:46` | инъекция `IntegrationService` напрямую, минуя фасад `DealFsmSupport` (единственный такой handler) | PLAUSIBLE |
| `domain/model/aggregate/strategy/setting/IndicatorParams.java:23` | `@JsonSubTypes` без `@JsonTypeInfo` инертен; резолв в `StrategyJsonConverter` вручную; javadoc вводит в заблуждение | PLAUSIBLE |
| `domain/service/market/MarketPhaseService.java:62` | 2 запроса на indicator setting (`findLatest`+`findLatestTwo`) вместо одного | PLAUSIBLE |

---

## ⚪ Отсеяно верификацией (3)

| Файл:строка | Заявка | Почему REFUTED |
|---|---|---|
| `domain/command/risk/RiskValidator.java:220` | fail-open при null `riskPerTradePercent` (major) | Null-риск обрабатывается намеренно и симметрично в `SizeCalculator.capByRiskLimit` (opt-in cap; размер ограничен allocation/`SIZE_ABOVE_LIMIT`); инвариант держит `RISK_CREATING_ENTRY_WITHOUT_STOP`. «Should block» — спорное дизайн-мнение, не дефект. |
| `domain/command/calc/CalculationContextFactory.java:104` | N запросов структур vs батч индикаторов (perf) | Посылка неверна: `getLatestValues` тоже не батчит (та же N-выборка per setting) — асимметрии нет. |
| `domain/command/calc/SizeMode.java:16` | `FULL_CLOSE` — мёртвая константа | Задокументированное значение (`CalculatedSize.md §SizeMode`); полное закрытие реально через `ClosePositionExecutor`; javadoc «нет действия close» — про `StrategyActionType`, не `SizeMode`. |

---

## Рекомендованный порядок исправления

1. **B1 + B2** — чинить вместе (защита позиции): без них бот может держать незащищённую live-позицию. Фикс B2 заодно «оживляет» неиспользуемые предикаты `AttachedAlgoOrder`.
2. **M3** — застревание сделки на ACK-реджекте (ядро петли, влияет на все ордера).
3. **M4** — молчаливая деградация фазы (влияет на выбор стратегии).
4. **M1 + M2** — валидация/error-поверхность (дрейф эпохи шага 2, но пользовательский контракт API).
5. **Minor** — пакетно на `SYNC`/чистке: мёртвый код, избыточные `@Mapping`, стейл-javadoc, `Objects.equals`, примитив на контракте.
