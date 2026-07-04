# Фаза 1. Полноценная торговля одной стратегией

## На какой вопрос отвечает этот файл

Из каких шагов состоит Фаза 1 продуктового роадмапа и в каком
статусе каждый шаг.

## Назначение

Детальный роадмап Фазы 1. Главный роадмап —
`.claude/work/roadmap/roadmap.md`. Процесс исполнения шага —
`.claude/processes/roadmap-step-execution.md` (там же канонический
набор статусов шага). Скилл ведения прогресса —
`.claude/skills/update-roadmap-progress.md`.

## Цель фазы

Бот торгует одной стратегией end-to-end: получает рыночные
данные, рассчитывает индикаторы, генерирует сигналы, отправляет
команды на биржу, ведёт позиции, фиксирует P&L. Полный
production-flow одной стратегии.

## Шаги

| # | Шаг | Статус |
|---|---|---|
| 1 | Поток рыночных данных (коннект к OKX, инструменты, цены/свечи, свежесть) | DONE |
| 2 | Стратегия (абстракция: объявляет нужные индикаторы и условие сигнала; одна реализация) | DONE |
| 3 | Производные рыночные данные: индикаторы + структура рынка (`MarketStructure`) + фаза рынка (`MarketPhase`) — jobs, модели, сервисы (расчёт/чтение/сохранение значений, запрошенных стратегией) | DONE |
| 4 | Команды и их жизненный цикл (ServiceCommand: submit/replace/cancel/close/REFRESH; исполнители; lifecycle; факт и реконсиляция через REFRESH, не ACK; ведение Position/Order) | DONE |
| 5 | Риск-преконтроль (валидация перед отправкой: размер, ограничения инструмента, reduce-only, лимиты) | DONE |
| 6 | FSM + живая оркестрация (состояния и переходы сущностей + handler'ы; живая оркестрационная петля `DealOrchestratorJob` (driving), REPLACE-оркестрация, per-deal concurrency-guard, механика финализации — финализационные executor'ы / терминальные рёбра / retry-state финализации) | DONE |
| 7 | Сделки и P&L (`DealOrchestratorJob` — агрегирование в `Deal`, расчёт `resultProfit` / P&L) | DOCS_CHECK_3 |
| 8 | AnomalyJob (полноценный, операционная детекция аномалий состояния/исполнения) | HOLD |
| 9 | Безопасность (auth-инфраструктура: Spring Security, `@PreAuthorize`, `SecurityFilterChain`; остаточный хардненинг секретов Vault — политики/approle/ротация/unseal, сама привязка уже введена на инфра-шаге; реактивирует фокус `security-review`) | HOLD |
| 10 | Тесты | HOLD |
| 11 | Фронт | HOLD |

## Примечания

- **Фронт (шаг 11)** — простой, для прогонов. Полноценный фронт
  появится после архитектурного рубежа.
- **Безопасность (шаг 9)** — строит auth-инфраструктуру (Spring
  Security, `@PreAuthorize`, `SecurityFilterChain`). **Vault-привязка
  секретов введена раньше — на инфра-шаге (2026-06-12, снапшот v47):
  datasource и OKX-креды читаются из Vault per-profile.** Шаг 9
  рескоупится на остаточный хардненинг секретов (политики/approle,
  ротация, unseal) поверх уже подключённого Vault — не на его введение.
  Содержание прорабатывается docs-first на самом шаге; на нём
  реактивируется фокус `security-review`. Форвард-материал —
  `.claude/work/backlog.md` §S1 (рескоуплен) / §S2.
- **Тесты (шаг 10)** и **Фронт (шаг 11)** — отдельные шаги
  фазы, исполняются по тому же процессу docs-first.
- Под-шаги внутри каждого шага заранее не дробятся; они
  появляются в процессе исполнения (см. процесс).
