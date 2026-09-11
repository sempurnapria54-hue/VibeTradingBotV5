# Чистка бэклога и ввод машинной формы секции 2026-09-11

## На какой вопрос отвечает этот файл

Что унесла чистка `.claude/work/backlog.md` 2026-09-11 и чем она
обеспечена от повторного отрастания.

## Повод и итог

Указание держателя: «закрывай процессные дефекты, чтобы в бэклоге
хранились только актуальные задачи, а решённые убирались автоматом».
Разбор всех 143 секций (7 частей, каждая позиция проверена грепом по
`services/`, `libs/`, `docs/`, `tools/`, реестрам) показал механизм, а
не беспорядок: с 5 сентября файл рос закрытым — закрытие шага бэклог не
читало, условия возврата были написаны словами, парковки писались
разбором. Три процессных дефекта закрыты:

- **форма секции с машинным условием** — `.claude/rules/backlog-section-form.md`
  (маркер `владелец / оживит / закрыто-когда`, потолок 60 строк, запрет
  провенанса);
- **энфорсер** — `tools/backlog-check.py`, в гейте инструментов корпуса
  (`.claude/skills/update-roadmap-progress.md` §«Гейт инструментов корпуса»,
  с флагом `--статус` до записи статуса);
- **указатели** из `closed-work-transfer.md`, `parking-address.md`,
  `curation.md`, `structure.md`, `roadmap-step-execution.md`; довод —
  `.claude/decisions/backlog-machine-form.md`; дайджест Д1439-Д1445.

Файл приведён к форме одним ходом. Замер до и после — командами
`wc -l -c .claude/work/backlog.md` и `py tools/backlog-check.py`:

| Момент | Строк | Байт | Секций `##` | Единиц с маркером |
|---|---|---|---|---|
| до (коммит `714a0217`) | 5806 | 499609 | 100 | 0 |
| после | см. прогон | см. прогон | см. прогон | см. прогон |

Числа «после» здесь не хранятся: их печатает прогон, а файл живёт.

**Снята вводная секция «Переписывание корпуса — что осталось»**: пункт 1
дублировал §«Боевые числа риск-аппетита…», пункт 2 указывал на
CODE-дельту, построенную шагом 7 фазы 2. Итог переписывания корпуса —
`2026-09-03-phase-1-step-7-deals-and-pnl/corpus-rewrite-mapping.md`.

**Рабочий лист CODE-дельты шага 7 фазы 1** (секция «Агрегатная сделка и
транши — CODE-дельта» и подсекции «CODE-дельта шага», «Грунт
`integrator`», «Рантайм-верификация») сохранён дословно как единственный
носитель снятых редакций —
`2026-09-03-phase-1-step-7-deals-and-pnl/phase-1-step-7-code-delta-worksheet.md`.

**Правки вне бэклога тем же ходом:** входящие указатели
(`docs/rules/api-access-policy.md`, `docs/components/HoldService.md`,
`.claude/tests/source-api/okx/plan.md`, `open-questions.md` ORCH-Q1 и
PNL-Q1, `Instrument.java`), устаревшие клеймы
(`FillOkxResponse.md`, `DealOpeningService.md`), клауза об
автоконфиг-модулях в `codestyle.md` §«Новый модуль монорепозитория»,
норма `test`-профиля в `agents/tester.md`, реестр гейтов (G1 закрыта,
примечание `DealContextService`, оговорки у компонентов «оркестратор
действий» и «strategies: валидация создания»), реестр снятых редакций
(шесть кортежей популяции `backlog.md` сняты вместе с секциями, запись о
паре сетевой политики переведена на манифест), семь погашенных строк
`tools/anchor-debt.txt`, заметка `2026-07-02-code-review-full-codebase.md`
перенесена сюда из `notes/`.

**Найдено попутно и оставлено задачами бэклога:** три кода
create-валидации из правила не построены у `strategies`; читателя
`exitOutcome` и кодов `PARTIAL_EXIT_*` в ядре нет; операнд
`episodes[].exitAt` спеки назван по несуществующему полю; ступень
`ENTRY_BLOCKED` по несвежести ставки без писателя; битый
javadoc-указатель в `StrategyDefinitionValidator.java`.

Ниже — выписки по семи диапазонам прежнего файла: что закрыто, чем
доказано, где живёт содержание, какие оживители сработали.

---

# Выписка закрытого — диапазон backlog.md 72-433 (секции «Агрегатная сделка и транши — CODE-дельта», «Cross-cutting миграции»)

Для `history/2026-09-11-backlog-diet.md`. Сокращения: `V1` = `services/trading-core/src/main/resources/db/migration/V1__trading_core_baseline.sql`; `TC` = `services/trading-core/src/main/java/com/example/tradingcore`; `DM` = `libs/domain-model/src/main/java/com/example/tradingbot/domain/model`; `ledger` = `.claude/work/code-gate-ledger.json`.

## Секция «Агрегатная сделка и транши — CODE-дельта» (снята целиком)

- **Шапка секции** (имя `Tranche` ратифицировано, корпус приземлён 2026-08-29, «осталось коду») — всё оставленное коду построено шагом 7 фазы 2 (DONE 2026-09-06): `history/2026-09-06-phase-2-step-7-trading-core.md` §Итог; `ledger` компоненты «порт агрегата сделки и транша», «FSM сделки и её обработчики», «FSM транша и её обработчики», «риск-гейт» — `закодирован`. Конструкция и доводы — `history/2026-08-29-tranche-landing/aggregate-deal-design.md`, `history/2026-08-29-tranche-landing.md`.
- **Схема траншей** (`deal_tranches`, `strategy_tranches`, `*.deal_tranche_id`, `positions.external_fee/funding_cost/liquidation_penalty`, `exchanges.risk_base*`, `consecutive_loss_count`, `uk_deal_tranche_declaration`, `entry_reason NOT NULL`) — факт миграции `V1` ядра; `strategy_tranches` также в `services/strategies/.../V1__strategies_baseline.sql`.
- **Статусные енумы** — `DM/aggregate/deal/Deal.java` (`Deal.Status`: `ACTIVE`, `EXIT_PENDING`, `ERROR`, `CLOSED`, `EMERGENCY_CLOSED`), `DealTranche.Status`.
- **FSM** — `TC/domain/fsm/DealStateMachine.java`, `DealTrancheStateMachine.java`, `TrancheTransitionGate.java` (ребро переоткрытия `reopenEdge`/`reopenPermitted`), обработчики `DealActiveHandler`/`DealExitPendingHandler`/`ErrorHandler`.
- **Экспозиция транша и сверка Σ с `Position.externalSize`** — детектор A11 `docs/components/AnomalyJob.md`; `ledger` «детекция аномалий: AnomalyJob и детекторы» — `закодирован`.
- **Преконтроль** (операнд покрытия траншевый) — `RiskValidator`; `ledger` «риск-гейт: RiskValidator и RiskBlockResolver» — `закодирован`.
- **Валидация create** (транши, `STRATEGY_DEAL_LEVEL_STEP_OUT_OF_SCOPE`, `N_overlap`) — код в валидаторе `services/strategies`; `ledger` «strategies: валидация создания» — `закодирован`; `docs/rules/strategy-validation.md`.
- **Эталон `trend-following-ema.json` в форме траншей** — `services/strategies/src/test/resources/strategy-examples/trend-following-ema.json`, тест `ReferenceDefinitionBodyTest`.
- **«Названные цены, принятые держателем»** — пересказ решения; дом уже назван: `history/2026-08-29-step-7-docs-check-loop/`.

## Подсекция «CODE-дельта `GAPS_CLOSE_29` (узлы посерийного прохода)» (снята; живой остаток — новая секция «Коды непозитивной доли действия — док объявляет, код не эмитит»)

- **Входной гейт полноты графа `DEAL_GRAPH_INCOMPLETE`** — `TC/domain/command/risk/RiskValidator.java` и ещё 4 файла; `graphComplete` на контексте прохода.
- **Схема `deals.entry_market_phase`, `uk_position_deal_external` парой** — `V1` (частичный уникальный индекс `(deal_id, external_id, external_created_at) where external_id is not null`).
- **Дискриминатор эпизода — пара `(externalId, externalCreatedAt)`** — `V1`, `externalCreatedAt` в 23 файлах `services`/`libs`; `ledger` «исполнители площадки: добыча состояния» — `закодирован`.
- **Три энфорсера запрета набора риска в окне сворачивания** — `RISK_CREATING_UNDER_COLLAPSE`/`riskCreatingUnderCollapse` в `RiskValidator`, `RiskBlockResolver`, `RiskCheckResult`, `TrancheTransitionGate`; `TranchePrecheckHandler`; ветвь переоткрытия — `TC/domain/fsm/tranche/TrancheManagingHandler.java`.
- **Ступень реакции покрытием, радиус типом; энфорсмент жёсткой ступени обоих радиусов; идемпотентность отчёта отдельно от анкера** — `ledger` «холды: HoldService, SafetyHoldCoordinator, KillSwitchService, KillSwitchExecutor» — `закодирован`; примечание «петля прохода: DealOrchestratorJob» («энфорсмент жёсткой ступени до сборки контекста»); детектор A6 `AnomalyJob.md`. Дома — `docs/rules/instrument-hold.md`, `docs/rules/error-handling-policy.md`, `docs/components/HoldService.md`.
- **Признаки отбора не пересчитываются при финализированном числе** — `TC/domain/command/calc/DealTerminalFeaturesWriter.java` (`resultFinalized`); `docs/spec/deal-lifecycle.json`.
- **Фаза рынка в проходе сделки** — см. «Сработавшие оживители».
- **Судьба встроенной защиты по фактам родителя** — `TC/domain/command/resolve/AttachedAlgoOrderStateResolver.java` (`attachedParentClass`, `attachedOutcomeByParent`, `searchExhaustedOutcome`); снятая редакция `OkxAttachedAlgoOrderStateResolver` в коде отсутствует.
- **Однократность шага на эпизод — писатель `+1`** — см. «Сработавшие оживители».
- **Отказ расчёта по стороне уровня `STOP_LEVEL_NOT_ON_LOSS_SIDE_FOR_SIZING`** — 5 файлов в `services`/`libs`.
- **Цикл добычи материализованной защиты** — позиция Т4 закрыта 2026-09-02 (`history/2026-09-03-step-7-code-tail-closures.md`); `TC/.../RefreshOrderExecutor.java` (`getPendingMaterializedProtections`), коннектор `services/connector-okx/.../OkxSourceReader.java` (`orders-algo-history`), `AttachedAlgoOrderExternalSnapshot.externalStatus`. Дом — `docs/lifecycles/Order.md` §«Исход ненайденности — вторая ступень».
- **Уровень на всю позицию отказывает вычислением (`dealStopUnresolved`)** — 3 файла в `services`/`libs`; `docs/spec/protection-coverage.json`.
- **Снятие значений без производителя и ввод `EXTERNAL_CLOSE`** — `MANUAL_CLOSE`/`MANUAL_CANCEL`/`MANUAL_STOP`/`UNKNOWN` в перечнях `Deal.CloseReason`, `Deal.ShutdownReason`, `Position.CloseReason` отсутствуют; `EXTERNAL_CLOSE` — `DM/aggregate/deal/Deal.java` (ранг старшинства), `DM/core/position/Position.java`; писатель — `TC/domain/fsm/tranche/TrancheExitPendingHandler.java`; javadoc payload-ов снятых значений не несёт.

## Секция «Cross-cutting миграции» (снята; четыре живых парковки промоутированы в свои `##`)

