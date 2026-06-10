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
| 4 | Команды и их жизненный цикл (ServiceCommand: submit/amend/cancel/close/REFRESH; исполнители; lifecycle; факт и реконсиляция через REFRESH, не ACK; ведение Position/Order) | GAPS_CLOSE_2 |
| 5 | Риск-преконтроль (валидация перед отправкой: размер, ограничения инструмента, reduce-only, лимиты) | HOLD |
| 6 | FSM (состояния и переходы сущностей — связующее звено) | HOLD |
| 7 | Сделки и P&L (DealOrchestratorJob — агрегирование в Deal, P&L; он же оркестрирует торговый цикл сигнал→команда→позиция) | HOLD |
| 8 | AnomalyJob (полноценный, операционная детекция аномалий состояния/исполнения) | HOLD |
| 9 | Безопасность (auth-инфраструктура: Spring Security, `@PreAuthorize`, `SecurityFilterChain`; конфигурация секретов через Vault; реактивирует фокус `security-review`) | HOLD |
| 10 | Тесты | HOLD |
| 11 | Фронт | HOLD |

## Примечания

- **Фронт (шаг 11)** — простой, для прогонов. Полноценный фронт
  появится после архитектурного рубежа.
- **Безопасность (шаг 9)** — строит auth-инфраструктуру (Spring
  Security, `@PreAuthorize`, `SecurityFilterChain`) и конфигурацию
  секретов (Vault). Содержание прорабатывается docs-first на самом
  шаге. На нём реактивируется фокус `security-review`.
  Форвард-материал (Vault/секреты) — `.claude/work/backlog.md`
  (раздел шага «Безопасность»).
- **Тесты (шаг 10)** и **Фронт (шаг 11)** — отдельные шаги
  фазы, исполняются по тому же процессу docs-first.
- Под-шаги внутри каждого шага заранее не дробятся; они
  появляются в процессе исполнения (см. процесс).
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
  `.claude/work/progress/phase-1-step-4-docs-check-1.md`.
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
  `.claude/work/progress/phase-1-step-4-docs-check-2.md`. Шаг готов к
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
- **Шаг 2, фиксация задним числом:** между `GAPS_CLOSE_7` и
  `DOCS_CHECK_8` пройден повторный под-шаг `TOOLING` (торговый
  совет: агент `trading-specialist`, дистиллят корпуса
  `.claude/library/trading/distilled/`, активация фокуса
  `trading-review`; 2026-06-04/05). В таблицу в моменте не
  проставлялся — рассинхрон закрыт на `GAPS_CLOSE_8` этой пометкой
  (таблица держит только текущий статус).