- **Граница шага 6 ↔ 7 уточнена (2026-06-21).** Живая оркестрация
  отнесена к **шагу 6**, не 7. Шаг 6 — «FSM + живая оркестрация»: помимо
  статусной механики (состояния и переходы сущностей) и конструкции
  handler'ов в него входят живая оркестрационная петля
  (`DealOrchestratorJob` driving), REPLACE-оркестрация, per-deal
  concurrency-guard (D-M1) и механика финализации (финализационные
  executor'ы, терминальные рёбра, retry-state финализации). Шаг 7 —
  «Сделки и P&L» — сужен до расчёта `resultProfit` и агрегации `Deal`
  (DEAL-Q2 закрыт как терминальный контракт на шаге 6; остаётся лишь
  *число* прибыли на ошибочном терминале — деталь шага 7); формулировка «он
  же оркестрирует торговый цикл сигнал→команда→позиция» из строки шага 7
  снята (петля уехала в шаг 6). Жёсткие гейты D-B3/D-M1 привязаны к шагу 6
  (петля включается там; см. примечание ниже).
- **Жёсткие гейты `DONE` шага 6 (2026-06-12, из разбора ревью шага
  4; привязка к шагу 6 уточнена 2026-06-21 вместе с границей 6 ↔ 7 —
  петля включается на шаге 6).** **D-B3** (SUBMIT recovery-by-clientId —
  дубль ордера при ресабмите после краша между place и сохранением
  `externalId`) и **D-M1** (concurrency-guard вокруг исполнения команды —
  двойной SUBMIT при перекрытии триггеров) — деньги-дубли, латентны до
  включения оркестрационной петли. **Блокирующее условие:**
  оркестрационную петлю **нельзя включать** и шаг 6 **не уходит в `DONE`**,
  пока оба не закрыты. Это жёсткий гейт шага (проверяется при переходе
  шага 6 в `DONE`), не просто форвард-долг. Детали —
  `.claude/work/backlog.md` §Хвост шага 4.
- **Error-политика — ✅ зафиксирована на `GAPS_CLOSE_1` шага 6
  (2026-06-22).** Единая политика исключений спроектирована docs-first:
  `docs/rules/error-handling-policy.md` (внешняя поверхность — единый
  `@ControllerAdvice` + error-DTO; async-фасад 202/409; внутренняя градация
  4 уровней — лог / ретрай / холд инструмента / холд биржи) + новое правило
  `docs/rules/instrument-hold.md` (уровень 3). **TBD error-конвенции в
  `codestyle.md` §«Обработка ошибок» снят.** Неблокирующие майоры шагов 2 и
  4 (500 вместо 422/409, невыровненные коды реджектов) ретро-закрываются по
  этой политике; конкретный набор HTTP-кодов и 409-vs-идемпотентность —
  провизорный хвост пользователя.
- **Шаг 3 → `CODE` (2026-06-09):** `GAPS_CLOSE_4` закрыт — концепт
  производных рыночных данных доспецифицирован, **гейт `CODE` расчищен**.
  Закрыты: условная фаза + fork A (ER в каталоге), Н6/Н8/Н10 (свежесть/
  retention/ключевание), ТР2 + ТР1 ч.1 (семантика объёмных условий), Н3
  (семантика структуры + контракт `MarketStructureResolver`). Открытый
  хвост, шаг **не** блокирующий: крипто-часть IND-Q1 (якорь — фаза 4).
  Per-находочного трекинга в роадмапе нет — статус ведётся по шагу.
- **Шаг 3 → `DOCS_CHECK_6` (2026-06-09):** перед `CODE` прогнан
  содержательный концепт-ревью сильно переписанных доков (после
  `GAPS_CLOSE_4`). Прошлый активный набор (Н3/Н6/Н8/Н10/ТР1-книжная/ТР2/
  fork A) подтверждён закрытым; редизайн условной фазы обнажил **два новых
  узких пробела** — Н11 (`TREND_CHANGED` в whitelist правил фазы vs
  stateless-классификатор) и Н12 (деривация `confirmedAt`
  условно-классифицированной фазы). Не чисто → нужен узкий `GAPS_CLOSE_5`;
  гейт `CODE` снова закрыт до их закрытия. Отчёт —
  `.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-step-3-docs-check-6.md`.
- **Шаг 3 → `GAPS_CLOSE_5` (2026-06-09):** Н11 и Н12 закрыты в доках.
  **Н11** — `TREND_CHANGED` исключён из whitelist правил фазы
  (`StrategyMarketPhaseRule`): темпоральное правило несовместимо со
  stateless-классификатором; структурные переходы выражаются
  `RANGE_BREAKOUT_CONFIRMED`/`MARKET_STRUCTURE_IS`; entry-контекст не
  затронут; дверь на будущее — чтение готовой истории структуры. **Н12** —
  `confirmedAt` фазы выводится как консервативный `max` по гейт-операндам
  сработавшей клаузы (роль распущенного `confirmationBars`). Далее — чистый
  `DOCS_CHECK_7` и переход к `CODE`.
- **Шаг 3 → `DOCS_CHECK_7` (2026-06-09):** подтверждающий прогон после
  `GAPS_CLOSE_5` — **чисто**. Н11/Н12 закрыты по всем затронутым докам,
  новых несогласованностей правки не внесли; единственный остаток — ТР1
  крипто (IND-Q1, запаркован на фазу 4, не гейтит). Концепт-гейт `CODE`
  пройден (правило `roadmap-step-execution.md` §«Гейт `CODE` — чистый
  `DOCS_CHECK`»). Отчёт —
  `.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-step-3-docs-check-7.md`. Шаг готов к
  `CODE` (вкл. forward-debt Java step-2) — перевод за пользователем.
- **Шаг 3 → `SYNC_DOCS_FROM_CODE` (2026-06-09):** доки приведены к
  утверждённому коду (docs←code). Синканы инкременты D1-D3 + fork-A;
  заведён рациональ-батч `docs/decisions/derived-market-data-code-increments.md`.
  Расхождения (add: адресный компонент операнда, пороги структуры,
  ER/ATR-soft-ключи, `breakoutEvent`/`MarketBreakoutEvent`; change:
  скалярный контракт резолвера, корректный прокси, ER-порог вместо
  EMA-наклона) реконсилированы; removal нет. Численные пороги помечены
  провизорными (STRUCT-Q1). Краевой случай идентичности `config_id` vs
  ER/ATR-ключей **не закрыт** — вынесен STRUCT-Q2 (не блокирует sync).
  Отчёт — `.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-step-3-sync-docs-from-code.md`.
  **Перед `DONE` — пост-хок концепт-гейт (§6a) для D1** (концепт-инкремент
  на CODE): `concept-review` по приведённым докам, в этом прогоне не
  запускался.
- **Шаг 3 → `DONE` (2026-06-10, после ревизии D):** поверх закрытого CODE
  прошёл отдельный трек — **ревизия D** (owner-ключевание результатов вместо
  реестров/шаринга; настройки рыночных данных → собственные strategy-scope-
  строки; фаза `MarketPhase` stateless — не персистится, вычисляется на лету;
  `MarketPhaseClassifier` → `MarketPhaseResolver`). Доведена до целевого
  состояния в доках (docs-first) и реализована в коде (миграции `V4`/`V5`,
  компилируется). Пост-хок концепт-гейт (§6a, для D1) и торговый гейт
  пройдены **чисто** на пост-ревизионных доках: `DOCS_CHECK` обоих фокусов
  (`concept-review` + `trading-review`) по шагам 1-3 — без блокеров (отчёт
  `.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/phase-1-docs-check-post-revision-d.md`). Открытый
  хвост — **non-gating**: PHASE-Q1 (липкость/гистерезис фазы, `trading-review`),
  PHASE-Q2 (размещение `MarketPhase` как computed value), STRUCT-Q1
  (калибровка порогов, фаза 2), IND-Q1 (крипто-объём, фаза 4). Ролляп фазы —
  без изменений (`IN_PROGRESS`: шаги 1-3 `DONE`, 4-11 `HOLD`).
- **Шаг 4 → `DOCS_CHECK_1` (2026-06-10):** стартован шаг команд (`TOOLING`
  пройден без новых артефактов — concept/trading-фокусы уже есть). Первый
  прогон сквозной проверки: command-layer **в основном уже материализован**
  миграцией из архива; обход дошёл до стадии 2 (компоненты+модели). **Не
  чисто** — гейт `CODE` закрыт. Блокер `CODE` — **N1/DEAL-Q3**
  (`DealActionState` не материализован: центральная command-модель без
  структуры/размещения). Прочее: CMD-Q2 (базовый тип payload'ов, гейтит
  чистоту), N2 (`AttachedAlgoOrderStateResolver` без компонент-дока),
  N3/N5 (стале-ссылки на несуществующие `tasks/{order,algo-order,position}.md`;
  исполнитель recovery-refresh команд). Торговый фокус — блокеров нет (модель
  реконсиляции корпусно-состоятельна). Нужен `GAPS_CLOSE_1`. Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-docs-check-1.md`.
- **Шаг 4 → `GAPS_CLOSE_1` (2026-06-10):** пробелы `DOCS_CHECK_1` закрыты.
  **N1/DEAL-Q3** — `DealActionState` материализован
  (`docs/models/domain/other/DealActionState.md` +
  `docs/lifecycles/DealActionState.md`; `RuntimeTarget` объектом, retry через
  `Retryable`, вложенное — jsonb; решение
  `docs/decisions/deal-action-state-materialization.md`) — **блокер `CODE`
  снят**. **N2** — заведён
  `docs/components/AttachedAlgoOrderStateResolver.md`. **N3/N5** — сняты
  стале-ссылки на несуществующие `tasks/{order,algo-order,position}.md`;
  закреплён исполнитель recovery-refresh команд (entity-refresh-executor, без
  отдельных файлов). **Э3/CMD-Q2** — payload-разделы перенесены к
  executor'ам; базовый тип payload'ов **не финализирован** (крен разошёлся на
  валидации): концепт-проектирование дообучено (эвристика 5 — сигнатуры/
  расширяемость), переоценка базового типа выносится на валидацию, CMD-Q2
  остаётся открытым. Далее — `DOCS_CHECK_2`.
- **Шаг 4 → `DOCS_CHECK_2` (2026-06-10):** подтверждающий прогон после
  `GAPS_CLOSE_1` — **чисто**. Все находки `DOCS_CHECK_1` (N1-N5) и CMD-Q2
  подтверждены закрытыми; `DealActionState`/`RuntimeTarget`/статусы
  согласованы между моделью, lifecycle и всеми потребителями (вкл. step-6/7
  доки); новых doc↔doc несогласованностей и битых ссылок нет. Остаток — 2
  не-гейтящие CODE-level заметки (`SKIPPED`-рёбра lifecycle, `maxAttempts`
  на `Retryable` vs политике). Торговый гейт — без блокеров. **Концепт-гейт
  `CODE` пройден.** Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-docs-check-2.md`. Шаг готов к
  `CODE` — перевод за пользователем.
- **Шаг 4 → `GAPS_CLOSE_2` (2026-06-10):** закрыта находка **F1** (владение
  evidence-cycle refresh-команд), всплывшая при выкладке «команды → запросы
  к OKX» уже после чистого `DOCS_CHECK_2`. Решение — вариант (a)
  (`docs/decisions/refresh-evidence-cycle-ownership.md`): refresh-исполнители
  (`REFRESH_ORDER` / `REFRESH_ALGO_ORDER` / `REFRESH_FILLS`) обходят
  evidence-cycle **внутри одной команды** и сами выносят терминал
  `MISSING_AFTER_REFRESH`; атомарность — на уровне команды, не HTTP-запроса
  (переформулировано в `command-lifecycle`). **F2** — той же моделью
  (`REFRESH_FILLS` 3d→3m внутри команды; архив 3m+ — `OKX-Q2`). Поднят
  подвопрос **CMD-Q3** (судьба standalone pending/history refresh-команд) —
  не достраивается. Концепт изменён → перед `CODE` нужны закрытие CMD-Q3 и
  подтверждающий `DOCS_CHECK_3`.
- **Шаг 4 → CMD-Q3 закрыт (steer, 2026-06-10):** refresh-набор — ровно по
  одной команде на сущность (`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`,
  `REFRESH_POSITION`, `REFRESH_BALANCE`, `REFRESH_FILLS`); bulk-команды
  `REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS`
  / `REFRESH_ALGO_ORDER_HISTORY` сняты из enum'а, эндпоинты — звенья
  внутреннего цикла. Обновлены **все** ссылки: `ServiceCommand` (enum),
  `ServiceCommandFactory`/`Executor`, `risk-validator-scope`, контракты
  OKX, lifecycles `Order`/`AlgoOrder`/`Position`, mapping `Order`, 7 FSM
  handlers. Снятие bulk обнажило **CMD-Q4** (перечисление **неизвестных**
  live orders/algo по инструменту — Precheck-cleanliness / `AnomalyJob`);
  не достраивается. Перед `CODE` — закрытие CMD-Q4 + `DOCS_CHECK_3`.
- **Шаг 4 → `DOCS_CHECK_3` (2026-06-10):** подтверждающий прогон после
  F1/F2/CMD-Q3 — **чисто** (механика). F1/F2/CMD-Q3 закрыты и согласованы;
  grep-верификация: все REFRESH-токены — ровно 5 выживших команд, снятые 4
  только в нотах-о-снятии (dangling нет); закрытия `DOCS_CHECK_2` интактны.
  **CMD-Q4 переклассифицирован форвард/non-gating** (владелец step-6/8: Precheck
  / `AnomalyJob`; command-layer шага 4 полон без него; remedy — read вне
  command-layer, не команда) — пересматривает раннюю пометку «перед `CODE`
  закрыть CMD-Q4». Концепт-гейт `CODE` пройден при принятии форвард-статуса
  CMD-Q4; финальный «блокер vs форвард» и переход в `CODE` — за
  пользователем. Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-docs-check-3.md`.
- **Шаг 4 → тулинг `integrator` + прогон 1 (2026-06-11):** материализован
  владелец интеграционного знания (`integrator`, процесс
  `api-docs-completion`, скилл `integration-okx`, решение
  `.claude/decisions/integrator-agent.md`, ступень Предложение в леджере).
  Первый прогон — сверка command-relevant OKX-доков под скоуп шага 4:
  in-scope доки (order/algo-order/position/balance/fills) **подтверждены
  соответствующими спеке**, докачки не потребовалось. Surfaced **находка
  И-1** (Предложение, не финал): cancel-путь trailing `move_order_stop` —
  вероятно `cancel-advance-algos`, а не задокументированный `cancel-algos`;
  требует подтверждения официальным доком (OKX-док — SPA, WebFetch
  ненадёжен). Скан возможностей → форвард-кандидаты (cancel-all-after →
  шаг 8, order-precheck → шаг 5, positions-history → шаг 7, batch-write →
  portfolio). Гейт `CODE` этим прогоном **не меняется** — И-1 и кандидаты
  идут на разбор в чат. Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-1.md`.
- **Шаг 4 → правка процесса + прогон 2 интегратора (2026-06-11):** процесс
  `api-docs-completion` переведён на **полное покрытие периметра** (не
  скоуп шага) + **манифест покрытия** с провенансом per-row; скан — только
  после полного корпуса. Перепрогон: заведён манифест
  `docs/integrations/okx/coverage-manifest.md` (полная поверхность OKX,
  статус+провенанс). Командный костяк шага 4 — `есть-док`/офдок, полон
  против поверхности; новых внутри-скоупных возможностей нет. **И-1**
  зафиксирован фактом в `algo-order.md` (trailing → `cancel-advance-algos`),
  выбор (а)/(б) — на валидации (крен б). **Эскалация — источниковый
  дефицит:** офиц. док OKX (SPA) поле-уровнево нечитаем доступными
  инструментами → поле-доки `пробел`-строк не заведены; нужен канал чтения
  офдока (выкладка/OpenAPI/интерим с провенансом) — частично новая фактура
  к отвержению «библиотеки выгрузок». Скан с полного манифеста → форвард-
  кандидаты В-1…В-9 (шаги 5/7/8, portfolio). Гейт `CODE` **не меняется**.
  Отчёт — `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-2.md`.
- **Шаг 4 → прогон 3 интегратора: докачка офдок-grade (2026-06-11):**
  пакет прогона 2 закрыт в чате и исполнен. **Канал чтения офдока —
  самообслуживание** (страница — статический HTML; сырой fetch +
  локальный парсинг; хранение — только дистиллят, решение B);
  эскалация источникового дефицита снята. **Все `пробел`-строки
  периметра докачаны** до поле-уровневых доков: 18 новых контрактов +
  обновления (position/account-bills/algo-order), манифест без
  пробелов, провенанс `офдок`, sync-шапки по `external-source-sync`.
  **И-1 закрыт исходом (а)** — ветвление cancel по семье algo
  (основание — продуктовый факт trailing-защиты, `Strategy.md`).
  Сверка симметрии advance подняла **И-2** (`cancel-advance-algos`
  выведен из офдока 2025-04-24; конфликт внутри страницы —
  runtime-подтверждение в demo trading на `CODE`) и **И-3** (advance
  не амендится). Форварды В-1/В-2/В-3/В-6/В-7/В-8/В-9 переданы шагам
  5/7/8 (`backlog.md` §Форвард-материал); В-4/В-5 — «рассмотрено, не
  берём»; В-6 положен рядом с OKX-Q3. Гейт `CODE` **не меняется**.
  Отчёт — `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-integrator-run-3.md`.
- **Шаг 4 → валидация И-2 + `DOCS_CHECK_4` (2026-06-11):** И-2
  провалидирован пользователем (принят без правки): ветвление И-1(а)
  стоит, advance-ветка с пометкой; снятие — runtime-проверка в demo
  trading на `CODE`; **кредов demo пока нет** — проверка ждёт их
  появления (за пользователем), до неё документальная фактура
  прогона 3 принимается достоверной (зафиксировано в `algo-order.md`
  и отчёте прогона 3). Прогон `DOCS_CHECK_4` (рябь прогонов
  интегратора): механика чиста — ветвление разнесено согласованно,
  link-integrity/манифест↔контракты/форварды без находок, закрытия
  `DOCS_CHECK_1-3` интактны. **Не чисто** — одна содержательная
  находка **К-1/Т-1** (обнажена фактом И-3): концепция предлагает
  амендный путь ремодела trailing (`newTrailingPrice` в payload,
  «меняет» в `CalculatedPrice`, AMEND с `trailingSettings` в
  `Strategy.md`), биржа standalone trailing не амендит; торговая
  грань — окно без защиты при cancel+place [Vince, введ., с. 6].
  Варианты (а) запрет амендного пути / (б) трансляция в replace-flow
  / (в) как есть; крен — (а). **Гейт `CODE` перезакрыт** до закрытия
  К-1 → нужен узкий `GAPS_CLOSE_3` + подтверждающий `DOCS_CHECK_5`.
  Отчёт — `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-docs-check-4.md`.
- **Шаг 4 → проработка К-1, расширенная развилка (г) (2026-06-11):**
  финализации К-1 по (а)/(б)/(в) не было — пользователь расширил
  развилку вариантом **(г)**: запрет AMEND как доменной операции
  целиком (ордера и algo), единый replace-путь ремоделирования.
  `solution-designer` проработал (г) против baseline (а): scope
  подтверждён полным (вкл. amend-order); торговое правило порядка
  ног по риск-классу (protective — place-new → факт → cancel-old,
  двойная reduce-only защита безопасна, обобщение
  protection-switch; entry — cancel-old → факт → place-new);
  очередь/rate-limit — нематериальны для класса стратегии;
  семантика REPLACE: под-развилка (г-1 команда)/(г-2 оркестрация) —
  **крен (г-2)** (REPLACE — `StrategyActionType`, исполняется
  оркестрацией атомарных команд; `AMEND_*` уходят из enum;
  identity — новая сущность + `replacesInternalId` +
  `REPLACED_BY_STRATEGY` (у algo уже есть); резолюция цели по
  цепочке). **Крен — принять (г)/(г-2)**; на валидации
  пользователя. К-1 остаётся открытой до валидации; `GAPS_CLOSE_3`
  (дельта ~12 доков + decision `replace-not-amend`) и `DOCS_CHECK_5`
  — после. Статус шага не меняется (`DOCS_CHECK_4`). Проработка —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-k1-replace-design.md`.
- **Шаг 4 → `GAPS_CLOSE_3`: REPLACE-only принят и разнесён
  (2026-06-11):** валидация проработки К-1 — **чистая** (все три
  пункта крена приняты без правки). Дельта исполнена: `AMEND_ORDER` /
  `AMEND_ALGO_ORDER` сняты из `ServiceCommandType` (19 → 17), амендные
  executors/payload'ы удалены; `StrategyActionType` —
  `CREATE/REPLACE/CANCEL` (+палитра REPLACE = CREATE, правила 4/6/7,
  резолюция цели по цепочке); identity — `replacesInternalId` на
  `Order`/`AlgoOrder`, `Order.CloseReason += REPLACED_BY_STRATEGY`;
  `DealActionState` §REPLACE-действия (две ноги из фактов, без новых
  статусов); порядок ног по риск-классу в lifecycle/правилах
  (`risk-validator-scope` — риск-контроль на place-ноге;
  `exchange-hold`, `ack-not-runtime-truth`, `command-lifecycle`);
  mapping `Order`/`AlgoOrder` — амендный request-mapping снят;
  контракты OKX/манифест/скилл — пометки «доменом не используется»;
  `ORDER_AMEND_PRICE` → `ORDER_REPLACE_PRICE`. Рационал + отвергнутые
  (а)/(б)/(в)/(г-1) — `docs/decisions/replace-not-amend.md`. К-1/Т-1
  закрыты, следствие И-3 снято; заметка про окно двойной защиты — у
  CMD-Q4. Код-дельта (существующие amend-исполнители) — на `CODE`.
  Далее — подтверждающий `DOCS_CHECK_5`.
- **Шаг 4 → `DOCS_CHECK_5` (2026-06-11):** подтверждающий прогон после
  `GAPS_CLOSE_3` (REPLACE-only) — **чисто**. Все закрытия подтверждены
  (enum 17, executors/payload'ы сняты, `StrategyActionType` REPLACE +
  правила + резолюция цепочки, `replacesInternalId` +
  `REPLACED_BY_STRATEGY`, порядок ног, риск-скоуп на place-ноге,
  пометки поверхности OKX); grep-верификация: амендные токены — только
  ноты-о-снятии/decision/биржевые DTO-поля, dangling нет; link-integrity
  31 файла дельты — чисто; закрытия `DOCS_CHECK_1-4` интактны; торговый
  гейт без блокеров. 3 не-гейтящие CODE-заметки (главная — код-дельта
  REPLACE-only на `CODE`: Java-енумы, amend-исполнители, пример
  стратегии). **Концепт-гейт `CODE` вновь пройден** — перевод за
  пользователем. Вне гейта: И-2 (ждёт кредов demo), CMD-Q4, RISK-Q1/Q2,
  OKX-Q1/Q2/Q3. Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-docs-check-5.md`.
- **Шаг 4 → `CODE` (2026-06-11):** концепт-гейт пройден, шаг переведён в
  `CODE` (пользователь). CODE-фаза нарезается инкрементами по зависимостям
  (ревью + компиляция между крупными): **(1) REPLACE-only дельта к
  существующему коду — исполнена** (`StrategyActionType` AMEND→REPLACE +
  согласование javadoc/`@Schema`/json-примера; grep — без остаточных
  amend-токенов кроме note-of-removal в enum; `mvn compile` чисто на
  JDK 25); (2) командное ядро (`ServiceCommand` / `ServiceCommandType` 17 /
  `ServiceCommandPayload`-маркер; `DealActionState` + `RuntimeTarget` +
  `Retryable` / `RetryError` / `RuntimeErrorCode`); (3) дозревание
  `Order` / `AlgoOrder` / `Position` до полных моделей (сейчас —
  скелеты-енумы, `Position.java` отсутствует); (4) persistence + mapping +
  Flyway; (5) `IntegrationService`-команды OKX; (6) резолверы статусов;
  (7) исполнители + `ServiceCommandExecutor` + `RetryPolicyService`;
  (8) `ServiceCommandFactory`. FSM-handlers / `DealStateMachine` /
  оркестратор / risk-преконтроль / `AnomalyJob` — шаги 5-8, не в этом шаге.
- **Шаг 2, фиксация задним числом:** между `GAPS_CLOSE_7` и
  `DOCS_CHECK_8` пройден повторный под-шаг `TOOLING` (торговый
  совет: агент `trading-specialist`, дистиллят корпуса
  `.claude/library/trading/distilled/`, активация фокуса
  `trading-review`; 2026-06-04/05). В таблицу в моменте не
  проставлялся — рассинхрон закрыт на `GAPS_CLOSE_8` этой пометкой
  (таблица держит только текущий статус).
- **Шаг 4 → `SYNC_DOCS_FROM_CODE` + `DONE` (2026-06-11):** код шага 4
  (8 инкрементов: ядро `ServiceCommand`, дозревшие модели
  `Order`/`AlgoOrder`/`Position`+`Condition`, persistence + Flyway
  `V6`/`V7`, OKX-интеграция + `OkxProxyController`, 4 status-резолвера,
  **13 исполнителей** + диспетчер `ServiceCommandExecutor` + retry,
  `ServiceCommandFactory`) **аппрувнут пользователем**; `mvn clean
  compile` чисто на JDK 25. Ревью-итерации (пользователь): evidence-cycle
  REFRESH (single→pending→history, fills 3d→3m, терминал
  `MISSING_AFTER_REFRESH`), `updateFromSnapshot` маппером, порядок
  KillSwitch (close→cancel→cancel→безусловный close), фикс
  `StatusResolveResult` (IDEA-Lombok). **SYNC_DOCS (docs←code):** add
  (balance persistence; минимальные calculated-заглушки → шаг 5;
  evidence-cycle эндпоинты `IntegrationService`; прокси; retry-конфиг),
  change (порядок KillSwitch → `KillSwitchExecutor.md`;
  `StatusResolveResult` фабрика; mapper-конвенция → `codestyle`), defer
  (SUBMIT recovery, ClosePosition ccy, REPLACE-оркестрация/CANCEL-цепочка
  фабрики, refresh `condition` external-поля, `billId`-пагинация →
  `backlog.md` §Хвост шага 4); removal нет. **Пост-хок концепт-гейт §6a
  не триггерится** (концепт/контракт-инкремент на CODE не въезжал:
  утверждённая концепция + отложения + codestyle-конвенция + поведенческие
  refinements) → `DONE` напрямую. Рантайм (прокси, demo-креды, И-2) —
  отдельно, после шага. Ролляп фазы — **без изменений** (`IN_PROGRESS`:
  шаги 1-4 `DONE`, 5-11 `HOLD`). Отчёт —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-sync-docs-from-code.md`.
- **Шаг 4 → откат `DONE` → `CODE` (2026-06-11):** обнаружен дефект гейта —
  `DONE` проставлен **мимо предписанного агентского адверсариального
  ревью**. Фактически: на `CODE` фокусы `conventions`/`performance`/
  `disaster` **не прогонялись** (ревью было авторское + ручное
  пользователя), на `SYNC_DOCS_FROM_CODE` `divergence`-детект **не
  запускался** («доки ок» — на глаз), пост-хок концепт-гейт §6a не
  прогонялся. По ужесточённому правилу (`roadmap-step-execution.md` §7
  «Гейт `DONE`») статус **откатан в `CODE`** до фактического прохождения:
  независимые ревьюер-фокусы → `divergence` на синке → пост-хок
  концепт-гейт §6a (концепт-инкременты, въехавшие на CODE: REFRESH
  evidence-cycle order→pending→history, терминал `MISSING_AFTER_REFRESH`,
  ловля controlled-исключений на границе исполнителя) → закрытие находок.
  Ролляп фазы — без изменений (`IN_PROGRESS`). После прохождения гейтов —
  повторный `DONE` с зафиксированными исходами.
- **Шаг 4 → `DONE` (повторно, через гейт, 2026-06-11):** прогнаны
  **независимые** адверсариальные фокусы (general-purpose субагенты, не
  автор): `conventions` (0/3/2), `performance` (0/3/4), `disaster`
  (**4 blocker**/6/3), `divergence`; пост-хок концепт-гейт §6a
  (`concept-review`) — **чисто**. Зафиксированный исход —
  `.claude/work/history/2026-06-11-phase-1-step-4-concept-review/phase-1-step-4-adversarial-review.md`.
  Дефект гейта подтверждён эмпирически: disaster нашёл деньги-блокеры,
  пропущенные авторской+ручной проверкой. **Закрыто правкой** (код
  компилируется чисто): D-B1 `closePosition` фабриковал success → ack по
  `sCode`; D-B2 `verifyCode` доверял top-level code → write-пути по
  `sCode`; D-B4 kill-switch отмены → best-effort; D-M2 перевёрнутый
  `classify` (транспорт↔терминал); D-M3 проверка ACK до мутации; perf
  FK-индексы `V6`/`V7`; conventions `Objects.equals`/константы side.
  **Доки** C4 (`RefreshFillsExecutor.md` сужен до Order)/C5. **Форвард-
  долгом** (gating step-6/7, латентны без оркестрационной петли): D-B3
  SUBMIT recovery, D-M1 concurrency-guard; +D-M5/P-M3/D-M4 → `backlog.md`
  §Хвост шага 4. Гейт `DONE` по новому правилу пройден. Ролляп фазы —
  без изменений (`IN_PROGRESS`: шаги 1-4 `DONE`, 5-11 `HOLD`).
- **Шаги 1-3 → ретро-достройка ревью (2026-06-11):** по новому правилу
  гейта проверены записи шагов 1-3 — **независимый** адверсариальный
  code-review был пропущен (шаг 1 — self-review; шаг 3 — не зафиксирован;
  шаг 2 — фокусы с находками, независимость неявна; `divergence` на
  синках — везде прогонялся). Достроен независимыми субагентами
  (consolidated `conventions`+`performance`+`disaster`): **блокеров нет
  ни в одном шаге** — статусы `DONE` остаются валидными. Находки: шаг 1
  (0/1/4), шаг 2 (0/3/3), шаг 3 (0/0/3) — все неблокирующие, в
  `backlog.md` §Ретро-ревью шагов 1-3 (часть major'ов шага 2 гейтится
  TBD error-конвенцией). Зафиксированный исход —
  `.claude/work/history/2026-06-11-phase-1-steps-1-3-retro-adversarial-review.md`.
  Ролляп без изменений (`IN_PROGRESS`).
- **Шаг 5 → `DOCS_CHECK_1` (2026-06-20):** стартован шаг риск-преконтроля
  (`TOOLING` пройден без новых артефактов — фокусы `concept`/`trading` активны).
  Первый прогон сквозной проверки: risk-layer **в основном уже материализован**
  миграцией из архива (процессы `risk-evaluation`/`strategy-action-calculation`,
  `RiskValidator`/`RiskBlockResolver`/калькуляторы, RVO, правила). Механика чиста
  (стадии 0-1); пробелы сосредоточены на **входах** валидатора (стадия 2). **Не
  чисто** — гейт `CODE` закрыт. Гейтят `CODE`: **N1+N2/INSTR-Q1+Q2**
  (`InstrumentExternalRules` не материализована, а шаг 5 — её потребитель; +
  трёхсторонняя несогласованность по max-size/`externalMaxLeverage` полям —
  объявлены/использованы, но не маппятся), **N3/RISK-Q1** (структура `RiskSettings`
  только name-level), **N4/RISK-Q2/TR1** (нет `RiskCheckCode` и правила worst-case
  guard'а экспозиции/плеча — `position exposure` считается, блокировать нечем;
  единственный потолок — биржевой максимум, на крипто-перпах не guard rail).
  **Торговый фокус — одна блокирующая находка TR1** (жёсткий гейт «модель не
  выражает обязательный worst-case guard», корпус единодушен). N5 — паттерн
  потребления constraint-эндпоинтов (live vs persisted; `order-precheck` вне
  нашего режима маржи). N6/N7 — гигиена (атрибуция направления в `RiskBlockResolver`,
  битая кросс-ссылка). Все четыре центральные развилки имеют штатный
  горизонт-владельца шаг 5. Нужен `GAPS_CLOSE_1`. Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-1.md`.
- **Шаг 5 → `GAPS_CLOSE_1` (2026-06-20):** пробелы `DOCS_CHECK_1` закрыты.
  **Риск-политика на сделку** проработана с пользователем и зафиксирована
  решением `docs/decisions/per-trade-risk-policy.md` (трёхуровневая модель
  риска: сделка — фаза 1, биржа — фаза 3, межбиржевой портфель — мультибиржевой
  этап; риск на сделку = убыток на стопе как % от **свободного** депозита, входы
  цена входа/стопа/размер/плечо/комиссии; плечо связано лимитом риска — отдельного
  кэпа нет; без поправки на проскок в фазе 1; строгий блок при невмещении даже на
  `minSz`; числовой лимит провизорный). Этим закрыты **RISK-Q2/N4/TR1** (worst-case
  guard экспозиции — уровень риска на биржу/портфель, отложен; в фазе 1 контроль —
  лимит риска на сделку, код `RISK_PER_TRADE_EXCEEDED` уже есть; новый
  exposure-код не вводится), **RISK-Q1/N3** (нет RVO `RiskSettings` — поля
  `StrategyDetail`; поле `CalculationContext.riskSettings` упразднено) и **TR5**
  (база — `externalAvailableEquity`). **InstrumentExternalRules материализована**
  (N1/N2/INSTR-Q1) решением
  `docs/decisions/instrument-external-rules-materialization.md` (JSONB-навес на
  `Instrument`; домапплены per-order max sizes + `lever→externalMaxLeverage` +
  `state→externalState` — снята трёхсторонняя несогласованность; источник потолка
  плеча — инструмент-уровневый, per-tier `position-tiers` — форвард). **INSTR-Q2**
  закрыт в части роли плеча/HOLD; остаток — тайминг set-leverage (форвард к шагу 6).
  **N5** — собственный преконтроль основной, `order-precheck` вне режима маржи,
  live-эндпоинты (tiers/price-limit) вне валидатора фазы 1. **N6/N7** — гигиена
  снята. Распространено по доменным/процессным/контрактным докам + закрыты
  RISK-Q1/Q2/INSTR-Q1 в `open-questions.md`. Далее — подтверждающий `DOCS_CHECK_2`.
- **Шаг 5 → ратификация (а) + `DOCS_CHECK_2` (2026-06-20):** контроль риска
  ратифицирован пользователем (вариант (а): в фазе 1 отдельного потолка
  плеча/экспозиции нет). Заведена форвард-заметка: простой жёсткий предел плеча
  на сделку рассматривался против зазора «узкий стоп → высокое плечо» и
  сознательно отложен — revisit после бэктеста/живых прогонов
  (`backlog.md` §Шаг 5; рационал — `docs/decisions/per-trade-risk-policy.md`
  §Альтернативы). Подтверждающий `DOCS_CHECK_2` (независимые ревьюер-фокусы
  concept+trading) — **чисто**: все находки `DOCS_CHECK_1` (N1-N7) CLOSED-CLEAN,
  торговый гейт чист (TR1 разрешена корпусно-состоятельно — кэп экспозиции —
  уровень риска на биржу/портфель, фаза 3), новых блокеров правки `GAPS_CLOSE_1`
  не внесли. 3 микро-рассинхрона (`BalanceContainer` база, `Strategy`
  «% от капитала», `InstrumentExternalRules` Auditable-формулировка) закрыты на
  месте. **Концепт-гейт `CODE` пройден.** Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-2.md`. Готовность к `CODE` —
  перевод за пользователем.
- **Шаг 5 → `CODE` (2026-06-20):** написан код по утверждённой концепции
  (47 файлов в working tree, staged; компилируется `clean test-compile`,
  без deprecation). Материализован: `InstrumentExternalRules` (доменная модель
  + JSONB-навес на `instruments`, миграция `V8`, маппер + JSON-конвертер,
  domain↔persistence DataService, `InstrumentExternalRulesSyncJob` + фасад +
  конфиг + домаппинг OKX-полей max-size/`lever`/`ctType`/`ctValCcy`); расчётный
  слой (`MarketPriceData`-сборка по REST ticker; `Calculated*`-RVO достроены до
  полной структуры + enum'ы; `CalculationContext(Factory)`, `PriceCalculator`,
  `SizeCalculator` с risk-bounded сайзингом, `StrategyActionCalculator`); risk-слой
  (`RiskValidator`, `RiskBlockResolver`, RVO `RiskValidationResult`/`RiskCheckResult`/
  `RiskBlockAction` + `RiskCheckCode`). **Аппрув-гейт:** прогнаны три независимых
  адверсариальных ревьюер-фокуса (`conventions`/`performance`/`disaster`; `security`
  деактивирован до шага 9) — без блокеров; clean-code находки и реальные
  safety-фиксы (ctVal=0 → controlled error, `NumberFormatException` на сырьё →
  null, гард SL/TP/trailing > 0 после округления, clamp reduce-fraction 0..1,
  устранён дубль чтения фазы) закрыты на месте. Отчёт и форвард-заметки —
  `.claude/work/progress/phase-1-step-5-code.md`. Финальный аппрув CODE и переход к
  `SYNC_DOCS_FROM_CODE` — за пользователем. **Концепт-инкременты на CODE**
  (требуют пост-хок концепт-гейта §6a): `CalculationContext` несёт каталоги
  настроек индикаторов/структуры для резолва по ключу; внутренний
  `CalculationException`; `InstrumentExternalRulesDataService` вместо
  doc-имени `InstrumentExternalRulesService`.
- **Шаг 5 → `SYNC_DOCS_FROM_CODE` (2026-06-20):** доки приведены к
  утверждённому коду (docs←code). Независимый фокус `divergence` выписал ~44
  расхождения по 16 докам; реконсилированы `knowledge-curator`. Ключевое:
  `InstrumentOkxResponse` — добавлены реально присутствующие поля
  (`ctType`/`ctValCcy`/`maxLmtSz`/`maxMktSz`/`maxTriggerSz`/`maxStopSz`), снято
  ложное «не входят»; `MarketPriceData` (модель+маппинг) — снят forward-блок
  «класса ещё нет» (код есть); `CalculationContextFactory` —
  `InstrumentExternalRulesService`→`InstrumentExternalRulesDataService`, убран
  `MarketPhaseService`, добавлен `StrategyDataService`; `RiskValidator` — входы
  (читает rules сам, сигнатура 2 арг.), фактические проверки, без комиссий
  (фаза 1); `RiskCheckResult`/`CalculatedPrice` — размечен реально эмитимый/
  используемый субсет vs forward; `PriceCalculator` — расширенный словарь
  `StrategyPriceSource` помечен forward (резолв через `baseType`/
  `StopLossCalculationType`/`TrailingSettings`); `InstrumentExternalRules` —
  снято фантомное поле `id`. Каскад: doc `InstrumentExternalRulesService.md`
  (компонента, которой в коде нет) → `git mv` в
  `InstrumentExternalRulesDataService.md` + переписан. Отчёт —
  `.claude/work/progress/phase-1-step-5-sync-docs-from-code.md`. **Остаётся до
  `DONE`:** пост-хок концепт-гейт §6a (`concept-review` по пост-sync докам для
  концепт-инкрементов CODE: каталоги настроек в `CalculationContext`,
  внутренний `CalculationException`) — в этом прогоне не запускался.
- **Шаг 5 → `DOCS_CHECK_3` (пост-хок концепт-гейт §6a, 2026-06-20):**
  независимый `concept-review` по пост-sync докам — **не чисто**, 2
  несогласованности doc↔doc, обе гейтят `DONE`. **C1** — механизм
  controlled-ошибки расчёта описан двумя способами (возврат `CalculationError`
  в калькулятор-доках vs бросок `CalculationException`/`NO_MARKET_PRICE` в
  `CalculationContextFactory.md`); `CalculationException` нигде не определён,
  catch-граница не описана, код не зарегистрирован. Разрешение —
  docs←code-выравнивание под as-built (слой возвращает Result; суб-калькуляторы
  бросают внутри, orchestrator ловит), без развилки пользователя. **C2**
  (явно запрошенный code↔doc-чек) — комиссии: `per-trade-risk-policy.md` числит
  их входом риск-расчёта, `RiskValidator.md`/`SizeCalculator.md` — «опущены
  (фаза 1)»; decision откладывает только проскок, не комиссии → прямое
  расхождение. C2 — policy-развилка пользователя (`trading-specialist`-хвост).
  Increment 1 (каталоги `CalculationContext`) — когерентен. Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-3.md`. Нужен `GAPS_CLOSE_3`
  (C1 curator-выравнивание + C2 по решению пользователя), затем `DOCS_CHECK_4`.
- **Шаг 5 → `GAPS_CLOSE_3` + `DOCS_CHECK_4` (2026-06-20) — чисто.** **C1**
  закрыт docs←code-выравниванием механизма controlled-ошибки (заведена §«Механизм
  сигнализации» в `CalculationError.md`: суб-калькуляторы бросают
  `CalculationException`, `StrategyActionCalculator` ловит → `CalculationError` в
  `ERROR`-результате; `NO_MARKET_PRICE` как пример кода; формулировки
  `PriceCalculator`/`SizeCalculator`/`CalculationContextFactory`/
  `StrategyActionCalculator`/`strategy-action-calculation` выровнены).
  **C2** — по решению пользователя комиссии отнесены к **шагу 7**: decision держит
  их концептуальным входом, код-учёт отложен (§«Учёт комиссий — отложен к шагу 7»
  в `per-trade-risk-policy.md`); `RiskValidator`/`SizeCalculator` ссылаются на
  отсрочку; форвард-пункт в `backlog.md` §Шаг 7. Подтверждающий `DOCS_CHECK_4`
  (независимый) — **чисто** (C1/C2 CLOSED-CLEAN, новых doc↔doc-несогласованностей
  нет). **Все гейты `DONE` (CODE-фокусы / SYNC `divergence` / §6a концепт) пройдены
  с зафиксированным исходом — перевод в `DONE` за пользователем.** Отчёт —
  `.claude/work/progress/phase-1-step-5-docs-check-3.md` §Закрытие.
- **Шаг 5 → `DONE` (2026-06-20).** Все условия §7 выполнены с зафиксированным
  исходом: CODE (фокусы `conventions`/`performance`/`disaster`, находки закрыты),
  `SYNC_DOCS_FROM_CODE` (`divergence` прогнан, реконсилировано), пост-хок
  концепт-гейт §6a (`DOCS_CHECK_3 → GAPS_CLOSE_3 → DOCS_CHECK_4` — чисто).
  Ролляп фазы — без изменений (`IN_PROGRESS`: шаги 1-5 `DONE`, 6-11 `HOLD`).
  Открытый хвост — **non-gating форвард**: комиссии в риск-расчёте → шаг 7;
  бесстоповый risk-creating вход → шаг 6; остаток INSTR-Q2 (тайминг
  set-leverage) → шаг 6; STRAT-Q4 (якорь allocation %); провизорный численный
  лимит риска (бэктест/пользователь).
- **Шаг 4 → рантайм-хвост закрыт (2026-06-12, инфра-сессия):** первый
  реальный boot обоих профилей — зелёный. Заведён dev/test-сплит БД
  (compose `postgres` 5440 / `postgres-test` 5441); `application.yaml`
  разнесён на базовый + `application-{prod,test}.yaml`; datasource и
  OKX-креды — per-profile через Vault (`spring.config.import`). Вскрыты и
  закрыты 3 пробела Boot-4 split-autoconfig (`restclient`, `jackson2`,
  `flyway` → `pom.xml`; `backlog.md` §Инфра-долг). **Рантайм-подтверждение
  (снимает v46-хвост шага 4):** Flyway применил `V1`-`V7` на обеих БД
  (`tradingbot` / `tradingbot_test`), схема создана (закрыт хвост Flyway
  `V6`/`V7`); OKX-прокси (prod) вернул реальный balance — подпись +
  Vault-креды + Jackson-сериализация end-to-end. И-2 (demo trailing)
  разблокирован (у `test` теперь demo-креды); сама demo-проверка —
  обычный пункт `backlog.md` §Хвост шага 4, не блокирующий хвост. Ролляп
  фазы без изменений (`IN_PROGRESS`: 1-4 `DONE`, 5-11 `HOLD`). Детали —
  снапшот v47.
- **Шаг 6 → `DOCS_CHECK_1` (2026-06-22):** стартован шаг «FSM + живая
  оркестрация» (`TOOLING` без новых артефактов — фокусы `concept`/`trading`
  активны). Статусный костяк (процесс `deal-management`, `DealStateMachine`,
  7 handler'ов, lifecycles, command-layer) **в основном материализован**
  миграцией из архива; пробелы сосредоточены на **петле, финализации и
  операционной оболочке оркестратора**. **Не чисто** — 15 пробелов
  (N1-N15), 8 эскалаций (Э1-Э8); торговый блокер — TR1 (бесстоповый
  risk-creating вход). Гейтят `CODE`: N1 (error-политика), N2-N4/DEAL-Q1
  (финализационная под-спина), N5-N6/CMD-Q5-Q6 (REPLACE-владелец / «действие
  vs команда»), N9/TR1 (защита входа), N8 (оболочка джоба), N10 (set-leverage),
  N11 (`maxAttempts`), N12 (Precheck-чистота); N7/D-M1 — жёсткий гейт `DONE`.
  Нужен `GAPS_CLOSE_1`. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-1.md`.
- **Шаг 6 → `GAPS_CLOSE_1` (2026-06-22):** пробелы `DOCS_CHECK_1` закрыты.
  **N1** — error-политика (`docs/rules/error-handling-policy.md` +
  `docs/rules/instrument-hold.md`; TBD `codestyle` снят). **N2-N4/DEAL-Q1**
  — финализационная под-спина: дом retry-state `DealFinalizationState`
  (модель + lifecycle + `docs/decisions/deal-finalization-state-materialization.md`),
  4 executor-дока (`FINALIZE_*`/`MARK_*`), путь эмиссии (`ServiceCommand`
  +`dealFinalizationStateId`, `ServiceCommandFactory`). **N9/TR1** — инвариант
  `docs/rules/risk-creating-entry-protection.md` + снят fail-open `RiskValidator`
  (код `RISK_CREATING_ENTRY_WITHOUT_STOP`). **N5-N6/CMD-Q5-Q6** —
  `docs/decisions/action-orchestration-vs-command.md` (REPLACE-владелец —
  петля/`DealStateMachine`; `KILL_SWITCH` — команда). **N7-N8/D-M1** —
  `DealOrchestratorJob` оболочка + concurrency-guard (БД-блок на весь проход).
  **N10** — set-leverage перед ордером в `PRECHECK` (INSTR-Q2 продвинут).
  **N11** — авторитет `maxAttempts` = policy. **N12/CMD-Q4** —
  инструмент-скоупный read вне command-layer (Precheck-часть закрыта). **DEAL-Q2**
  — терминальный контракт (`Deal.md`). **N13-N15** — гигиена (стале-ссылки,
  finalization-список, scope-нота `account-bills`). Закрыты DEAL-Q1/DEAL-Q2/
  CMD-Q5/CMD-Q6; продвинуты INSTR-Q2/CMD-Q4. Далее — подтверждающий
  `DOCS_CHECK_2`. Закрытие — `.claude/work/progress/phase-1-step-6-docs-check-1.md`
  §Закрытие.
- **Шаг 6 → `DOCS_CHECK_2` (2026-06-22):** подтверждающий прогон после
  `GAPS_CLOSE_1` — три независимых ревьюер-субагента (concept ×2 + trading).
  **Все 15 пробелов `DOCS_CHECK_1` (N1-N15) + торговый блокер TR1 +
  DEAL-Q1/DEAL-Q2/CMD-Q5/CMD-Q6 подтверждены закрытыми чисто** (верификация
  атрибуции по каждому целевому доку; ripple-проверки финализации (a/b/c)
  пройдены; гейтящих open-questions нет; TR2-TR4 остаются forward, не
  регрессировали). **Почти чисто — одна минорная негейтящая гигиена-рябь R1:**
  `deal-management.md:63-64` несёт устаревший безусловный инвариант
  `resultProfit` для всех terminal — расходится с DEAL-Q2-контрактом
  (обязателен только для чистого `CLOSED`, `EMERGENCY_CLOSED` освобождён);
  правки DEAL-Q2 не пробросились в обзорный процесс-док (исполнительные доки
  контракт несут верно). По строгому гейту «чистый `DOCS_CHECK`» R1 держит
  прогон формально не-чистым. Нужен микро-`GAPS_CLOSE_2` (одна строка) +
  подтверждающий `DOCS_CHECK_3` (либо принять R1 как гигиену в составе
  `GAPS_CLOSE_2`); затем гейт `CODE` чист. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-2.md`.
- **Шаг 6 → `GAPS_CLOSE_2` (2026-06-22):** закрыта единственная находка
  `DOCS_CHECK_2` — **R1** (реконсиляция формулировки). `deal-management.md`
  §«Статусная механика и recovery»: обязательность `resultProfit`/
  `resultProfitCurrency` ограничена **чистым** terminal `CLOSED`, ошибочный
  `EMERGENCY_CLOSED` — по терминальному контракту (`docs/lifecycles/Deal.md`
  §«Терминальный контракт финализации», DEAL-Q2). Правка-cleanup (выводима из
  принятого DEAL-Q2-контракта, без вариантов). Далее — подтверждающий
  `DOCS_CHECK_3`.
- **Шаг 6 → `DOCS_CHECK_3` (2026-06-22):** узкий подтверждающий прогон после
  `GAPS_CLOSE_2` (независимый ревьюер-субагент, concept-фокус) — **чисто**.
  **R1** подтверждён закрытым чисто (`deal-management.md` согласован с
  DEAL-Q2-контрактом `Deal.md`); sweep по `docs/` — других стале-копий
  безусловного `resultProfit`-инварианта нет; новой ряби нет. **Гейт `CODE`
  пройден** (`roadmap-step-execution.md` §«Гейт `CODE` — чистый `DOCS_CHECK`»):
  concept — этот прогон, trading — чисто на `DOCS_CHECK_2` (поверхность не
  менялась). Шаг 6 готов к `CODE`; перевод за пользователем. Жёсткие гейты
  `DONE` (D-B3 / реализация D-M1) — на `CODE`/`DONE`. Отчёт —
  `.claude/work/progress/phase-1-step-6-docs-check-3.md`.
- **Шаг 6 → `CODE` (2026-06-22):** написан код по утверждённой концепции
  (~50 файлов в working tree, staged; `mvn clean compile` чисто на JDK 25,
  без deprecation/warnings). Материализованы: финализационная под-спина
  (`DealFinalizationState` + entity/repo/dataservice/mapper, миграция `V9`;
  4 финализационных executor'а; эмиссия через фабрику + retry-anchor в
  диспетчере), FSM (`DealStateMachine` + 7 handler'ов + `DealFsmSupport`/
  `DealActionPlanner`/`MarketConditionContextFactory`), оболочка петли
  (`DealOrchestratorJob` + `EntryScannerJob` + фасады + конфиг + триггеры),
  **D-M1** (`OrchestratorPassLock` — БД advisory lock на проход), **D-B3**
  (recovery-by-clientId в submit-executor'ах), N9/TR1 (защита бесстопового
  входа), set-leverage, error-политика (`@RestControllerAdvice` +
  `ErrorApiResponse`). **Аппрув-гейт:** три независимых адверсариальных
  фокуса (`conventions` 0/2/9, `performance` 0/2/3, `disaster` 2/4/3;
  `security` деактивирован до шага 9) + независимая верификация фиксов.
  Закрыты оба disaster-blocker'а (B1 RETRY_PENDING зависание action-команд;
  B2 несоблюдение `nextRetryAt`), major'ы M3 (финализация FAILED → ошибочная
  тропа), M4 (терминальный гейт по live orders/algo), M5 (частичный
  unique-index «одна сделка на инструмент»), perf-M1 (индексы `deals`), все
  conventions-находки. Форвард (осознанно, фаза 1): REPLACE-leg-оркестрация,
  биржевой REST в `@Transactional` (M6), перф M2-M5, tech-radar-запись по
  raw-JDBC advisory lock. Отчёт и концепт-инкременты для пост-хок гейта §6a —
  `.claude/work/progress/phase-1-step-6-code.md`. Финальный аппрув `CODE` и
  переход к `SYNC_DOCS_FROM_CODE` — за пользователем.