- **§2 Resolver / mapper / checker** — мапперы `OrderMapper`/`PositionMapper`/`AlgoOrderMapper`/`BalanceContainerMapper` в `services`, доки `docs/models/mapping/{Order,Position,AlgoOrder,Balance}.md`; `AttachedAlgoOrderStateResolver` построен. Имена `BalanceFreshnessChecker`, `OkxAlgoOrderTypeResolver` мертвы (ни в коде, ни в `docs/`): свежесть средств живёт у `DealContextService`/`HoldService`/`EntryScannerJob`. Форвард-заметки ORD-Q2/POS-Q2/ALGO-Q2/BAL-Q6 — архив `history/2026-05-27-миграция-торговых-сущностей/`.
- **§6 Аудит и история исполнения** — журнал по тенанту строит шаг 10 фазы 2 (`docs/models/domain/other/AuditRecord.md`, `docs/spec/audit-journal.json`; `ledger` компоненты `audit-statistics` — `закодирован`); durable-строки исполнения — Т12, финализация PnL — Т13 (`history/2026-09-03-step-7-code-tail-closures.md`). Имена `ServiceCommandExecutionHistory`, `timeline` в `docs/` мертвы. Живой остаток — пофилловый аудит — новая секция «Пофилловый аудит исполнений».
- **§7 Anomaly / safety / kill-switch** — `ReconciliationJob` поглощён детекторами A1 и A8 `AnomalyJob` (`docs/components/AnomalyJob.md`); kill-switch — `docs/components/KillSwitchService.md`, `KillSwitchExecutor.md`, `Position.CloseReason.KILL_SWITCH`; `TradeRuleValidator` — предмет живёт в `docs/rules/trading-constraints.md` и `docs/components/InstrumentExternalRulesDataService.md`. Шаг 8 фазы 1 закрыт — `history/2026-09-04-phase-1-step-8-anomaly-job.md`.
- **§8 Strategy: enforcement, валидатор, примеры** — предмет пункта (runtime-прогон донорского Strategy API: `V2` → `POST`/`GET`/`PUT`) снят вместе с донором: донор заморожен и удаляется гейтом фазы (`phase-2.md` §Примечания); целевой `strategies` построен шагом 8 фазы 2. Форвард «живой прогон против базы» уже припаркован общей секцией §«Контекстный тест сервиса — по появлению базы в прогоне» (все `services/**`). `Strategy API examples.md` — решено де-факто: примеры живут в `docs/spec/strategy-reference.json` и `services/strategies/src/test/resources/strategy-examples/`.
- **§9 Exchange модель/lifecycle** — целевая форма построена шагами 3-4-7 фазы 2: площадка и счёт у `auth`, торговое состояние у `trading-core` (`docs/models/domain/core/Exchange.md`, `ExchangeAccount.md`, `Tenant.md`, `docs/architecture/tenant-and-exchange.md`); `Account` → `ExchangeAccount`; лестница safety — `docs/rules/exchange-hold.md`. Живой остаток — новая секция «Периферийные статусы `Instrument` — …».
- **Отложенные продуктовые вопросы: `linkedOrderExternalIds` (ALGO-Q6)** — решено: поле — внешний факт, runtime на него не опирается (`docs/models/domain/core/AlgoOrder.md`, таблица полей); поле в `DM/core/algo_order/AlgoOrder.java`, `AlgoOrderEntity`, маппере коннектора. ALGO-Q6 — архивный вопрос `history/2026-05-27-миграция-торговых-сущностей/tasks-algo-order.md`.
- **Отложенные продуктовые вопросы: ретеншен строк исполнений, версионирование JSONB-снимков** — остались живыми, промоутированы в свои `##` с домами (`DealActionState.md` §Назначение; `persistence-representation.md` §«Реляционно или JSONB»); в старой редакции у обоих не было ни дома, ни оживителя, а адрес «фаза 3» у ретеншена носителя в роадмапе не имел.

## Сработавшие оживители

- **«Фаза рынка в проходе сделки» — оживитель «читатель фазы» сработал** шагом 7 фазы 2: `StrategyConditionEvaluator` (`libs/strategy-engine/.../condition/StrategyConditionEvaluator.java`) реализует `MARKET_PHASE_IS` и `TREND_CHANGED` (`evaluateMarketPhaseIs`, `evaluateTrendChanged`); `DealOpeningService.java` пишет `Deal.entryMarketPhase`; `DealContext.java` несёт `entryMarketPhase`. Исход — **закрыта**. Примечание компонента «DealContextService и сборка контекста прохода» в `ledger` («ПРИПАРКОВАНО НАЗВАННО: фаза рынка в контекст не кладётся… Дом парковки — backlog §«CODE-дельта `GAPS_CLOSE_29`…»») устарело — правка в fixes.
- **«Однократность шага на эпизод — остался писатель `+1`» — оживитель (ветвь переоткрытия) сработал:** `DealTrancheStateMachine.bumpEpisodeOnReopen`, `TrancheManagingHandler` (ветвь переоткрытия по нулевой экспозиции), `episode_seq`/`tranche_episode_seq` в `V1`. Исход — **закрыта**.
- **§8 «runtime-прогон Strategy API при поднятом PostgreSQL» — условие (PostgreSQL) сработало** стендом 2026-09-05, но предмет (донорский API) снят вместе с донором; прогон целевого `strategies` покрыт §«Контекстный тест сервиса — по появлению базы в прогоне». Исход — **закрыта** без переноса.
- **ORCH-Q1 (реестр открытых вопросов, вне диапазона)** — собственный горизонт вопроса «конец фазы 1» сработал (фаза 1 `FOLDED`), а его указатель «backlog п.9» ведёт в снятую секцию; вопрос остаётся открытым и служит оживителем новой секции о периферийных статусах `Instrument`. Правка указателя — в fixes.

---

# Выписка закрытого — часть 2 (строки 434-1271 `backlog.md`, 2026-09-11)

Для `history/2026-09-11-backlog-diet.md`. Проверки — на рабочем дереве
ветки `claude-audit` 2026-09-11.

## Закрытые пункты

- **Секция «Биржевой карв-аут возврата после снятия жёсткой блокировки» — снята целиком.** Предмет снят: у биржевого счёта онбординговых значений нет — `status` есть состояние регистрации, ступень живёт в `safetyRung` (`docs/models/domain/core/ExchangeAccount.md`, строки 31, 44); открытый вопрос `HOLD-Q2`, на который секция ссылалась, закрыт «снятием предмета» (итог — `.claude/work/progress/phase-2-step-10-gaps-close-1.md` §«Итог `HOLD-Q2`, снятого из `open-questions.md`»); `docs/rules/exchange-hold.md` ограничения «возврат безусловен при аварии на онбординге» не называет (grep `онбординг` — пусто), горизонт «шаг, вводящий онбординг биржи» ни в одной фазе роадмапа не существует. Живой остаток — ноль; входящих ссылок из живых носителей нет.
- **«Носители прогона гейтящих слотов шага 7» — исполненная часть.** Все 13 слотов реестра `.claude/tests/source-api/okx/code-preconditions.md` — `✅ собрано` (строки 122-134); П. 17 закрыт (`M17CancelOrderLiveTest.FIXTURE_RISK_CEILING_USDT`, `donor/src/test`); П. 10 закрыт `.claude/decisions/unorderable-fact-substitutes.md`. Живой остаток (коллекция Postman) переозаглавлен: «Коллекция Postman не изоморфна плану контура источника OKX». Хроника шага — `.claude/work/history/2026-09-03-phase-1-step-7-deals-and-pnl.md`.
- **«Указатели javadoc в несуществующие доки» — хроника измерения.** Два адреса переадресованы (`docs/decisions/fsm-execution-layering.md` → `docs/processes/`, `docs/decisions/replace-not-amend.md` → `docs/rules/`); 21 указатель шага 6 фазы 2 и 8 шага 7 закрыты переадресацией; измерение заведено — `tools/doc-pointer-check.py` (`ROOTS = ('services', 'libs', 'web')`, строка 35), в гейте инструментов корпуса. Устаревшие числа донора (22/49; на 2026-09-11 — 16 адресов / 30 вхождений) сняты по правилу самой секции «числа не хранятся».
- **«Неразрешимые адреса пассажей» — E-П7 (одноимённые величины в двух спеках без энфорсера).** Закрыто классом D `tools/spec-scope-check.py` (ось 9, строки 332-339); `.claude/rules/structure.md` строка `docs/spec/` это объявляет.
- **«Неразрешимые адреса пассажей» — E-П4 (пятому неравенству не назван исполнимый носитель).** Закрыто: `docs/rules/strategy-validation.md` строки 247-251 называют `notionalHeadroomSatisfied` / `detailsBreakingNotionalHeadroom` в `docs/spec/strategy-reference.json`.
- **«Неразрешимые адреса пассажей» — G-П6, одна из трёх позиций.** `.claude/processes/question-delegation.md` — вопрос сведён к одному («Как устроен режим автономии.»). Две оставшиеся (снапшот, `2026-08-26-step-7-question-flow-analysis.md`) — живы.
- **«Неразрешимые адреса пассажей» — G-П9, одна из трёх самоссылок.** `docs/models/domain/other/MarketStructure.md:190` — снята; остаются `MarketPhase.md:16` и `MarketStructure.md:110`.
- **«Неразрешимые адреса пассажей» — хроника долга.** Расширение детектора до трёх форм адреса (272 → 472 адресов), построчный реестр `tools/anchor-debt.txt` с храповиком; устаревшие числа «32 адреса / 29 адресов» сняты по правилу секции. Содержание — шапка `tools/anchor-check.py`, `.claude/rules/structure.md` §Принципы.
- **«Свод адресных перекрёстий `docs/**`» — подпункт `docs/models/api/OkxRawApiRequest.md`** («граница api-модели против артефакта тест-контура»). Решён картой владельцев: `.claude/rules/knowledge-ownership-by-service.md`, строка `docs/models/api/` — сырой вызов источника отходит коннектору ходом удаления донора.
- **«Свод адресных перекрёстий `docs/**`» — ложный клейм «межфайловая половина снята везде».** Опровергнут замером: `grep -rnE '\.md\` §«' docs | wc -l` → 390 в 102 файлах (лидеры — `AnomalyJob.md` 25, `contracts.md` 23, `AuditRecord.md` 21, `statistics-aggregates.md` 17); класс вернулся с шагами 7-10 фазы 2. Клейм переписан в живой секции; довод «плотнейшие носители — гиганты» больше не описывает распределение (`risk-policy.md` 18, `manual-halt.md` 20) и снят вместе с дублем «почему не одним ходом».
- **«Места истины схемы» — история `algo_orders` / `attached_algo_orders` и строка `trading-core 29/0`.** Места истины заведены: `docs/models/domain/core/AlgoOrder.md` §Персистентность, `docs/models/domain/core/Order.md` §«Персистентность встроенной защиты»; у `trading-core` таблиц без места истины ноль (синк шага 7 фазы 2). Живой остаток — четыре таблицы `auth` / `market-data`.
- **«Боевые числа риск-аппетита» — хроника переезда носителя.** Дом чисел — строка `tenant_risk_appetites` (`services/trading-core/.../V1__trading_core_baseline.sql:98`); `RiskAppetiteStartupCheck` в `services`/`libs` отсутствует (только донор). Содержание — `docs/models/domain/core/Tenant.md` §«Структура — риск-аппетит», `.claude/work/history/2026-09-06-phase-2-step-7-trading-core.md`.
- **Пересказы домов и снятые редакции, вычищенные из живых секций** (содержание живёт в домах): «таблицы пусты» — донорский перечень носителей и «почему якорь отдельным пунктом» (`.claude/rules/pre-launch-schema-changes.md` §«Снятие обеспечено встречным якорем»); «Второй субъект» — рационал «отложенная цена посылки» (`docs/rules/api-access-policy.md` §«Принципал один»); «Боевые числа» — таблица трёх чисел с ролью в формулах (`docs/rules/risk-policy.md` §«Числа назначает держатель; пустое место — отказ»); «worst-case» — счётная иллюстрация 10R и врезка о кумулятивной базе (`docs/rules/risk-policy.md`); «`MARK`-only» — два упоминания о неверном доме `live-risk-protection.md` и адресе §«Остаточный грунт»; «Команды в клетках» — механизм дефекта и три оси энфорсера (`.claude/processes/roadmap-step-execution.md` §«Команда, объявленная воспроизводимой…», шапка `tools/command-carrier-check.py`); «Неразрешимые адреса» — пассажи о переносе строки и открытости форм (`.claude/processes/roadmap-step-execution.md` §«Обход идёт двумя формами поиска»); «Инкрементальный расчёт» — «что измерено / почему не закрыто кодом» (отчёт фокуса P2 шага 6 фазы 2); «Марк-/индексная» — провенанс решения держателя 2026-08-30 (D5(b)).

