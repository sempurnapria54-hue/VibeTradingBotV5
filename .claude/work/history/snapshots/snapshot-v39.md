# Snapshot v39

**Дата:** 2026-06-09.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (тема «редизайн условной фазы рынка и
смежное» закрыта в каноне: редизайн фазы + fork A (ER) + A-bis +
дообучение агентов). Плановое завершение темы — ветка закрыта полностью;
новый чат стартует с PK-префлайта и берёт хвост по выбору пользователя.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаги 1-2 — `DONE`; **шаг 3 — `GAPS_CLOSE_4`**
(частично закрыт: фаза/скоринг + fork A — в каноне; структурное ядро и Н6
— отдельная закрываемость; целиком `GAPS_CLOSE_4` не закрыт); шаги 4-11 —
`HOLD`. Ветка — `claude-audit`. Все правки сессии — в `docs/` и
`.claude/` (staged, не коммичено); **кодовая база отстаёт** — Java
step-2 ещё реализует старую скоринговую модель (форвард-долг, хвост 2).

Путь к точке: после `DOCS_CHECK_5` (отвалидировал пайплайн, snapshot v37)
шёл содержательный `GAPS_CLOSE_4`. Фундаментальная развилка по фазе
(скоринг → авторские условия, bucket-2) проработана и зафиксирована в
прошлом чате; **эта сессия** закрыла зависимые от неё fork A и A-bis,
снесла выделенный `EFFICIENCY_BELOW_THRESHOLD`, вывела принцип грамматики
и дообучила агентов.

## Закрыто в каноне (тема целиком)

- **Редизайн условной фазы.** `MarketPhase.Type` — авторскими условиями
  (first-match-список `StrategyMarketPhaseRule {level, type, condition}`)
  вместо скоринга; `MarketPhaseParams` распущен целиком (JSONB `params`
  исчез); `confidenceScore` удалён; `MarketPhaseClassifier` — stateless
  first-match поверх `StrategyConditionEvaluator`; анти-whipsaw
  операнд-уровневый (сглаживающие периоды индикаторов + структурный
  `breakoutConfirmationBars`). Decision —
  `docs/decisions/market-phase-conditional-classification.md`.