- **Шаг 6 → сверка scope `CODE` на полноту (2026-06-22):** поставленный `CODE`
  сверён построчно со scope (роадмап-строка + граница 6↔7 + закрытия
  `GAPS_CLOSE_1` N1-N15 + жёсткие гейты), не только на качество. **Весь scope —
  built**, кроме двух **обоснованных deferral'ов** (зафиксированы с владельцами и
  треком, не молча): **D1** REPLACE-leg-оркестрация (фабрика ног возвращает
  empty; самостоятельный refinement, базовой петле фазы 1 не нужен; `backlog.md`
  §Хвост шага 4) и **D2** error-градация уровни 3-4 — реактивный enforcement
  холдов instrument/exchange + `AnomalyReport`-реакция (зависит от `AnomalyReport`
  ops шага 8 и status-lifecycle backlog п.9; порог серии неудач — провизорный;
  `backlog.md` §Шаг 6). Внешняя поверхность + уровни 1-2 + `KillSwitchExecutor` +
  преконтроль N9/TR1 — построены. На сверке снят один дефект: орфан-метод
  `DealFsmSupport.killSwitchCommand()` (эмиссия `EXECUTE_KILL_SWITCH` без
  вызовов; конвенц-фокус пропустил) удалён. **set-leverage** — намеренное сужение
  «каждый ордер → открывающий» в submit-executor'е подтверждено как
  §6a-инкремент (доки сами отнесли тайминг к шагу 6). Жёсткие гейты `DONE`
  (D-B3 / D-M1) — оба built. Сверка — `phase-1-step-6-code.md` §Сверка scope;
  финальный аппрув `CODE` за пользователем.