## Сработавшие оживители

| Секция | Условие, которое сработало | Чем доказано | Исход |
|---|---|---|---|
| Указатели javadoc → подсекция «Адрес пассажа в javadoc…» | снятие заморозки усиления измерения — вход шага 10 фазы 2 в `CODE` | `.claude/work/progress/phase-2-step-10-code-transition.md` существует; `phase-2.md` строка 10 — `GAPS_CLOSE_6` | взята в работу (`сейчас`), `закрыто-когда` — область `libs` у `tools/anchor-check.py` |
| Исполнимая форма предиката свечной целостности | ближайший шаг, трогающий загрузку свечей | шаг 6 фазы 2 (порт свечей) `DONE` 2026-09-05; `docs/spec/` без спеки свечной целостности, предикат по-прежнему прозой в `CandleGroup.md` | взята в работу (`сейчас`), `закрыто-когда=файл:docs/spec/candle-group-integrity.json` |
| Вопрос спеки, склеенный через «и» | ближайшая правка `docs/spec/statistics-aggregates.json` | файл правился коммитом `714a0217` (2026-09-11) без исправления вопроса (`question` на строке 3 несёт «и») | взята в работу (`сейчас`) |
| Неразрешимые адреса пассажей; Свод адресных перекрёстий `docs/**` | «курационный заход после закрытия шага 7» | шаг 7 фазы 1 закрыт 2026-09-03 (`history/2026-09-03-phase-1-step-7-deals-and-pnl.md`) | взяты в работу (`сейчас`); у обеих `закрыто-когда` по реестру / по грепу `§«` |
| Марк- и индексная цена как источник размещения | поддержка источником — марк- и индексная цена в добываемом снапшоте | `services/connector-okx`: `MarkPriceOkxResponse`, `IndexTickerOkxResponse`, `OkxConstants.MARK_PRICE_PATH` (`/api/v5/public/mark-price`), `MarketSnapshotMapper.markPrice()/indexPrice()`; `libs/domain-model` `MarketTicker.markPrice/indexPrice`; `docs/models/domain/other/MarketTicker.md:42-43` | взята в работу (`сейчас`), `закрыто-когда=нет-грепа:"PRICE_SOURCE_UNAVAILABLE"@libs/strategy-engine/src/main/**/*.java`. Оговорка: тикер `market/ticker` цен по-прежнему не несёт, поддержка пришла отдельными эндпоинтами |
| Места истины схемы для таблиц без §Персистентность | «ближайший шаг, который эти таблицы меняет» — шаги 4 и 6 фазы 2 | оба `DONE`, четыре таблицы без места истины (`grep -rl` по `docs/` — 0 у трёх, `memberships` только как путь API в `contracts.md:23`) | взята в работу (`сейчас`) |
| Команды в клетках таблиц у живых отчётов | — (не сработал; условие переписано машинно) | долг `tools/command-carrier-debt.txt` — 21 строка, все в `progress/phase-2-step-10-*` | перевооружена: `оживит=шаг:2-10:DONE` — гейт прогонит с `--статус 2-10=DONE` до переноса отчётов |

---

# Выписка закрытого — часть 3 (строки 1272-1919 прежнего `backlog.md`)

Для `.claude/work/history/2026-09-11-backlog-diet.md`. Одна строка — один
закрытый пункт: что закрыто, чем доказано, где живёт содержание.

## Закрытые пункты

- **Зонтик «Шаг «Безопасность» (Фаза 1, шаг 9) — форвард-материал»** —
  снят: шаг 9 фазы 1 `DONE` (`phase-1.md`), хроника —
  `history/2026-09-04-phase-1-step-9-security/`. Живые подсекции S1 и
  «Реакция на серию отказов доступа» промоутированы в `##`.
- **S1 — хроника «почему не сделано кодовой фазой», «что кодовая фаза
  добавила»** — снята как пересказ; факт (секрет принципала лежит в Vault
  per-profile под ту же ротацию) — `docs/rules/api-access-policy.md`
  §«Секрет принципала лежит в Vault per-profile». Пункт «approle вместо
  root/dev-token» снят: целевые сервисы входят в Vault методом Kubernetes
  (`services/*/src/main/resources/application.yaml`,
  `deploy/base/data/vault.yaml`); «вынос токена из run-config» — донорский
  остаток, уходит с донором.
- **S2. Auth-инфраструктура** — закрыто: `SecurityFilterChain`, точки входа
  отказа с записью `AccessDenial`, принципал из Vault построены кодовым
  заходом 2026-09-04 (хроника —
  `history/2026-09-04-phase-1-step-9-security/phase-1-step-9-code-pass-access-policy.md`);
  у семи целевых сервисов `config/SecurityConfig.java` на
  `oauth2ResourceServer`. Остаток «`@PreAuthorize` на операциях» дублировал
  §«Второй субъект поверхности — пересмотр посылки о единственном
  принципале» бэклога — там и живёт.
- **Контур доступа — два названных ограничения (CSRF при Basic, хэш на
  каждом вызове)** — закрыто: оживитель «переход на bearer — шаг 4 фазы 2»
  сработал (`phase-2.md`, шаг 4 `DONE`); `docs/rules/api-access-policy.md`
  §«Что снимается на шаге 4, а что на фазе 5» объявляет, что оба
  ограничения уходят вместе со средством, `docs/architecture/platform.md`
  («Basic-аутентификации и CSRF-оговорок больше нет»). Донорский
  `ApiAccessSecurityConfig` заморожен до удаления донора.
- **Реакция на серию отказов — «Причина», «Вторая величина»** — доводы
  сведены к указателям на дома (`AccessDenial.md` §Персистентность,
  `JournalCleanupJob.md` §Границы); задача жива.
- **Форвард-материал шагов 7/8 и фазы 3 — зонтик** — снят; указатели
  (`.claude/processes/api-docs-completion.md`, В-4/В-5 в
  `history/2026-07-14-claude-docs-curation.md`) остаются в history.
- **Риск-преконтроль: ссылка на INSTR-Q2** — снята: вопроса в
  `open-questions.md` нет (закрыт, `history/2026-05-31-…`). «Фаза 3» для
  риска на портфель переведена в фазу 6 по действующему роадмапу.
- **Шаг 8 — «Остаток холдов L3/L4»** — закрыто целиком: (1) проактивная
  детекция и (2) биржа-широкая реконсиляция закрыты шагом 8 фазы 1
  (`history/2026-09-04-phase-1-step-8-anomaly-job.md`); (3) аудит ручного
  un-hold переехал в §«Класс события `HoldReleased` и его ручная тропа».
- **Шаг 8 — «Остаток kill-switch (ANOM-Q2)»** — закрыто: AnomalyJob-путь и
  общебиржевая orphan-сверка (`A2`, `A7`, `A9`) построены; **PnL-финализация
  `EMERGENCY_CLOSED` построена** — `MarkDealEmergencyClosedExecutor` считает
  итог через `DealResultCalculator` (`writeBestEffortResult`), ребро
  `ERROR → EMERGENCY_CLOSED` пишет причину той же транзакцией
  (`docs/lifecycles/Deal.md`, `docs/spec/deal-lifecycle.json`). Порог «серия
  неудач» ушёл в секцию `TradeGuardJob`. Ссылки ANOM-Q2 (вопроса нет) и
  STRUCT-Q1 (вопрос о порогах структуры рынка, не о серии неудач) сняты как
  ошибочные.
- **Шаг 8 — «Идемпотентность `AnomalyReport`»** — закрыто: построена шагом 8
  (индекс `V25`, дом — `docs/models/domain/other/AnomalyReport.md`);
  остаток ANOM-Q6 живёт в `open-questions.md`.
- **Шаг 8 — «Инвентарь периодических джоб — держать сверенным»** —
  датированный замер монолита на 2026-08-03 снят: в порте джобы разложены
  по `market-data`, `trading-core`, `audit-statistics`, пометки состояния —
  в компонент-доках (сам пункт так и предписывал), отдельного
  инвентаря нет. Содержание замера: `AnomalyJob` материализован
  2026-09-03 одним детектором, на открытии шага 8 перечень объявлен
  целиком — **ступень и радиус выводятся** тремя ратифицированными осями
  (дом — `docs/components/AnomalyJob.md` §«Что ищет»); `MarketPhaseJob`
  снят (`docs/models/domain/other/MarketPhase.md`), `TradeFeeSyncJob`
  отвергнута (`docs/rules/pnl-reconciliation.md` §«Что сверяется, когда,
  кем»).
- **Ось упущенных возможностей — «Разрешимость R-выборки»** — сжата до двух
  строк: довод [SR ∝ √(независимых ставок/год), Carver ST гл.2 с.59-60] —
  трейдинг-довод, не задача; «фаза 3» переведена в фазу 6 (портфель), как
  указывает `open-questions.md` (PNL-Q1).
- **Перф-форвард — «[MINOR] Дублирующий тикер-REST в entry-скане»** —
  снято: описывал донора. В порте классов `MarketPhaseService.buildContext`,
  `MarketConditionContextFactory.build`, `EntryScannerJob.scanInstrument`
  нет; `trading-core` тикер не читает нигде (grep 0), контекст и скан берут
  `MarketFeatures` одним чтением (`MarketFeatureService.readForCalculation`
  / `readForEntry`, `CalculationContextFactory`), фаза считается в
  `market-data` без тикера.
- **Перф-форвард — L4 burst** — имена донора (`fireExchange`,
  `findActiveByExchangeId`, «~9 запросов») переписаны на as-built
  (`KillSwitchService.fireExchangeAccount`,
  `DealDataService.findNonTerminalByExchangeAccountId`); источник ревью —
  `history/2026-07-03-phase-1-step-6-fsm-orchestration/phase-1-step-6-code.md`
  §Доработка холд-дельты. Перечень двенадцати точек внешнего вызова в
  транзакции — отчёт фокусов `CODE` шага 7 фазы 2, §D1.
- **Унификация инфраструктуры джоб — проект состава** (`ScheduledJob`,
  `JobLock`, `InProcessJobLock`, `AdvisoryJobLock`, код-ревью заход 2
  2026-07-01) — снят как пересказ: в коде ни одного (grep 0 по
  `services/`, `libs/`); дом операционной оболочки —
  `docs/components/DealOrchestratorJob.md` §«Операционная оболочка».
  Встречный якорь и чек-лист снятия оставлены.
- **Преамбула «Шаг 7 (сделки и P&L) — исполнительный хвост»** (45 строк:
  «концепция закрыта», «гейт `CODE` упирается в два условия», «калибратор
  допуска гейтом быть перестал», «цена доведения до `CODE`») — хроника
  пройденного гейта шага 7 фазы 1 (`DONE`,
  `history/2026-09-03-phase-1-step-7-deals-and-pnl.md`). Реестр предусловий
  — `.claude/tests/source-api/okx/code-preconditions.md`; снятие допуска —
  §«Прод-рубеж — снятие разведочного режима допуска сверки» бэклога;
  разведка разрывов —
  `.claude/notes/2026-08-23-разрывы-спека-кода-на-тропе-живой-сделки.md`.
  Заголовок оставлен тонкой секцией: на него ссылаются `HoldService.md`,
  `code-preconditions.md`, `plan.md`.
