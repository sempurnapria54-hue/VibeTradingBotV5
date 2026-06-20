# Шаг 5 фазы 1 — Риск-преконтроль — DONE (2026-06-20)

## На какой вопрос отвечает этот файл

Что сделано на шаге 5 фазы 1 (риск-преконтроль) и где детальные артефакты.

## Итог

Материализован risk-layer (валидация рассчитанного действия до отправки
команды) + достроен калькуляторный слой. Шаг прошёл весь docs-first процесс и
закрыт в `DONE`; все гейты §7 — с зафиксированным исходом.

## Под-шаги

- `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2` (концепт+торговый фокусы,
  чисто): закрыты RISK-Q1/Q2, INSTR-Q1, материализация
  `InstrumentExternalRules`, база сайзинга `externalAvailableEquity`, отказ от
  нашего кэпа плеча (вариант (а)).
- `CODE`: написан код (47 файлов), прогнаны независимые фокусы
  `conventions`/`performance`/`disaster` (без блокеров), находки закрыты.
- `SYNC_DOCS_FROM_CODE`: `divergence` (независимый) → ~44 расхождения по 16
  докам → реконсилировано (docs←code); ренейм
  `InstrumentExternalRulesService.md` → `InstrumentExternalRulesDataService.md`.
- `DOCS_CHECK_3` (§6a пост-хок концепт-гейт) → `GAPS_CLOSE_3` → `DOCS_CHECK_4`
  (чисто): C1 — выравнивание механизма controlled-ошибки (`CalculationException`
  бросается суб-калькуляторами, ловится оркестратором → `CalculationError`);
  C2 — комиссии отнесены к шагу 7 (решение пользователя).

## Что построено (код)

- **`InstrumentExternalRules`** — доменная модель + JSONB-навес на `instruments`
  (миграция `V8`), маппер + JSON-конвертер, `InstrumentExternalRulesDataService`,
  `InstrumentExternalRulesSyncJob` + фасад + конфиг; домаппинг OKX-полей.
- **Расчётный слой** — `MarketPriceData`-сборка (REST ticker); полные
  `Calculated*`-RVO + enum'ы; `CalculationContext(Factory)`, `PriceCalculator`,
  `SizeCalculator` (risk-bounded сайзинг), `StrategyActionCalculator`.
- **Risk-слой** — `RiskValidator`, `RiskBlockResolver`, RVO
  `RiskValidationResult`/`RiskCheckResult`/`RiskBlockAction`.

## Ключевые решения

- Риск на сделку — единственный уровень риска фазы 1; база —
  `externalAvailableEquity`; контроль через лимит на размер + биржевой потолок
  плеча; нашего кэпа плеча/экспозиции нет (`docs/decisions/per-trade-risk-policy.md`).
- `InstrumentExternalRules` — JSONB-навес (`instrument-external-rules-materialization.md`).
- Внешний контракт калькулятор-слоя возвратный (`StrategyActionCalculationResult`);
  внутри — `CalculationException` (throw/catch).

## Открытые хвосты (non-gating форвард)

- **Комиссии** в риск-расчёте → шаг 7 (вместе с fee-моделью / `trade-fee`;
  decision держит концептуальным входом, код-учёт отложен).
- **Бесстоповый risk-creating вход** не размещаем (аномалия) → шаг 6
  (`backlog.md` §Шаг 6).
- Остаток **INSTR-Q2** (тайминг set-leverage) → шаг 6; **STRAT-Q4** (якорь
  allocation %); провизорный численный лимит риска (бэктест/пользователь).

## Детальные артефакты

Подпапка `2026-06-20-phase-1-step-5-risk-precontrol/`:
`phase-1-step-5-docs-check-1.md`, `-docs-check-2.md`, `-code.md`,
`-sync-docs-from-code.md`, `-docs-check-3.md` (вкл. §6a / `GAPS_CLOSE_3` /
`DOCS_CHECK_4`).
