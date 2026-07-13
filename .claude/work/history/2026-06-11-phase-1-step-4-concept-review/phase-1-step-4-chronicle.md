# Хроника шага 4 Фазы 1 — команды и их жизненный цикл

## На какой вопрос отвечает этот файл

Какова хроника прохождения шага 4 Фазы 1 по под-шагам, включая
ретро-ревью шагов 1-3 и рантайм-хвост шага 4 (перенесена из
phase-1.md при расщеплении 2026-07-06).

## Хроника

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