- **Гистерезис — провизорные значения (1/2/3 тика)** — сняты из бэклога как
  пересказ дома `docs/components/AnomalyJob.md` §«Такт и гистерезис».
- **Слепота — абзац «голодание тика на общем потоке планировщика»** —
  закрыт заходом K16 шага 10 фазы 2: пул ядра — величина конфигурации,
  вывод охраняется пробой, пары джоб пройдены
  (`.claude/work/progress/phase-2-step-10-code-pass-k16.md`).
- **Хвост шага 8 — «Мультибиржевая корректность прохода»** — закрыто шагом 7
  фазы 2: проход посчётный, срезы читаются ключами счёта
  (`AnomalyJob.java`, цикл по `findTradingAccounts()`;
  `docs/components/AnomalyJob.md` §«Проход идёт по биржевым счетам, а не по
  площадкам»). Остаток — порядок обхода счетов — своим `####`.
- **Лестница биржевых safety-состояний — преамбула и Т11** — закрыто:
  мягкая биржевая ступень построена 2026-09-03
  (`history/2026-09-03-soft-exchange-rung.md`); дом правила —
  `docs/rules/exchange-hold.md`, сигнал — `docs/components/models/HoldSignal.md`.
- **Лестница — «реакцию controlled-исключений оставить на
  `KillSwitchService.fireExchange`… `exchangeTradeBlock(...)`»** — закрыто
  портом: `SafetyHoldCoordinator.react(...)` →
  `KillSwitchService.fireExchangeAccount`; `ControlledExchangeException`
  ведёт счёт в `TRADE_BLOCKED` с flatten
  (`docs/rules/controlled-exchange-exceptions.md` §«Реакция — безусловная
  биржевая ступень 2»). Символа `exchangeTradeBlock` в as-built нет.
- **Лестница — «ввести блок-сет ступени 2»** — закрыто:
  `docs/rules/exchange-hold.md` §«Состав реакции» («все команды торгового
  доступа; исключение — teardown kill-switch»); код —
  `HardRungShutdownReasonResolver`, `DealDataService.findIdsUnderAccountRung`,
  `RiskValidator.checkSafetyRung` (порт шага 7 фазы 2).
- **Лестница — «перевесить `markErrorStopless` / гейт
  `TrancheEntryFinalizedHandler` на ступень 2»** — закрыто под другим
  именем: `TrancheEntryFinalizedHandler.java` при отсутствии обязательства
  защиты запрашивает `HoldSignal.exchangeAccount(EXCHANGE_LIVE_RISK_UNCOVERED)`
  — биржевую ступень 2 (`docs/components/TrancheEntryFinalizedHandler.md`,
  `docs/rules/live-risk-protection.md` §«Кто наблюдает и кто запрашивает»).
  Символа `markErrorStopless` нет ни в коде, ни в доках.
- **Лестница — «построить ручную сервисную поверхность… Остаётся
  постановка»** — закрыто портом шага 7 фазы 2: `SafetyController`
  `POST /halts` (`raise`, `raiseFull`) и `/halt-clearances`; снятие
  двухходовое под `HoldClearanceGate`
  (`history/2026-09-06-phase-2-step-7-trading-core/phase-2-step-7-chronicle.md`).
  Дом — `docs/rules/manual-halt.md`, `docs/spec/manual-halt.json`.
- **X1. `ShutdownReason.MANUAL_STOP`** — закрыт: перечень сведён к
  `STRATEGY_DELETED`, `MARKET_DATA_EXPIRED`
  (`history/…/phase-1-step-7-gaps-close-31.md`; дома —
  `docs/models/domain/aggregate/Deal.md`, `docs/lifecycles/Deal.md`).
- **X2. `Position.CloseReason.MANUAL_CLOSE`** — закрыт решением держателя
  2026-08-30, `CODE`-дельта исполнена 2026-09-03 (ручные значения и
  fallback-значения без производителя сняты из перечней; дом —
  `docs/rules/manual-halt.md` §«Точечных ручек в фазе 1 нет»).
- **X3. Носитель свободного повода держателя** — снят: требование
  отличимости закрыто кодом; условная заметка «если понадобится — кандидат
  `message`» без оживителя, дом — `docs/models/domain/other/AnomalyReport.md`.
- **X11. Исключающий ключ доведения недоделанного** — закрыт портом:
  `SafetyHoldCoordinator.react(signal, context, teardownRetry)` плюс
  исключающий ключ объекта радиуса (хроника шага 7 фазы 2); распределённая
  форма — условие снятия у §«Унификация инфраструктуры джоб».
- **Хенд-оффы Н9 — хроника «пункт переформулирован» (X9), «после Н9
  существуют оба» (X7)** — сжаты; задачи живы.

## Сработавшие оживители

- **«Контур доступа — два названных ограничения»** — условие «шаг 4 фазы 2»
  сработало; исход: **закрыта** (см. выше). Входящий указатель
  `docs/rules/api-access-policy.md` — в fixes.
- **Лестница: блок-сет ступени 2, перевес гейта на ступень 2, ручная
  постановка; X11** — условие «порт ядра / `CODE`» сработало шагом 7 фазы 2;
  исход: **закрыты** (предмет построен, доказательства выше).
- **X6. Носитель пятого признака** — условие «тем же шагом, что
  `CODE`-дельта о терминальном проходе» сработало (терминальный гейт
  `DealTerminalGate.riskProvenAbsent` построен), предмет **не** построен:
  `docs/rules/manual-halt.md` §«Полнота названа и ограничена» по-прежнему
  «4 признака из 5». Исход: **перевооружена** — `оживит=сейчас` в
  «Хенд-оффы узла Н9».
- **Пункты шага 8 с оживителем «на шаге» / «спроектировать на шаге»** —
  шаг 8 фазы 1 закрыт без них; исход: **перевооружены** — В-1
  `cancel-all-after` (`рубеж:prod`), переоценка инварианта A13
  (`шаг:2-11:DONE` | первый период), Stage 3 FSM (`шаг:4-2:открыт`),
  ветвь «отчёт есть, ступень не поднята» (`шаг:2-11:открыт`; прежний
  оживитель «заход по проактивной детекции» устарел — порт прошёл без
  него).
- **`TradeGuardJob`** — оживителя не было; назначен `рубеж:prod` |
  наблюдение серии отказов.

---

# Выписка закрытого — часть 4 (`backlog.md` 1920-3116: CODE-дельта шага 7 фазы 1, грунт `integrator`, рантайм-верификация и форвард)

Диапазон — три подсекции `##` «Шаг 7 (сделки и P&L) — исполнительный хвост»:
`### CODE-дельта шага` (1085 строк), `### Грунт integrator для шага 7` (21),
`### Рантайм-верификация и форвард` (91). Секция была рабочей инструкцией
исполнителя `CODE` шага 7 фазы 1, дописывавшейся каждым закрытием
(`GAPS_CLOSE_10` … `_29`, кодовые заходы 2-5); всё её содержимое, кроме
восьми позиций ниже, построено портом ядра (шаг 7 фазы 2) и проверено
грепом по `services/**` / `libs/**`. Полный текст секции — единственный
носитель, где видно, какие редакции снимались по ходу (F14/F16
`DOCS_CHECK_28`, A1 `DOCS_CHECK_33` и др.); рекомендуется сохранить его как
детальный артефакт в
`.claude/work/history/2026-09-03-phase-1-step-7-deals-and-pnl/phase-1-step-7-code-delta-worksheet.md`,
а не выбросить.

## Закрытые блоки — чем доказано, где живёт содержание

- **Преамбула «Форма позиции: имя + указатель на дом»** — норма формы уже в
  домах (`.claude/rules/closed-work-transfer.md` §«Диета рабочих файлов»,
  `.claude/rules/policy-home.md`); кейс семи снятых редакций — история
  `DOCS_CHECK_28`.
- **Дельта кодового захода 2** (`stepRetryGated`, `INSTRUMENT_SAFETY_HOLD`,
  `@NotNull distancePercents`, `STRATEGY_ACTION_ALLOCATION_NOT_DECLARED`,
  `STRATEGY_STEP_ACTIONS_EMPTY`) — `StrategyStepSelector.retryGated`,
  `INSTRUMENT_SAFETY_HOLD` в 5 файлах ядра, оба кода в
  `services/strategies/…/StrategyDefinitionValidator`. Дома:
  `docs/rules/strategy-step-once-per-episode.md`,
  `docs/rules/strategy-validation.md`, `docs/rules/instrument-hold.md`.
- **Дельта `GAPS_CLOSE_27`, узел Н5** — `riskProvenAbsent` в 5 файлах;
  `DealFinalizationStateStatus` снят (0 вхождений);
  `INSTRUMENT_SETTLE_CURRENCY_MISSING` в `RiskValidator`; аварийный терминал
  `MarkDealEmergencyClosedExecutor` с тремя ветками числа;
  `closeReasonCandidate` в `docs/spec/external-status-resolution.json`.
  **Не закрыт** один пункт — операнд `episodes[].exitAt` (перенесён живым).
- **Дельта `GAPS_CLOSE_26`** — `DealResultCalculator` (6 файлов), четыре
  пары сверки по `docs/spec/pnl-reconciliation.json`, курс пишет
  `RefreshBillsExecutor`, `ladder.trancheExposure`, `trancheStopCurrent`,
  частичный ключ `deal_strategy_action_states`, писатель `Order.positionId`
  — `RefreshPositionExecutor`, база риска `riskBase`/`riskBaseCurrency`
  (`RefreshBalanceExecutor` + оба терминальных исполнителя), счётчик
  `consecutiveLossCount`, `TranchePrecheckHandler`. **Не закрыт** реджект
  `STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK` (перенесён живым).
- **Гигиена `_26`** (27 битых javadoc-ссылок на `docs/decisions/`, 101
  вхождение процессной арматуры в доках) — донор заморожен; в `services/**`
  осталась одна ссылка (`StrategyDefinitionValidator.java:88` →
  несуществующий `docs/decisions/strategy-materialization-and-validation.md`)
  — дом `backlog.md` §«Указатели javadoc в несуществующие доки»; арматура —
  §«Свод адресных перекрёстий» / G4.
- **Многоэпизодная сделка `GAPS_CLOSE_18`/`_19`** — `Deal.positions`,
  `livePosition()` в 16 файлах, `positionReopenAllowed`,
  `billsFetchedThrough`, линковка по `cash-flow-linkage.json` §`inWindow`,
  единый пересчёт четырёх чисел, `SystemActionExecutor` пишет `SKIPPED`,
  `PNL_RECONCILIATION_MISMATCH` в 5 файлах. Дома: `Deal.md`,
  `lifecycles/Position.md`, `TrancheManagingHandler.md`,
  `docs/rules/pnl-reconciliation.md`.
- **Дельта `GAPS_CLOSE_28`** — `closeOutcome` в 8 файлах; коды
  `UNRECOGNIZED_CLOSE_TYPE`, `RISK_BENCHMARK_MISSING`,
  `RESULT_CURRENCY_UNVERIFIABLE`, `RESULT_CURRENCY_MISMATCH`; FIFO по
  `DealTranche.id`; `ExchangeContourProperties`; числа допуска в
  `application.yaml`. Дома: `docs/models/mapping/PositionCloseResult.md`,
  `docs/spec/position-close-outcome.json`, `DealTranche.md`, `Exchange.md`.
- **Дельта `GAPS_CLOSE_20`** — `coverageProvenThrough` монотонное;
  `getServerTime()` у `RefreshBillsExecutor` и `EntryScannerJob`; ребро
  `* → ERROR` карв-аутом; `plannedRiskEquityBase`; `orders.position_id`;
  три лимита `RISK_PER_*` (4 кода); `external_fee_level`; трейлинг через
  `updateFromSnapshot`; `trigger_price_type` в 23 файлах; `stopCurrent`
  поногово; `ExitOutcome` в `libs/strategy-engine`. **Не закрыт**: читатель
  `exitOutcome` и журнальные коды `PARTIAL_EXIT_*` (перенесены живым).