- **Шаг 6 → `SYNC_DOCS_FROM_CODE` → §6a → `DONE` (2026-07-01…03):** аппрув
  `CODE` дан; `SYNC_DOCS_FROM_CODE` (фокус `divergence`, docs←code) выровнял 52
  дока под as-built (Stage 2/3-рефактор + фиксы ревью): `ServiceCommandFactory`
  распилен на `StrategyActionOrchestrator`+per-type executor'ы +
  `DealFinalizationCommandFactory`; `OrchestratorPassLock`→`JobExecutionGuard`;
  kill-switch package-move + реактивность через `HoldSignal`→`SafetyHoldCoordinator`
  (снапшот v64). **Пост-хок концепт-гейт §6a** (концепт-инкременты на CODE):
  `DOCS_CHECK_4` — 6 пробелов (2 блокера: таксономия kill-switch «команда» vs
  side-executor, частичный unique-index `uk_deal_active_instrument`; 4 не-блокера:
  inline set-leverage у owner-дока, спека `SafetyHoldCoordinator`/`HoldSignal`,
  placeholder-ZERO, ссылка §8.C). `GAPS_CLOSE_4` закрыл все 6 docs←code
  (kill-switch→side-executor; §Персистентность `Deal.md` + `trading-constraints.md`
  app-gatekeeper+DB defense-in-depth; inline set-leverage, **INSTR-Q2 закрыт**;
  новые `SafetyHoldCoordinator.md`/`HoldSignal.md`/`KillSwitchService.md`;
  placeholder-ZERO примирён; §8.C). `DOCS_CHECK_5` — подтверждено, 1 остаток
  (`AnomalyReport.scope` docs↔code-лаг); `GAPS_CLOSE_5` — `scope: HoldScope`
  добавлено. **§6a ПРОЙДЕН** — все гейты `DONE` (CODE-фокусы / `divergence` /
  §6a; жёсткие D-B3/D-M1 built) удовлетворены с зафиксированным исходом. Ролляп
  фазы без изменений (`IN_PROGRESS`: шаги 1-6 `DONE`, 7-11 `HOLD`). Отчёт §6a —
  `.claude/work/progress/phase-1-step-6-docs-check-4.md`. Дельта `GAPS_CLOSE_4/5`
  — staged для коммита в IDEA.
