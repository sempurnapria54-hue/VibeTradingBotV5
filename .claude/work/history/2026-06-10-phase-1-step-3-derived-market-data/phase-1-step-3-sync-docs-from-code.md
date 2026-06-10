# SYNC_DOCS_FROM_CODE — шаг 3 фазы 1

## На какой вопрос отвечает этот файл

Что разошлось между утверждённым кодом шага 3 и `docs/`, как
реконсилировано, и что осталось открытым после сверки код↔доки.

## Контекст

Под-шаг 6 (`SYNC_DOCS_FROM_CODE`) процесса
`.claude/processes/roadmap-step-execution.md`. Направление — docs←code:
код шага 3 утверждён (`CODE`, commit `ROADMAP 1-3-4 CODE`) и считается
истиной. Доки были доведены до чистого `DOCS_CHECK_7` **до** CODE; на
CODE по необходимости вошли четыре инкремента (D1-D3 + fork-A), которые
доки не отражали. Рациональ батча зафиксирован
`docs/decisions/derived-market-data-code-increments.md`.

Фокус `divergence` (`.claude/skills/divergence-review.md`) — список
расхождений; реконсиляция change/remove —
`.claude/skills/reconcile-knowledge.md`, добавления — штатным потоком
размещения.

## Расхождения (divergence) и реконсиляция

### ADD (в коде есть, в доках не было)

| # | Расхождение (код) | Реконсиляция (доки) |
|---|---|---|
| A1 | `IndicatorComponent` enum + `StrategyConditionOperand.indicatorComponent`; контракт required-multi / forbidden-single / совместимость (`util.IndicatorComponents`, валидатор) — **D1** | Strategy.md §StrategyConditionOperand + bullet; strategy-condition-authoring-contract.md §Операнд + контракт и §per-ruleType; IndicatorValue.md перекрёстная заметка; рациональ — новый decision §D1 |
| A2 | `MarketStructureParams.trendEfficiencyThreshold` — **D2** | Strategy.md §MarketStructureParams; MarketStructure.md §Семантика п.4; помечен провизорным (STRUCT-Q1) |
| A3 | `MarketStructureParams.levelToleranceAtrMultiplier` (`толеранс = k·ATR`, fallback доля цены) — **D3** | Strategy.md §MarketStructureParams; MarketStructure.md §Семантика п.2; провизорный (STRUCT-Q1) |
| A4 | `StrategyMarketStructureSetting.efficiencyRatioKey` / `atrKey` (soft-ссылки) — **fork-A** | Strategy.md §StrategyMarketStructureSetting; MarketStructureJob.md / Resolver.md |
| A5 | `MarketStructure.breakoutEvent` (поле модели) + `MarketBreakoutEvent` (`brokenLevelType`/`direction`/`levelPrice`/`confirmedAt` + `Direction`) | MarketStructure.md §Структура + новый под-раздел §MarketBreakoutEvent (форма зафиксирована на CODE) |

### CHANGE (в коде иначе, чем в доках)

| # | Доки (было) | Код (истина) | Реконсиляция |
|---|---|---|---|
| C1 | Resolver-контракт: вход `optional indicatorValues` (список `IndicatorValue`) | `resolve(window, efficiencyRatio: BigDecimal?, atr: BigDecimal?, params)` — готовые **скаляры**, извлекает job | Resolver.md §Контракт переписан на скалярную сигнатуру |
| C2 | Прокси резолвера = «наклон short-EMA / ATR из окна» | Прокси ER = нетто-ход / суммарный побарный ход (мини-ER по close); ATR-fallback = доля цены | Resolver.md, efficiency-ratio-as-catalog-indicator.md §3, MarketStructure.md §Семантика — прокси скорректирован |
| C3 | Тренд-критерий = «ER высок / **наклон EMA согласен**» | `ER ≥ trendEfficiencyThreshold` (EMA-наклон в коде нет) | MarketStructure.md п.4, Resolver.md §Границы — EMA-наклон убран |
| C4 | ATR — «пол свинг-шума» (фильтр приёмки свинга, п.1) | ATR используется в **толерансе кластеризации** (п.2), не в приёмке свинга | MarketStructure.md п.1/п.2 — ATR перенесён на кластеризацию |
| C5 | `(MarketStructureParams не затронуты)` (efficiency-ratio decision §3) | params получили `trendEfficiencyThreshold` / `levelToleranceAtrMultiplier` | efficiency-ratio decision §3 — оговорка снята, cross-ref на новый decision |
| C6 | «объявлено, но не готово» поведение не описано | Объявлен вход, но не готов/устарел → `UNKNOWN` (job, не proxy) | MarketStructureJob.md, Resolver.md, MarketStructure.md §Семантика |