- **CODE-дельта `GAPS_CLOSE_21`** — `strategySimultaneousRiskPerDealPercent`
  (7 файлов), три `STRATEGY_*`-кода валидатора,
  `NOTIONAL_HEADROOM_SHARE = 0.01` (`Constants.java:31`),
  `reviseLiveExecutions` из трёх точек, `uk_position_deal_external
  (deal_id, external_id, external_created_at)` в `V1__trading_core_baseline.sql`,
  глобальный максимум у риск-аппетита тенанта (`RiskValidator:134`).
- **CODE-дельта `GAPS_CLOSE_22`** — `CashFlowCategory` (9 файлов),
  `UNCLASSIFIED_CASH_FLOW`, `separateFeeGranularity`; комментарии `§I3` —
  только в замороженном доноре.
- **CODE-дельта `GAPS_CLOSE_23`+`_24`** — `isActiveLike`, четыре точки
  покрытия, `STRATEGY_PROTECTION_COVERAGE_INCOMPLETE`,
  `PROTECTION_COVERAGE_REDUCED`, `PROTECTION_LADDER_STEP_BELOW_MIN_SIZE`,
  `bookDepthAtPlacement`, `externalAskSize`/`externalBidSize`; комментарий
  «Risk-валидацию не проходит» снят (0 вхождений). Порядок ног
  entry-`REPLACE` по филлу НЕ ветвится — дом
  `docs/rules/replace-not-amend.md` §«Порядок ног — по риск-классу действия».
- **CODE стадий 1-2 — остаток** — сам текст объявлял закрытие; итог —
  `.claude/work/history/2026-09-03-step-7-code-tail-closures.md`.
- **CODE узла добычи положения закрытия** —
  `PositionCloseResultExternalSnapshot` (6 файлов), `REFRESH_POSITIONS_HISTORY`
  не заведён, `billsWindowBegin`, окно `RefreshBillsExecutor` по
  `getServerTime()`, ветвь «живой позиции нет и строки нет» в
  `RefreshPositionExecutor.md`.
- **CODE R-слота и формулы риска** — `plannedRiskAmount` (9 файлов),
  `riskBenchmarkAvailability` (7), `liquidationDistanceRatio` (4),
  `DealRiskNumbersService`; javadoc `RiskValidator.checkRiskCreatingEntryProtection`
  уже в принятой форме. Ремодел Р3 (`REPLACE_ACTION`) припаркован вне
  диапазона — §«Названные ограничения кодирования шага 7» (Т2-в) и
  §«Хвост шага 4» (`ServiceCommandFactory`). Конвенционный остаток без
  гейта: предикат `Order.isEntryLeg()` не заведён, две инлайновые
  дизъюнкции по `Type` остались (`PriceCalculator:107`,
  `DealRiskNumbersService:132`) — в бэклог не перенесён (`codestyle`
  §«Вложенность и rich-модели», чинится при касании).
- **Прочая дельта валидаций `GAPS_CLOSE_16`/`_17`** —
  `RECONCILIATION_OPERAND_MISSING` удалён (0), `POST_MORTEM_HARVEST_EXHAUSTED`
  не введён, `hasLiveEpisode`, реджект `contractType ≠ LINEAR`
  (`InstrumentExternalRulesMapper:88`).
- **`attachedProtection` не доезжает до payload (риск денег)** —
  `CreateOrderActionExecutor:151` заполняет `attachedProtection`;
  `CreateOrderCommandPayload` несёт плановые числа (`:57-81`).
- **Тропа выхода по условию / действию** — `EXIT_ACTION` (15 файлов),
  `StrategyPositionAction`, `ExitActionExecutor` эмитит
  `CANCEL_ORDER_COMMAND → CLOSE_POSITION_COMMAND` (`:114-122`).
- **`REPLACE`/`CANCEL` — исполнителей нет** — `CancelAlgoOrderActionExecutor`
  есть; `REPLACE_ACTION` припаркован вне диапазона (см. выше).
- **`TrancheManagingHandler` не наблюдает состояние** —
  `REFRESH_DEAL_CONTEXT_ACTION` в `SystemActionExecutor`,
  `TrancheActionDisposition`.
- **`FAIL_SAFE` без потребителя** — `DealActiveHandler:214` читает
  `StrategyStepType.FAIL_SAFE`.
- **Мёртвые поля `CreateOrderCommandPayload`; координатные колонки курса** —
  полей нет в payload ядра; `applied_rate_candle_instrument` в схеме.
- **`SizeCalculator` — закрытая форма** — `perContractRisk`;
  `SizeCalculator:173` отказывает при неположительном знаменателе.
- **CODE журнальных аномалий** — закрыт шагом 8 фазы 1; javadoc `HoldScope`
  описывает радиус и ссылается на уровни `error-handling-policy.md`
  §«Внутренняя градация: четыре уровня» — согласован. Колонка `kind`
  снята как редакция (реестр `tools/retired-check.py`, запись «поле
  `kind` как носитель природы факта отчёта»).
- **CODE cross-ccy + хэнд-офф `integrator` «носитель курса»** —
  `appliedRate`/`rateStatus` у `RefreshBillsExecutor`; слот `MG7.5` реестра
  предусловий собран 2026-08-30.
- **Список исключений сверки — содержание** — слот `AG6.2` собран
  2026-09-02 обеими половинами;
  `ExchangeContourProperties.reconciliationExclusions` непуст
  (`application.yaml:307-310`); справочник —
  `docs/integrations/okx/rules/cash-flow-categories.md`.
- **CODE fee-wiring (N9)** — `TradeFeeRate` (libs), `TradeFeeRateSyncJob`/
  `Service`/`Properties`, `TradeFeeRateMapper:69 .negate()`,
  `FEE_RATE_UNAVAILABLE` (11 файлов), `refreshCount`; компонент реестра
  контакта с кодом введён 2026-09-06. **Не закрыта** ступень по несвежести
  ставки (перенесена живым вместе с развилкой «через ребро или мимо»).
- **CODE узла холда** — `Instrument.SafetyRung.ENTRY_BLOCKED` (9 файлов),
  `RetryBudgetExhaustedException` + выделенный `catch` в
  `DealOrchestratorJob:249-253`, `HoldRungEdgeService`, ручное снятие
  `ManualHaltService`.
- **CODE-дельта `GAPS_CLOSE_10`** — `resultProfitCurrency` (6 файлов),
  `externalCloseAveragePrice` (5), `UNCLASSIFIED_CASH_FLOW`, колонки
  ставок `varchar(64)`, состав цикла добычи из `DealContext`,
  `billsWindowBegin` у `SubmitOrderExecutor`.
- **CODE-дельта катастрофического потолка** —
  `strategyCatastrophicRiskPerDealMultiplier` (6),
  `globalCatastrophicRiskPerDealMultiplier` (8), `DEAL_NOTIONAL_EXCEEDED`,
  `STRATEGY_CATASTROPHIC_MULTIPLIER_ABOVE_GLOBAL` в валидаторе `strategies`.
- **CODE-дельта риск-контура, узел Н6** — `actNotional`,
  `STOP_DISTANCE_BELOW_FLOOR`, `netCloseAllowed`,
  `TrancheEntrySubmittedHandler → EXIT_PENDING`, `placementRole`,
  `LOSS_LIMIT_NOT_CONFIGURED`, `PriceCalculator:387` отказывает
  `PRICE_SOURCE_UNAVAILABLE` на `MARK_PRICE`/`INDEX_PRICE`, `BREAKEVEN`,
  `breakevenLevel`, `level_count not null` (`strategies` V1:134). **Не
  закрыты** два create-кода — `STRATEGY_PRICE_SOURCE_UNAVAILABLE`,
  `STRATEGY_BREAKEVEN_NOT_A_TRANSFER` — в `services/strategies` отсутствуют
  (перенесены живым одной секцией с `NOT_MARK`).
- **Числа риска отчётности от плановой цены — RISK-Q1** — указатель без
  задачи; дом вопроса — `open-questions.md` §RISK-Q1, задача появится
  решением.
- **Форвард вне шага 7 (B6: top-level эхо attached-защиты, `slTriggerPxType`)**
  — решено в доме: `docs/models/mapping/Order.md` §«`OrderExternalSnapshot`
  → `Order`» (эхо в домен не приземляется; ценовая база триггера сверяется
  эхом).
- **Грунт `integrator` — преамбула** — гейтящие слоты собраны (все строки
  реестра `code-preconditions.md` ✅); история —
  `2026-09-02-env-revival-ground-batch.md`. П. 3 (семантика `actualPx`,
  `M15.7`) — дом ожидания `plan.md` §M15.7 (⏳, не гейтит);
  `docs/models/mapping/AlgoOrder.md` уже разводит `actualPx` как факт.
  П. 10 (`instId`/`instType` в `data[]`) — `PositionsHistoryOkxResponse:26`
  несёт `instId`; ограничение «корректность держит фильтр запроса» уже
  записано как исход.
- **N11 — рантайм-верификация агрегации** — слот `AG1.5` собран 2026-08-30
  (`observations/AG1_5.md`); текст бэклога стоял «⏳ PENDING» устаревшим.
- **Рантайм-хвост §AG1.5** — RQ-4 (`AG3.4`) собран (п. 9 реестра); `AG3.5`,
  `AG12.4`, `AG12.5`, `M1.7` — негейтящие, статус ведёт `plan.md`.
- **Калибровка epsilon (N10)** — дубль §«Прод-рубеж — снятие разведочного
  режима допуска сверки» (тот же перечень 0.01 / 0.5 % / `k`, та же
  оговорка «`k` гейтом не является»).
- **Вход в market-maker-программу → ось запроса `trade-fee`** — инвариант и
  условие держит `docs/integrations/okx/contracts/trade-fee.md`
  §«Инвариант organic-base-rates».
- **`elpMaker` → `rpiMaker`** — `trade-fee.md` держит окно параллельных
  имён; поле unused по `docs/rules/raw-exchange-dto-boundary.md`.

## Две снятые конструкции — переносить нельзя