- **Fork A → ER в каталоге** (`IndicatorValue.Type.EFFICIENCY_RATIO`;
  `EfficiencyRatioParams(period)`, `warmup = period`;
  `EfficiencyRatioValue(efficiencyRatio)`). Контекст-сплит:
  `MarketStructureAnalyzer` (выжил как вычисляющий компонент) потребляет
  тот же каталожный ER опциональным `IndicatorValue`-входом — единый
  источник, без дубль-счёта; fallback на прокси EMA-наклон/ATR. Decision —
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md`.
- **A-bis → свёртка** `EFFICIENCY_BELOW_THRESHOLD` в `INDICATOR_COMPARE`
  (чистый алиас одного сравнения; ER-тест в обе стороны: `LT` шум/range,
  `GT` тренд). Выведен принцип грамматики —
  `docs/rules/condition-ruletype-granularity.md`.
- **Дообучение агентов.** `solution-designer` — блок «Эвристики
  проработки развилки» (4 эвристики: где может жить свойство; риск-аппетит
  не закрывается инженерным дефолтом; контекст крена мог устареть;
  аналогия требует структурной сверки). `trading-specialist` —
  «Риск-рефлекс» (приемлемость остаточного риска — хвост пользователя).

## Хвосты (в новый чат, по приоритету)

1. **Сейв Н8/Н10/Н6 в канон** — валидировано в прошлом чате, ещё не
   зафиксировано: retention результатов (Н8 — не чистим до потребителя
   истории + якорь пересмотра); ключевание `MarketPhase` контейнером
   (Н10 — осознанное исключение из шаринга-по-идентичности); точка отсчёта
   свежести (Н6 — структура от `windowEndAt`, фаза от `candleTimestamp`,
   `confirmedAt` — гейт без look-ahead, не точка отсчёта; `expiredAt` —
   производная на чтение `referencePoint + askingSetting.expirationDuration`,
   не хранимая колонка). Вход — snapshot v38 §«Состояние находок» +
   `phase-1-step-3-gaps-close-4-design.md` §2.
2. **Рассинхрон Java step-2** — `MarketPhaseParams.java`,
   `MarketPhaseParamsApiModel.java`, `StrategyMarketPhaseSetting.java`,
   валидатор, `trend-following-ema.json` реализуют старую скоринговую
   модель. Форвард-долг к `CODE` шага 3 / ресинку под условную модель.
3. **ТР1/ТР2** — торговый грунт `trading-specialist`: ТР1 — крипто-объём
   (добор из v37 §ТР1); ТР2 — диагностика оконного / OBV относительного
   контракта + перепрогон.
4. **Перевод трекинг-статуса fork A** в роадмапе/трекинге Н3 — CC не
   трогал (не реконструирует структуру трекинга наугад); централизовать
   при закрытии остатка `GAPS_CLOSE_4`.

## Остаток `GAPS_CLOSE_4` (не закрыт)

- **Н3 структура (части 1.1/1.3 `phase-1-step-3-gaps-close-4-design.md`)**
  — выживает под условную модель (`MarketStructure` — вычисляемый
  результат-операнд правил фазы), но в канон **не зафиксирована**:
  `MarketStructureAnalyzer` — design-stage, гейт `CODE`, компонент-дока
  ещё нет. Развилка A в §1.4 помечена «перепрогнано и зафиксировано».
- **Н6** — отдельная закрываемость (см. хвост 1).
- Перевод роадмап-статуса шага 3 — хвост 4.

## Активные принципы (для подхвата)

- **Каталог индикаторов расширяем через стратегию** (по потребности, не
  превентивно) — подпёр fork A → вариант 2.
- **Приемлемость остаточного риска** (whipsaw/перескок/просадка) — хвост
  пользователя, не инженерный дефолт (`trading-specialist` §Риск-рефлекс;
  `solution-designer` эвристика 2).
- **Критерий sugar-vs-алиас грамматики**
  (`docs/rules/condition-ruletype-granularity.md`); отложенное **точечное**
  применение к `VOLUME_FILTER_PASSED`, `MARKET_PHASE_IS`/
  `MARKET_STRUCTURE_IS` — при фиксации их контрактов, не пакетом.

## Режим работы

**Содержательное закрытие** (не отладка пайплайна). Развилки со штатным
владельцем — через CC по ступеням автономии
(`.claude/processes/question-delegation.md`). Расхождения bucket-2 —
диагностика дефицита входа + перепрогон владельцем, не ручной вывод в чате
(`CLAUDE.md` §«разбор результатов сверки по трём исходам»).

## Активные задачи

- `phase-1-step-3-gaps-close-4-design.md` (staged) — проработка Н3 +
  хвост Н6; **живой остаток** — части 1.1/1.3 (структура) + §2 (Н6);
  развилка A в §1.4 закрыта (баннер).
- `phase-1-step-3-fork-a-rerun-er-location.md`,
  `phase-1-step-3-fork-a-bis-rerun-efficiency-ruletype.md` (staged) —
  проработки fork A / A-bis, **закрыты** (баннеры «зафиксировано в
  канон»).
- `phase-1-step-3-market-phase-conditional-redesign.md` (staged) —
  редизайн фазы, **закрыт**.
- `phase-1-step-3-docs-check-5.md` — gap-отчёт 5-го прогона (вход
  `GAPS_CLOSE_4`); `…-docs-check-1.md` … `-4.md` — сравнительная база.

## Текущий фронтир / следующее действие

Тема редизайна фазы **закрыта**. Следующее — по списку «Хвосты» (выбор
приоритета за пользователем): сейв Н8/Н10/Н6 в канон → ресинк Java
step-2 → ТР1/ТР2 → перевод трекинг-статуса. Остаток `GAPS_CLOSE_4`
(Н3-структура к `CODE`, Н6) — отдельной закрываемостью.

Коммит — за пользователем (всё staged, CC не коммитит).

## Синхрон / что в работе / PK

- **Project Knowledge:** последний снапшот теперь **`snapshot-v39`**
  (заменяет v38 в префлайте — обновить PK после коммита). `CLAUDE.md` /
  `structure.md` / `naming.md` / `place-knowledge.md` в этой сессии не
  менялись.
- **Custom Instructions (`chat-project-instructions.md`):** в этой сессии
  не правился (источник правды — поле в Settings, не PK).
- **Staged к коммиту (эта сессия):** decisions
  `efficiency-ratio-as-catalog-indicator.md` (new),
  rule `condition-ruletype-granularity.md` (new); правки
  `IndicatorValue.md`, `Strategy.md`, `strategy-condition-authoring-contract.md`,
  `market-phase-conditional-classification.md`; проработки
  `phase-1-step-3-fork-a-rerun-er-location.md` (new),
  `phase-1-step-3-fork-a-bis-rerun-efficiency-ruletype.md` (new),
  баннер в `phase-1-step-3-gaps-close-4-design.md`; агенты
  `solution-designer.md`, `trading-specialist.md`; этот снапшот.
  **Плюс** не закоммиченный канон редизайна из прошлого чата
  (`MarketPhaseClassifier.md`, `MarketPhaseJob.md`, `MarketPhase.md`,
  `MarketStructure.md`, `strategy-tree-persistence.md`,
  `phase-1-step-3-market-phase-conditional-redesign.md` и др.).
- Коммит — за пользователем.