- **Шаг 7 → `DOCS_CHECK_1` (2026-07-03):** стартован шаг «Сделки и P&L»
  (`TOOLING` без новых артефактов — фокусы `concept`/`trading` активны). Scope
  (граница 6↔7): расчёт числа `resultProfit` на терминале (вкл. PnL
  `EMERGENCY_CLOSED`) + агрегация фактов в `Deal`; заменяет placeholder-ZERO
  шага 6. Форвард-долг на шаг 7: комиссии в риск-расчёте (§6a шага 5),
  `positions-history` realizedPnl-разложение (В-3), funding SWAP (В-6/OKX-Q3 —
  выбор пути), `trade-fee` (В-7), граница audit/история (шаг 8) vs PnL-число.
  Прогон — три независимых ревьюер-субагента (concept ×2 + trading); CC
  верифицировал ключевые атрибуции грепом. **Не чисто — 6 пробелов, все сходятся
  к центральному блокеру G1** (стадия 0): источник данных `resultProfit` не выбран,
  три дока противоречат (fills/`TradeFill` `Deal.md` ↔ bills/`DealCashFlow`
  `account-bills.md` ↔ positions-history/`realizedPnl` `position.md`); OKX-Q1/Q3
  открыты. Торговый инвариант (TR-1/TR-2/TR-3, блокеры) задаёт направление: число =
  **net** realized P&L (комиссии+funding+liqPenalty) на любом терминале →
  fills-only исключён (fills не несут funding/liqPenalty). G2 (агрегирующая модель
  name-level), G3 (компонент-расчёта не назначен), G4 (fills не агрегирует
  algo-exit) — на выбранном пути G1; G5 (число `EMERGENCY_CLOSED`), G6 (комиссии в
  сайзинге — policy + нюанс скоупа) — отдельные хвосты. Обход остановлен на стадии
  0. **Исход → `GAPS_CLOSE_1`** (после решений пользователя по G1-пути и G6-policy).
  Отчёт — `.claude/work/progress/phase-1-step-7-docs-check-1.md`.