- **`Instrument.externalModifiedAt` как измеритель свежести ключа группы**
  (писатель — синк листинга; `NULL` ⇒ предусловие entry-скана). Заменена:
  свежесть мерится `TradeFeeRate.externalModifiedAt`
  (`docs/models/domain/other/TradeFeeRate.md`), радиус —
  `docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии» по режиму
  отказа синка. В `services/market-data` писателя нет, предусловия в
  `EntryScannerJob` нет, правила в доках нет. Строка «предусловие entry-скана
  «ключ группы подтверждён»» дельты `GAPS_CLOSE_10` снята той же
  конструкцией.
- **`Order.Type.REDUCE_ONLY` как третье значение бизнес-типа** + валидация
  пары `Type ↔ positionReducingOnly`. Заменена: `Order.Type` в
  `libs/domain-model` несёт только `ENTRY` / `ENTRY_ATTACHED_STOP_LOSS`
  (`Order.java:243-249`); reduce-only живёт признаком
  `positionReducingOnly` + `SizeMode.REDUCE_ONLY` +
  `RiskCheckCode.PARTIAL_EXIT_NOT_REDUCE_ONLY`.

## Сработавшие оживители

- **Хэнд-офф `integrator` «носитель курса» (cross-ccy)** — условие «собрать
  грунт» сработало: слот `MG7.5` собран 2026-08-30. Исход — закрыт.
- **Список исключений сверки — «непустой список — предусловие CODE»** —
  слот `AG6.2` собран 2026-09-02, список в конфиге непуст. Исход — закрыт.
- **N11 (`AG1.5`)** — собран 2026-08-30. Исход — закрыт.
- **RQ-4 (`AG3.4`)** — собран (п. 9 реестра). Исход — закрыт.
- **Грунт `integrator` — «гейтящие позиции собраны прогонами
  2026-08-30 — 2026-09-03»** — сработало по тексту самой секции. Исход —
  секция закрыта, два негейтящих пункта — к домам ожидания (`plan.md`).
- **Форвард B6 (эхо attached-защиты)** — «решить, нужен ли носитель»:
  решено домом маппинга. Исход — закрыт.
- **Развилка «ступень несвежести — через ребро или мимо»** — оживитель
  «CODE fee-wiring» сработал (компонент закодирован 2026-09-06), но ступень
  не построена и развилка не решена. Исход — перевооружена: секция
  «Ступень `ENTRY_BLOCKED` по несвежести ставки комиссии — писателя нет»,
  `оживит=сейчас`.
- **N13, H6/H17, матрица искажений, авто-снятие холда** — прежние
  условия («шаг ожидаемости», «установившийся режим», «фаза 2») не
  срабатывали: адресат — фаза 4 (`фаза:4:открыта`) либо наблюдение;
  перевооружены машинными условиями.

## Материал для PNL-Q1 (матрица искажений, снятая из бэклога)

Матрица «оси (исходы / возможности) × стороны (оптимистично /
пессимистично)»; крены на разных осях не компенсируются — сравнение
бэктест ↔ live двусторонне несопоставимо, мерить одно, не зная другого,
нельзя. Владелец — фаза ожидаемости (фаза 4).

- **исходы × оптимистично:** H6 null-drop (`docs/rules/pnl-reconciliation.md`),
  N11 недосчёт агрегации, опущенный гэп-проскок (`docs/rules/risk-policy.md`);
- **исходы × пессимистично:** flatten чужих здоровых сделок при
  `Exchange.TRADE_BLOCKED` (ступень 2) — рыночное закрытие в момент,
  некоррелированный с рынком: правый хвост R усекается, измеряемая
  ожидаемость занижается. Радиус механизма не сужен: ревизия держателя
  (`GAPS_CLOSE_18`) вернула controlled-исключения и safety-каскад на ступень
  2 с flatten — цена названа и принята (`docs/rules/exchange-hold.md`);
- **возможности × пессимистично:** taker-консерватизм при maker-входах =
  систематический недосайзинг (`docs/rules/pnl-reconciliation.md`); цена
  пропуска входа под реджектом / холдом оценена в ~0 — корпус против
  [Tharp гл.6, гл.11; Kaufman гл.1]; промо нулевой комиссии не видно в
  `trade-fee` ⇒ прогноз завышает издержку ⇒ недосайзинг
  (`docs/integrations/okx/contracts/trade-fee.md` §«Прочие ремарки офдока»);
- **возможности × оптимистично:** окно несвежести ставки двусторонне — при
  понижении тира внутри окна (0-24 ч) прогноз комиссии занижен ⇒ позиция
  больше положенной [Vince гл.1]; лечится сокращением порога свежести —
  калибровка вместе с величиной порога.

---

# Выписка закрытого — часть 5 (строки 3117-3934 `backlog.md`, 22 секции)

Для `history/2026-09-11-backlog-diet.md`. Формат: что закрыто — чем доказано — где живёт содержание.

## Закрытые пункты

### Хвост шага 4 фазы 1 (CODE-отложения) — секция снята целиком

- **Вводка и хроника** (источник `history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-sync-docs-from-code.md` §DEFER; D-B3/D-M1 закрыты на шаге 6) — хроника, не задача; содержание — в названных history-файлах.
- **ClosePosition settle ccy** — закрыто портом: `services/trading-core/.../ClosePositionExecutor.java:67` передаёт `getExternalSettlementCurrency()`, коннектор принимает `settleCurrency` (`OkxSourceReader`, `ExchangeGateway`).
- **`ExternalStatusReason` — enum вне доменного слоя** — закрыто портом: файл живёт в `libs/domain-model/src/main/java/com/example/tradingbot/domain/resolve/ExternalStatusReason.java` (доменный слой); донорская копия заморожена с донором.
- **`ServiceCommandFactory`: REPLACE-оркестрация** — донорская конструкция (`ServiceCommandFactory` в `services/` нет); живой остаток «исполнителя `REPLACE_ACTION` в ядре нет» перенесён буллетом «Т2-в и REPLACE-нога» в §«Названные ограничения кодирования шага 7 — возврат по появлению носителя» (туда же — производитель `REPLACED_BY_STRATEGY`, находка C12 линзы C прогона, закрывавшего шаг 7).
- **Refresh algo: external-поля дерева `condition`** — закрыто решением порта: `services/trading-core/.../mapping/AlgoOrderMapper.java` `updateFromFetched` намеренно игнорирует `condition` («площадка отдаёт эхо своих полей, а не нашу декларацию»); факты срабатывания верхнего уровня переносятся. Дом — javadoc маппера и `docs/rules/external-status-resolution.md`.
- **Evidence-cycle пагинация** — жива, вынесена в свою секцию §«Пагинация evidence-цикла и архивные звенья `REFRESH`-команд ядра» (доказательство: `orders-history-archive` в `services/connector-okx` — 0 вхождений).
- **Рантайм-прогон через `OkxProxyController`** — закрыто: контроллер только в `donor/src/main` (заморожен); сырой вызов уходит коннектору ходом удаления донора (`.claude/rules/knowledge-ownership-by-service.md`, строка `docs/models/api/`).

### Из адверсариального ревью шага 4 — подсекция снята

- **D-M5/R5 (пагинация evidence-цикла)** — дубль пункта выше; живёт в §«Пагинация evidence-цикла…».
- **P-M3 (`getRequiredById` грузит attached)** — закрыто по построению порта: `OrderRepository` ядра без `join fetch` attached.
- **D-M4 (корроборация RefreshPosition)** — жива, своя секция §«Корроборация закрытия позиции по пустому ответу источника».
- **Батчи/churn (`saveAll`, upsert баланса)** — перф-форвард; порт воспроизвёл delete+insert (`BalanceContainerDataService.java:66`, `BalanceRepository.deleteByBalanceContainerId`) — перенос в §«Перф-форвард» (см. fixes).
- **D-m1/D-m2 (clock-skew, эхо `clOrdId`)** — закрыто: в коннекторе `skew` не воспроизведён, цикл добычи перестроен (`docs/models/mapping/Order.md`).
- **m2 (`getRequiredByInternalId` Order/AlgoOrder не вызывается)** — закрыто: в ядре вызовов 0, метод не портирован.

### Агентское ревью всей кодовой базы (2026-07-02) — секция снята

- Хвост major/minor описывает 420 Java-файлов **донора**; донор заморожен, «правится при касании файлов в `CODE`» больше не наступает. Воспроизведение дефекта в `services/` — предмет фокусов `CODE` шага порта. Заметка `.claude/notes/2026-07-02-code-review-full-codebase.md` — в `history/` по `curation.md` п. 4 (см. fixes).

### Ретро-ревью шагов 1-3 — секция снята, воспроизведённое вынесено

- **Шаг 1, `syncOverlapBars`** — закрыто: свойство не портировано (`services/` — 0).
- **Шаг 1, `repairAttempts` in-memory** — воспроизведено портом (`services/market-data/.../CandleLoader.java:55`) → §«Дефекты донора, воспроизведённые портом».
- **Шаг 1, отброшенный return `saveCandles`, двойной `findByStatusIn`** — донорские детали, в порт не переносились.
- **Шаг 2, декартов `join fetch` дерева** — воспроизведено (`services/strategies/.../StrategyRepository.java:26`) → перф-форвард (fixes, §«Перф-форвард»).
- **Шаг 2, 500 вместо 422/409 при гонке «одна ACTIVE»** — закрыто: `uk_strategy_active_per_instrument` не портирован, гонка отвечается 409 (`StrategyLifecycleService.java:127`).
- **Шаг 2, неиндексированные FK `strategy_step_id`/`target_action_id`** — FK объявлены в `V1__strategies_baseline.sql:208-212`, индексов на них нет → перф-форвард (fixes).
- **Шаг 3, `lookbackBars` без нижней границы** — воспроизведено (`libs/domain-model/.../MarketStructureParams.java:22`, голое `Integer`) → §«Дефекты донора, воспроизведённые портом».
- **Шаг 3, провизорные пороги молча, двойная owner-простановка, N+1 по таймфреймам** — донорские детали `MarketStructureJob`; в порте не сверялись — предмет фокусов `CODE` `market-data`, отдельной задачи не заводится.
- **Сквозное (коды ошибок по error-политике)** — дом `docs/rules/error-handling-policy.md`, пересказ снят.

### Методологические задачи — обёртка снята, M1-M3 промоутированы в `##`

- **M1** — сжата: перечень моделей пересобран (разделы-заголовки остались у `Order.md`, `AlgoOrder.md`, `AuditRecord.md`; у `Position.md`, `Deal.md`, `Strategy.md`, `AnomalyReport.md` — строки, не разделы).
- **M2** — сжата: носители пересобраны по порту (`libs/domain-model`, `trading-core`, `connector-okx`); пересказ конвенции `Auditable` снят.
- **M3** — сжата до указателя: перечень кластеров держит `policy-home.md` §«Следствия для существующей базы».

### Инфра-долг — I1 снят

- **I1. Boot 3→4 split-autoconfig** — три пробела закрыты (все `services/*/pom.xml` несут `spring-boot-jackson2`; flyway/restclient стартеры — по факту сборки). «Durable-проверка на будущее» — правило, не задача; переезжает в `codestyle.md` §«Новый модуль монорепозитория» (fixes).
- **I4** — хроника F3a/F4 (`history/2026-06-20-source-api-contour/source-api-pilot-run-log.md`, `history/2026-07-14-claude-docs-curation.md`) снята; развилка защиты от рецидива осталась; round-trip тесты выделены в I5 (в `services/connector-okx/src/test` — 0 файлов с `sCode`/`cTime`).

### Средовой дефицит — снятые абзацы

- **Проброс Vault-токена** — снят по факту (профиль `test` поднимается, кейсы исполняются; токен перевыпущен держателем).
- **Переносимость инструментов** (два захода: Linux — `tools/spec-runner-env.sh`; Windows — mktemp/`cygpath`, `LC_ALL`, SKIP-фильтры, cp1251) — снята; факты — `code-preconditions.md` §«Среда контура — проверено прогоном, не выведено», решение — `.claude/decisions/measurement-repair-not-extension.md`.
- **Правило безопасности `tester` («автономно бутается только `test`-профиль», демо-контур доказывается отрицанием `50101`)** — политика, не задача; факт доказательства отрицанием живёт в `code-preconditions.md:475-479`; сама норма роли — см. fixes (дом уточнить).
- **Хвост «Снимает зависимость demo-прогонов от ручного бута» и примечание о ре-базе контура** — хроника; решение — `.claude/decisions/source-api-target-rebase.md`.
- **Умолчание surefire** — переформулировано: живые кейсы `@Tag("source-api-live")` теперь только в `donor/src/test` (2 файла), `services/` — 0; правка — `donor/pom.xml` либо корень.

### Хвосты узла полноты контекста — секция переразложена

- П.1 → §«Пересказ конъюнктов `flowsComplete` в правиле сверки» (жив: `docs/rules/pnl-reconciliation.md:166-176`).
- П.2 → §«Спек-раннер в доноре: границы, невыразимые на нём» (жив: `Spec.java` только в `donor/src/test`, `tools/spec-runner-env.sh:57-61` компилирует донорские файлы).
- П.3 → §«Примеры на пустой коллекции у агрегатов шести спек» (жив: `"legs": []` в `deal-risk-numbers.json` — 0).
- Пункт о реакции на отказ по неполноте графа — закрыт ранее решением в `docs/processes/risk-evaluation.md`.

### Проверка существования файловых указателей — сжата