### REMOVE (в коде нет, в доках осталось)

Удалений нет: код шага 3 только добавлял/уточнял относительно концепта
`DOCS_CHECK_7`; концептные сущности доков (типы, ruleType, источники)
сохранены. Прежнее «форма `breakoutEvent` — CODE» не удаление, а
доспецификация (A5).

## Что осталось открытым (краевой случай)

**STRUCT-Q2 — идентичность `config_id` vs ER/ATR-ключи.** Soft-ссылки
`efficiencyRatioKey` / `atrKey` живут на `StrategyMarketStructureSetting`,
не в `MarketStructureParams`, и в идентичность конфигурации структуры
(`timeframe` + canonical-`params`) **не входят**. Две настройки с
одинаковыми `timeframe + params`, но разными ER/ATR-ключами делят один
`config_id` и ряд результатов; `MarketStructureJob` дедупит расчёт по
`instrumentId:configId` — первый писатель выигрывает, второй читает
структуру по другому входу. Нарушает инвариант шаринга «одна конфигурация
→ идентичный результат».

По существу в этом прогоне **не закрыт** (краевой случай, sync не
блокирует). Заведён открытым вопросом STRUCT-Q2
(`.claude/work/questions/open-questions.md`); кандидаты разрешения и грунт
— `docs/decisions/derived-market-data-code-increments.md` §Что осталось
открытым.

## Провизорные пороги

Числовые пороги D2/D3 в доках помечены провизорными (value: бэктест-гейт
фазы 2, STRUCT-Q1), числом в канон не зашиты. В коде — провизорные дефолты
резолвера (ER-порог 0.30; k-толеранс 0.5; fallback-доля цены) как
fallback при `null`-полях. Канонические значения — после калибровки на
фазе 2.

## Затронутые доки (реконсиляция)

- **Новый:** `docs/decisions/derived-market-data-code-increments.md`.
- **Правлены:** `docs/models/domain/aggregate/Strategy.md`,
  `docs/models/domain/other/MarketStructure.md`,
  `docs/models/domain/other/IndicatorValue.md`,
  `docs/components/MarketStructureResolver.md`,
  `docs/components/MarketStructureJob.md`,
  `docs/decisions/strategy-condition-authoring-contract.md`,
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md`.
- **Вопросы:** `.claude/work/questions/open-questions.md` (+ STRUCT-Q2).

Референты проверены: ссылки на изменённые §-разделы не повисли; разделы
«На какой вопрос отвечает» затронутых доков остались валидны (вопрос файла
не сместился — добавлены поля/уточнения в рамках прежней темы).

## Гейт после sync

D1 — концепт/контракт-инкремент (новая семантика контракта операнда),
въехал на CODE → по §6a процесса требует **пост-хок концепт-гейта**
(`concept-review` по приведённым докам) **до** `DONE`. D2/D3/fork-A —
параметризация/проводка принятого концепта, своего гейта не требуют, но
попадают в тот же прогон. Пост-хок гейт в этом прогоне **не запускался** —
следующий под-шаг.

## Статус

Под-шаг `SYNC_DOCS_FROM_CODE` шага 3 завершён. Статус шага в
`phase-1.md` — `SYNC_DOCS_FROM_CODE`; пост-хок концепт-гейт (§6a) для D1
— pending перед `DONE`.