- **Шаг 7 → `GAPS_CLOSE_1` (2026-07-03):** пробелы `DOCS_CHECK_1` закрыты
  согласованными с пользователем решениями (стадия 0 расчищена). **G1** — источник
  числа `resultProfit` выбран (новый `docs/decisions/result-profit-source.md`):
  заголовочное число = **net realized P&L готовым из positions-history**
  (`realizedPnl = pnl+fee+fundingFee+liqPenalty`), категорийная разбивка — из bills
  (`DealCashFlow`), сумма bills сверяется с net; **fills-only отвергнут**
  (`OkxFillResponse` без `fundingFee`/`liqPenalty`). Примирены три расходящихся дока
  (`Deal.md` §Итоговый PnL, `account-bills.md`, `position.md` §История). **OKX-Q1
  закрыт** (persisted `TradeFill` не вводится; инспекция native: positions-history
  несёт `closeAvgPx`/`openAvgPx` → fills для avg-цены не нужны; `REFRESH_FILLS` —
  кандидат на снятие, диспозиция stage-1). **OKX-Q3 закрыт** (funding — через
  bills/positions-history, не `funding-rate-history`; В-3/В-6 разрешены). **G5** —
  число на `EMERGENCY_CLOSED` = фактический realized net вкл. `liqPenalty` (остаток
  DEAL-Q2 закрыт). **G3** — расчёт назначен `FinalizeDealExitExecutor` (число +
  разбивка + сверка), запись на терминале — `MarkDealClosedExecutor`
  (placeholder-ZERO снят). **G6** — прогнозная комиссия включена в риск-сайзинг
  (ставка `trade-fee`, В-7 активирован; `per-trade-risk-policy`/`RiskValidator`/
  `SizeCalculator`). **G4** — resolved-by-path (число не из fills). **G2** — целевые
  носители (positions-history-снапшот + `DealCashFlow`, native
  `OkxPositionsHistoryResponse`) зафиксированы; **структурная спека — стадии 1-2, на
  `DOCS_CHECK_2`**. Реконсилировано 20+ доков + open-questions/backlog/manifest;
  дельта staged. Отчёт — `.claude/work/progress/phase-1-step-7-gaps-close-1.md`.
  **Исход → перезапуск `DOCS_CHECK_2`** (стадии 1-2: процессы/модели/mapping/native
  под выбранный путь).