- **«Что было закрыто» (развилка Д432)** — живёт в `.claude/rules/closed-work-transfer.md` §«Ссылочная целостность»; пересказ снят.
- **«Смежное» (61 указатель javadoc `donor/src/**`)** — закрыто иначе: `tools/doc-pointer-check.py` заведён, область — `.java` в `services/**`, `libs/**`, `web/**`; донор намеренно вне области.

### Припаркованные прочие находки трёх прогонов доковой петли шага 7 — вводки и дубли

- **Вводки трёх секций** (ссылки на диспозиции, пересказ критерия классов) — сняты; диспозиции — `history/2026-09-03-phase-1-step-7-deals-and-pnl/phase-1-step-7-gaps-close-3{1,2,3}.md`.
- **«Форма 3 реестра снятых редакций не доказана осью»** — слита в §«Позиции класса `ИЗМЕРЕНИЕ` под заморозкой усиления» (буллет `retired-check.py`).
- **«Ось `RiskCheckCode × реакция`»** — перенесена в §«Оси спек, выразимые популяцией и не выраженные ею» вместе с A-P7 (`deal-tranche-lifecycle.json`, `populations` — 0 в обеих спеках).
- **Контур источника (`tester`), три первых буллета** — закрыто: `coverage-manifest.md:100-112` пересобран (60/53/5/2), там же — исправление команды (носитель только исполнимая строка) и примечаний 🟡-строк.
- **Батарея осей `preconditions-check.sh` вне команды** — дубль §«Батарея осей проверки предусловий — внутрь команды» (backlog:1103), которую называет сама команда (`tools/preconditions-check.sh`).
- **Слой опровержения в продуктовом корпусе** (четыре копии: _31 курация, G-11, G4 реестра, P1 линзы B) — дом §«Неразрешимые адреса пассажей в `.claude/**`» (backlog:970, пассаж о сведении); в бэклоге оставлена одна строка-указатель у адреса реестра G4.
- **§-адреса в `docs/**` вне детектора** (два копии: _31, G12) и **три класса носителей + храповик** — слиты в буллет `anchor-check.py` §«Позиции класса `ИЗМЕРЕНИЕ`…».
- **Седьмая форма указателя** (две копии) — одна, в том же буллете `spec-pointer-check.py`.
- **README без раздела вопроса** (G-4, G7) — одна подсекция с машинным условием.
- **Два артефакта вне типов** (_31) и **G-3** — одна подсекция; второй артефакт _31 не назван нигде (в диспозиции — «два артефакта»), оставлен один известный.
- **«Три поля обязательны» при четырёх строках (`DealCashFlow.md`)** — закрыто: фразы в носителе больше нет (`grep "три поля" docs/models/domain/other/DealCashFlow.md` → 0).
- **C6 `failReason`** — закрыто: `docs/models/integrations/okx/AlgoOrderOkxResponse.md:19` объявляет поле верхнего уровня операндом разбора `state=order_failed`.
- **B-P2 javadoc донора** — донорские файлы заморожены; тот же битый указатель воспроизведён портом (`services/strategies/.../StrategyDefinitionValidator.java:88`) → §«Дефекты донора, воспроизведённые портом»; слепота `doc-pointer-check.py` к указателю с точкой на конце — новая позиция `ИЗМЕРЕНИЕ`.
- **D-P1 (_33), D-P5 (_34), «числа эталона» (_31)** — три копии одного пункта; одна строка в §«Риск и сайзинг».
- **G-10, часть про `stagnation-detection.md:25`** — номер строки снят (текст сдвинулся).
- **П-7 линзы C (_34)** — «подтверждение припаркованного, не находка» — не позиция, снята.
- **П5 линзы F (_34)** — «констатация состояния `observations/`, не дефект» — снята.
- **P5 линзы B (_34), «битый указатель из кода»** — предмет мерит `tools/doc-pointer-check.py` (0 дефектов на `services/libs/web`); конкретный битый указатель с точкой — см. B-P2 выше.
- **П4 линзы F (_34), «указатель бэклога на способ закрытия слотов»** — относится к секции вне этого диапазона (backlog:634-637) — передано в fixes.
- **`REPLACED_BY_STRATEGY`** — слито в буллет REPLACE-ноги §«Названные ограничения…».
- **Механизм контрпримеров наполовину (E П1 _31), `SpecRunnerTest` vs `spec-run.sh` (E П8)** — слиты в §«Спек-раннер в доноре…».

### Гейты фазы кода шага 7

- **G1** — по факту закрыт: `tools/retired-check.py:146-148` свипает `tools/**/*.py|sh|txt` и `.claude/work/*.json` (реестр в области). В бэклоге оставлен по указанию до правки реестра (fixes).

## Сработавшие оживители

| Секция / пункт | Условие, которое сработало | Исход |
|---|---|---|
| Позиции класса `ИЗМЕРЕНИЕ` (четыре в «Средовом дефиците», ось `RiskCheckCode`, форма 3, E5, E9, G3) | «вход в `CODE` своего шага» — шаг 7 фазы 1 вошёл в код 2026-09-01 и закрыт; для позиций фазы 1 условие больше не наступит, заморозка вооружена заново | перевооружены: `шаг:2-11:CODE` (ближайший шаг, чей предмет — проверочная машинерия) |
| Н6 п.2, E5, контрпримеры (спек-раннер) | «`CODE` шага 7», «курационный заход после первого кодового захода» — прошли ≥ 3 шага порта | перевооружены: перенос раннера из донора (`греп:"class\s+Spec\b"@libs/**/*.java` либо исчезновение `donor/.../spec/Spec.java`) |
| Н6 п.3 (примеры на пустоте) | «ближайший `GAPS_CLOSE` до `CODE`» — прошло несколько закрытий | взята в работу: `сейчас` |
| Н6 п.1 | «ближайший свип курации» — прошёл | `сейчас` |
| A7-A9, B-P2 (_33) | «`SYNC_DOCS_FROM_CODE` шага 7» — шаг закрыт | доковые правки — `сейчас`; B-P2 — новая форма (порт) |
| `standingRungRaisedManually` | «`SYNC_DOCS_FROM_CODE` шага 7» — мёртвая ветка снята | `сейчас` + `закрыто-когда` по появлению операнда в `docs/components/**` |
| Негейтящие находки _34, _31 без полей | «курационный заход после первого кодового захода» — прошёл трижды | `сейчас`, сгруппированы по владельцам с домами |
| F-4, П2 линзы F (код-тесты контура) | «заход, трогающий код-тесты контура» — контур заморожен в доноре | перевооружены: `нет-файла:donor/.../OkxSourceApiLiveTestBase.java` (перенос контура) |
| Ретро-ревью, агентское ревью, хвост шага 4 | «при касании файлов в `CODE`» — донор не касается | закрыты; воспроизведённое портом — отдельной секцией |
| I1 | три пробела автоконфигов | закрыт; правило — в дом конвенций |
| C6 | «заход, трогающий контур» | закрыт по факту (поле объявлено) |
| «Три поля обязательны» (`DealCashFlow.md`) | курационный заход | закрыт по факту (фразы нет) |

---

# Выписка закрытого — часть 6 (строки 3935-4732 `backlog.md`)

## Закрытые пункты

- **Статус-набор шага не покрывает шаг реструктуризации** — закрыт:
  оживитель «второй шаг с непродуктовой дельтой» сработал (шаг 2 фазы 2,
  DONE); адверсариальные фокусы обоих шагов дали находки по всем осям,
  форма «фокус без находок» не понадобилась, штатный статус-набор оба шага
  провёл. Носители: `.claude/work/history/2026-09-05-phase-2-step-1-monorepo.md`
  §«Адверсариальные фокусы `CODE`»,
  `.claude/work/history/2026-09-05-phase-2-step-2-platform/phase-2-step-2-chronicle.md`
  §«`CODE` — адверсариальные фокусы».
- **Предикат свежести шага — к владельцу контекста оценки** — закрыт не
  портом, а отсутствием предмета: шаг 7 фазы 2 DONE, `stepDataFresh` в
  `services/**` и `libs/**` отсутствует (только `donor/`); у ядра своего
  предиката свежести нет — свежесть есть пустой ответ владельца
  (`docs/components/StrategyConditionEvaluator.md` §Границы,
  `docs/spec/market-data-freshness.json`). Хроника —
  `.claude/work/history/2026-09-06-phase-2-step-7-trading-core/phase-2-step-7-chronicle.md`.
- **Темы Kafka по производителям** — закрыто у двух из пяти производителей
  фазы 2: `trading-core.facts` (`deploy/base/services/trading-core.yaml`,
  `KafkaTopic`) и `strategies.facts` (`deploy/base/services/strategies.yaml`).
  Остаток (`auth`, `market-data`, коннектор; развилка конверта у
  нетенантного предмета) — в секции.
- **Единый error-DTO у поверхностей соседних сервисов** — закрыто у
  `strategies`, `bff`, `audit-statistics`: `GlobalExceptionHandler extends
  ResponseEntityExceptionHandler` плюс `AccessDenialHandler` в
  `services/{strategies,bff,audit-statistics}/src/main/java/**/api/`.
  Остаток — четыре сервиса, в секции.
- **Размеры томов данных** — первое наблюдение снято (2026-09-05, стенд,
  холостой ход): Postgres 207 МиБ, Elasticsearch 11 МиБ, Kafka и Vault —
  сотни КиБ; образы в хранилище узла — 16 ГиБ
  (`.claude/work/history/2026-09-05-local-stand.md`). Дефект тома
  Elasticsearch выделен подсекцией `сейчас`.
- **Политики сжатия и хранения гипертаблиц** — образ закреплён:
  `timescale/timescaledb-ha:pg17.10-ts2.29.2`
  (`deploy/base/data/postgres-cluster.yaml`, `ImageCatalog`); причина
  парковки «синтаксис сжатия не известен» снята. Сжатие — задача `сейчас`,
  хранение — подсекция до прод-рубежа.
- **Донорский числовой ключ инструмента** — класс «реактор не показал
  падения донора» закрыт измерением `bash tools/reactor-test.sh` (отказ на
  «Nothing to compile»); из секции снят как провенанс.

## Снятая хроника и пересказ (дома названы, содержание не потеряно)

- «События `auth`»: хроника переякоривания условия шагом 10 фазы 2
  (аудит потребляет только построенные классы, стойку у соседа не
  заводит) — дом признака: `.claude/work/roadmap/phase-2.md`, примечание
  к шагу 10; перечень непокрытых классов —
  `docs/models/domain/other/AuditRecord.md` §«Почему журнал сегодня несёт
  не все классы».
- «Проработка облака»: пассаж «Что изменилось против прежнего условия»
  (провайдер-нейтральность манифеста как оживитель не наступит до рубежа)
  — `docs/architecture/platform.md` §Развёртывание.
- «Размеры томов»: «Пере-диспозиция (шаг 6 фазы 2)» — условие «появится
  `market-data`» сработало и наблюдения не дало; переписано на событие
  развёрнутого окружения — `.claude/work/history/2026-09-05-local-stand.md`.
- «Контекстный тест сервиса»: «Цена отсутствия измерена» — четыре дефекта
  первого развёртывания (плагин исполняемого артефакта, стартер актуатора,
  `flyway-core` вместо стартера, поставщик момента аудита) —
  `.claude/work/history/2026-09-05-local-stand.md`.
- «WS-слушатель»: пассаж «Чем шаг 5 закрыт без этого» и оговорка о
  прежней редакции («опрос коннектору как донору») — дома:
  `.claude/work/roadmap/phase-2.md` (строка шага 5 и примечание о шаге 7),
  `docs/architecture/services.md` §«Что коннектор не знает»,
  `docs/architecture/data-ownership.md` §«Outbox и доставка». Фраза
  «опросом наблюдает **ядро**» в секции сохранена.
- «Клиенты провайдера идентичности», «Донорский числовой ключ», «Единый
  error-DTO»: блоки «Как найдено» (находка C8 `DOCS_CHECK_2` шага 9;
  аппрув `CODE` шага 8, фокусы `conventions`/`security`) — провенанс,
  живёт в хрониках шагов 8 и 9.
- «Тропа к брокеру»: «Почему не наблюдалось до сих пор» (полного
  контекста не поднимает ни один тест трёх сервисов) — сведено в причину
  парковки; клейм «Выдача — один носитель» заменён разбором выдачи по
  файлам.
- Донорские секции: перечни файлов донора (8 / 12 / 5 файлов) сняты —
  стареют с донором, условие возврата от них не зависит.
- «Мягкий ориентир»: «Класс выноса назван» сведён в одну строку.

## Сработавшие оживители

| Секция | Условие, которое сработало | Исход |
|---|---|---|
| Статус-набор шага не покрывает шаг реструктуризации | второй шаг с непродуктовой дельтой (шаг 2 фазы 2 DONE) | закрыта |
| Предикат свежести шага — к владельцу контекста оценки | открытие шага 7 фазы 2 | закрыта (предмет снят конструкцией) |
| Энфорсер конвенции путей внешней поверхности | ближайший заход, добавляющий контроллер (шаг 10 завёл `/api/v1/audit-statistics/…`) | перевооружена на `шаг:2-10:DONE`: новый энфорсер — усиление измерения под заморозкой доковой петли; шаг 10 уже прошёл `CODE` и стоит в пост-хок `GAPS_CLOSE_6`, поэтому `шаг:2-10:CODE` прогону виден не был бы; окно — между закрытием шага 10 и доковой петлёй шага 11 |
| Энфорсер оси владельца-сервиса | первый шаг порта с картой как входом `DOCS_CHECK` (шаги 3-9 DONE) | перевооружена на `шаг:2-10:DONE` по той же причине |
| Размеры томов данных | первое наблюдение в развёрнутом окружении (стенд 2026-09-05) | перевооружена на `наблюдение:` под торговой нагрузкой; том Elasticsearch — подсекция `сейчас` |
| Политики сжатия и хранения гипертаблиц | пин образа TimescaleDB | взята в работу (`сейчас`, миграция сжатия); хранение — подсекция `рубеж:prod` |
| Темы Kafka по производителям | «шаги 4-8 фазы 2» (все DONE) | частично закрыта; перевооружена на появление `KafkaTemplate` у `auth`, `market-data`, коннектора |

---

# Выписка закрытого — часть 7 (`.claude/work/backlog.md`, строки 4733-5806)

Диапазон — парковки шага 10 фазы 2. Полностью закрытых секций нет: все 28
несут живой форвард. Ниже — закрытые **части** секций и провенанс, снятые
из рабочего файла; содержание каждого пункта живёт у названного носителя.

## Закрытое (по секциям)

- **Экспорт метрик из сервисов.** Доля `audit-statistics` исполнена (кодовый
  заход K9 шага 10 фазы 2): реестр `micrometer-registry-prometheus` в
  `services/audit-statistics/pom.xml`, `ServiceMonitor` и `PrometheusRule` в
  `deploy/base/services/audit-statistics.yaml`; второе исключение контура
  доступа объявлено правилом — `docs/rules/api-access-policy.md` §«Съём
  метрик — второе исключение». Снята редакция задачи «подключить actuator +
  micrometer»: `spring-boot-starter-actuator` уже в `pom.xml` всех
  сервисов. Счёт сервисов («шести / из восьми / семи» — три числа при семи
  каталогах) заменён командой `ls services/`.
- **Таблица отказов доступа у сервисов со своей базой.** Таблица у
  `audit-statistics` — `services/audit-statistics/src/main/resources/db/migration/audit/V1__audit_baseline.sql`
  (заход K2); писатель строки `AccessDenialService` построен заходом K10
  (2026-09-10, отчёт `.claude/work/progress/phase-2-step-10-code-pass-k10.md`);
  промежуточная строка реестра компонентов (Д1238) снята тем же ходом.
  Уточнение оживителя «заход, предметом которого является сервис» —
  вызвано заходом K11 (правил `strategies` ради актора содержимого).
- **Класс события `HoldReleased`.** Провенанс перемещения: подпункт (3)
  «аудит ручного un-hold (кем/когда)» переехал из бывшей секции §«Шаг 8
  (safety / AnomalyJob)» (оживитель «шаг 9 / п.9» устарел — шаг закрыт);
  переадресация «на журнал как первого потребителя» отвергнута первым
  раундом критики (`.claude/rules/parking-address.md` §«Признак верного
  дома»).
- **Состав экранных операций чтения `audit-statistics`.** Клейм «пары
  сетевой политики нет» опровергнут: `deploy/base/services/bff.yaml` несёт
  egress `bff → audit-statistics` и `audit-statistics-ingress-from-bff`
  (заход K1, реестр `code-gate-ledger.json` — компонент «контур доступа,
  манифест и сетевые политики» закодирован). Абзац «Смежное» сведён к одной
  строке-указателю.
- **Api-формы периметра без дока.** Хроника состава (форма
  `DealShutdownInitiatedStreamApiModel` заведена шагом 10, `HoldRaisedStreamApiModel`
  правлена) и довод о доме признака (`.claude/rules/carrier-levels.md`:
  док формы — сторона стыка) — в Д1378 и `docs/architecture/contracts.md`
  §«Состав своей формы периметр объявляет сам» (узел 5 `GAPS_CLOSE_6`,
  закрытие B2).
- **Корневые build-файлы вне области энфорсера.** Два предъявленных случая:
  комментарий корневого `pom.xml` с сильнейшей формой снятой редакции
  (узел 5 `GAPS_CLOSE_3`) и `tools/session-prompt.md` (2026-09-11, правка
  остановки цикла на границе шага); оба закрыты строкой популяции записи
  `tools/retired-check.py`.
- **Энфорсер окрестности точки вставки.** Довод «не тем же ходом» — Д1176,
  Д1181 (`.claude/work/decision-digest.md`); половина предмета стала нормой
  проходом 2026-09-11 (`.claude/rules/edit-kind-obligations.md` §«Предмет
  обязанностей — дельта захода, а не находки, на которые он отвечает»).
  Причина парковки «запрещён заморозкой» переписана — заморозка шага снята.
- **Снятый термин через javadoc-перенос.** Замер: три носителя, невидимых
  команде (javadoc `ManualHaltService` — Ф13, javadoc `BalanceRefreshTest`,
  allow-лист донорского `StrategyEntity`) —
  `.claude/work/progress/phase-2-step-10-code-focuses-8.md` §Ф13.
- **Слова провода / Вторая ось.** Счёт «15 слов / 2 словом / 13 символом»
  и «четырёх / шести» заменён командой; таблица оставлена как снимок.
  Мутация `exchangeAccountInternalId` у `HoldRaisedContent` — отчёт
  `.claude/work/progress/phase-2-step-10-code-focuses-9.md` §Ф16. Абзац
  «Чем это платит» (пересказ `codestyle.md` §«Пин значения…») снят как
  копия дома. Снятая редакция «у прочих классов имя не связывает…»
  (реестр `retired-check`, запись «связку имён компонентов не мерит ни
  одна проба…») в переписанной подсекции не воспроизведена.
- **Конверт события.** Опровержение оживителя «потребители шага 10» —
  транспорт заголовками (`docs/architecture/contracts.md` §«Как конверт
  лежит на проводе — поимённо»); кейс `ExchangeAck` живёт в
  `.claude/rules/codestyle.md` §«Неизменяемое значение, пересекающее
  сериализацию» и из бэклога снят как пересказ.
- **Цена сессий.** Аудит контекстной стоимости отменён держателем
  2026-09-07 до замера (`.claude/rules/session-work-unit.md` §«Цена
  принята и названа»).
- **Долг адресных перекрёстий.** Разбор (проходы гигиены мерили себя от
  `HEAD`; норма базы замера — `roadmap-step-execution.md` §«КЛЕЙМ о шаге
  меряется от границы шага…»; состав прироста пофайлово) снят; секция
  сжата до указателя на §«Свод адресных перекрёстий…» (слить при сборке —
  см. fixes).
- **Провенанс, снятый из тел секций** (дом — отчёты прогонов шага 10 и
  `history/`): Справочник площадок (верификация узла 5 `GAPS_CLOSE_3`);
  Строка раскладки (узел 5 `GAPS_CLOSE_3`, развилка Р5); Образ сервиса
  (узел 4 `GAPS_CLOSE_4`, V3); Дедуп (`DOCS_CHECK_4` B9, узел 5
  `GAPS_CLOSE_4`, ось популяции); Отчёт ручной операции (`DOCS_CHECK_4`
  B11, узел 5 `GAPS_CLOSE_4`, ось D); Долг адресных перекрёстий
  (`DOCS_CHECK_3` C3, узел 6); Слова провода и Указатель на дом перечня
  (дома заведены узлом 4 `GAPS_CLOSE_6`); Сбор ликвидаций — абзац «Что от
  этого не страдает» (стакан и тикер — `market-data-collection.md`
  §«Невосполнимые срезы»); Класс события на переход строки — абзац «Что от
  этого зависит» сведён к указателю.

## Сработавшие оживители

| Секция | Что сработало | Исход |
|---|---|---|
| Исполнимость формы ценового результата | «ближайший ход — `CODE` шага 10»: шаг вошёл в `CODE` 2026-09-10, пересчёт построен (`AggregateRecomputeTest`) — но операнды дома (`resultProfit`, `fundingCost`, `graphComplete`) в спеку агрегатов не приведены, `includes` отвергнут ценой (Д1092, текст операнда `priceResult` спеки) | **перевооружена**: `греп:"graphComplete"@docs/spec/statistics-aggregates.json` — появление операнда дома в спеке агрегатов |
| Строка раскладки у́же корпусной фактики | «построение `audit-statistics`»: строки `audit` и `statistics` в `docs/architecture/data-ownership.md` §Раскладка заведены | **взята в работу** (`сейчас`): условие состоялось, сведение не сделано, внешнего события больше нет |
| Цена сессий под правилом единицы | «первый прогон цепочки под правилом единицы»: цепочка прошла 2026-09-08..11 (коммиты `DOCS_CHECK_4` → `GAPS_CLOSE_6`), журнал `%LOCALAPPDATA%\vibetrading-stand\sessions\journal.md` — 1092 строки | **взята в работу** (`сейчас`): снять числа, вынести держателю |
| Энфорсер окрестности точки вставки | «вход шага в `CODE`» — 2026-09-10 (`phase-2-step-10-code-transition.md`) | **перевооружена** по указанию сборщика: `шаг:2-10:CODE` (по роадмапу статус шага сейчас `GAPS_CLOSE_6`); причина парковки переписана на «своя единица с падающей пробой» |
| Снятый термин через javadoc-перенос | то же | то же: `шаг:2-10:CODE` |
| Энфорсера указателя на дом перечня | «ближайший ход, снимающий заморозку на своём шаге» — то же | то же: `шаг:2-10:CODE`; дописана причина парковки (её не было) |
| Корневые build-файлы вне области энфорсера | «второй случай класса» — предъявлен 2026-09-11 (`tools/session-prompt.md`) | **перевооружена**: `шаг:2-10:CODE` (позиция держится причиной — своя падающая проба, а не недобором случаев) |
| Экспорт метрик из сервисов | доля `audit-statistics` — предъявленный читатель (алерт на лаг группы журнала) | доля закрыта (K9); остаток перевооружён `наблюдение:` объявленного читателя у следующего сервиса |
| Api-формы периметра без дока | прежний оживитель «любой заход по `bff`» сработал вхолостую на шаге 10 | сужен до захода по собственной поверхности периметра (Д1378) — `шаг:2-12:открыт` |
| Класс события `HoldReleased` | оживитель подпункта «шаг 9 / п.9» устарел (шаг закрыт) | переадресован ходу, строящему ручную тропу снятия |