- **Шаг 7 → `DOCS_CHECK_2` (2026-07-03):** descend на стадии 1-2 под выбранный путь
  (positions-history + bills). Три независимых ревьюер-субагента (concept ×2 —
  механика/стадия 1 и модели/стадия 2 — + trading); CC верифицировал несущие
  атрибуции грепом/`ls` (нет refresh-команд под positions-history/bills; нет
  `MARK_DEAL_EMERGENCY_CLOSED`; `MarkDealErrorExecutor` пишет только `ERROR`;
  `ErrorHandler` без `FINALIZE_DEAL_EXIT`; нет `OkxPositionsHistoryResponse`/
  positions-history-снапшота/`DealCashFlow`; нет fee-поля в `CalculationContext`).
  **Не чисто — 13 находок (11 гейтят).** Кластеры: (A) три носителя пути только
  name-level — native `OkxPositionsHistoryResponse` (N1), positions-history-снапшот
  +имя доменной сущности (N2), модель/mapping/персистенция/линковка `DealCashFlow`
  (N3-N5); (B) механика стадии 1 — **добыча фактов positions-history/bills не
  назначена** (N6, центр тяжести; ни одна `REFRESH_*` их не производит), носитель
  staged-числа между `FINALIZE_EXIT` и `MARK_CLOSED` (N7, ломается об идемпотентность),
  владелец+провенанс аварийного числа (N8); (C) поток ставки `trade-fee` в отрезанный
  от биржи сайзинг (N9); (D) реакция на расхождение сверки bills↔net + epsilon +
  cross-ccy (N10); (E) торговые блокеры — **N8** (контракт `EMERGENCY_CLOSED`
  неисполним для compute-failure-провенанса → усечение левого хвоста R, жёсткий гейт)
  и **N11** (непроверенный инвариант агрегации partial-close на `posId` → рантайм-
  верификация); (F) не гейтят — `REFRESH_FILLS`-диспозиция (N12, ripple по 6
  handler'ам) и funding-как-holding-cost без форвард-дома (N13, форвард к экспектанси/
  фаза 2). Владельцы: `solution-designer` (N2/N3/N5/N6/N7/N8/N9/N10/N12) + `integrator`
  (N1/N4/N11-рантайм) + `trading-specialist` (N8/N11/N13); хвост пользователя тонкий
  (N10 epsilon, N13 scope). Ролляп фазы без изменений (`IN_PROGRESS`: 1-6 `DONE`, 7
  в `DOCS_CHECK_2`, 8-11 `HOLD`). Отчёт — `.claude/work/progress/phase-1-step-7-docs-check-2.md`.
  **Исход → `GAPS_CLOSE_2`.**
- **Шаг 7 → `GAPS_CLOSE_2` (2026-07-04):** descend-закрытие стадий 1-2 —
  13 находок закрыты, механика материализована. **Якорь** — новый
  `docs/decisions/pnl-finalization-mechanics.md`. **N6+N12:** добыча P&L-фактов —
  новые refresh-команды **`REFRESH_POSITIONS_HISTORY`** (positions-history-снапшот)
  + **`REFRESH_BILLS`** (`DealCashFlow`), заменяют снятый `REFRESH_FILLS` (его
  order-fill-метрики покрыты `REFRESH_ORDER`); каскад снятия по ~18 докам
  (агент). **N1-N5 (носители):** созданы native `OkxPositionsHistoryResponse`,
  снапшот `PositionCloseResultExternalSnapshot` (`mapping/PositionCloseResult.md`),
  модель+mapping+таблица `DealCashFlow` (`deal_cash_flows`, FK `deal_id`,
  `UNIQUE(external_bill_id)`; линковка по окну+`instId`+`ccy`, bills не несут
  dealId). **N7:** носитель staged-числа = **поле `Deal`** — `FINALIZE_DEAL_EXIT`
  пишет `resultProfit` на `Deal` в одной транзакции с `COMPLETED` (рестарт-safe),
  `MARK_DEAL_CLOSED` ассертит+терминализует (не пишет число). **N8:** аварийный
  терминал получил владельца — новая команда/executor **`MARK_DEAL_EMERGENCY_CLOSED`**
  (`DealFinalizationType.MARK_EMERGENCY_CLOSED`, симметрично `MARK_CLOSED`);
  провенанс-контракт **исполним** — best-effort: (a) ликвидация → фактический net;
  (b) отказ расчёта → `resultProfit = null` c маркером «неисчислимо» (**не ноль**),
  число не зануляется, левый хвост R не усекается. **N9:** ставка `trade-fee` —
  дом на `InstrumentExternalRules` (навес), калькуляторы читают через
  `CalculationContext.instrumentExternalRules` (без нового поля/fetch). **N10:**
  сверка bills↔net → `AnomalyReport`, **не блокирует** финализацию (число =
  positions-history net); epsilon провизорный (хвост пользователя); cross-ccy guard.
  **N11:** инвариант агрегации positions-history выписан + **рантайм-верификация**
  (test-план §AG1.5, ⏳ PENDING, гейтит CODE). **N13:** разделяющий довод
  комиссия-в-R / funding-в-post-cost-expectancy зафиксирован, форвард-дом — фаза 2.
  Enum `ServiceCommandType` 16→18. Реконсилировано ~35 доков (4 параллельных
  агента на непересекающихся наборах + ядро). Ролляп фазы без изменений
  (`IN_PROGRESS`: 1-6 `DONE`, 7 в `GAPS_CLOSE_2`, 8-11 `HOLD`). Отчёт —
  `.claude/work/progress/phase-1-step-7-gaps-close-2.md`. **Исход → `DOCS_CHECK_3`.**
- **Шаг 7 → `DOCS_CHECK_3` (2026-07-04):** подтверждающий прогон после
  материализации стадий 1-2. Три независимых ревьюер-субагента (concept ×2 —
  механика + модели/mapping — + trading); CC верифицировал несущие атрибуции
  грепом/`ls`. **Ядро механики N6/N7/N8/N12 — проведено полно и согласованно**
  (enum=18, N7 tx-связка «FINALIZE пишет число на `Deal` / MARK_CLOSED ассертит»
  везде, N8 терминал `MARK_DEAL_EMERGENCY_CLOSED` с владельцем, PositionCloseResult-
  и DealCashFlow-пути field-level согласованы). **Не чисто — 8 находок (3 гейтят):**
  **H1 (докогейт)** — N9 fee-wiring доспецифицирован наполовину: модель
  `InstrumentExternalRules` получила fee-поля, но **нет** native `OkxTradeFeeResponse`,
  **нет** маппинга ставки, `mapping/InstrumentExternalRules` **отбрасывает `groupId`**
  (а резолв feeGroup SWAP на нём завязан), `InstrumentExternalRulesSyncJob` не описан
  дочитывать `trade-fee` → `takerFeeRate()` останется null; **H2** — гранулярность
  bills: маппинг выбрасывает native `fee`/`pnl`, если OKX эмитит комбинированный
  trade-bill (`balChg=pnl+fee`) → `TRADE_FEE` недосчитан (гейтит **разбивку**, не
  заголовочное число; рантайм-верификация + вернуть `fee` в used); **H3/N11** —
  инвариант агрегации positions-history (рантайм-гейт, уже трекается §AG1.5). Не
  гейтят: гигиена ×5 (H4 — `lifecycles/DealFinalizationState` без `MARK_EMERGENCY_CLOSED`,
  `risk-validator-scope` без него в списке, мёртвые ссылки на удалённый
  `RefreshFillsExecutor`, неполные cross-ref), H5 (`Instant` vs `OffsetDateTime` в
  снапшоте), H6 (форвард: null-drop смещает ожидаемость — «пометки достаточно» торгово
  неверно, → фаза 2), H7 (переякорить epsilon на Σ|amount|), H8 (инвариант «комиссии в
  USDT, не OKB»). **Торговый синтез:** три механизма (N8 null-drop, N11 недосчёт,
  опущенный гэп-проскок) смещают левый хвост оптимистично согласованно — форвард-фокус
  фазы ожидаемости. Владельцы `GAPS_CLOSE_3`: `integrator` (H1/H2 native/mapping/sync) +
  `knowledge-curator` (гигиена H4/H5); форвард H6-H8. Ролляп фазы без изменений
  (`IN_PROGRESS`: 1-6 `DONE`, 7 в `DOCS_CHECK_3`, 8-11 `HOLD`). Отчёт —
  `.claude/work/progress/phase-1-step-7-docs-check-3.md`. **Исход → `GAPS_CLOSE_3`**
  (узкий) + **рантайм-верификация N11/H2 до `CODE`**.
