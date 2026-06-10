# Шаг 3 фазы 1 — производные рыночные данные (DONE)

## На какой вопрос отвечает этот файл

Что сделано на шаге 3 фазы 1 (индикаторы / структура / фаза рынка) — краткое
summary завершённого шага.

## Итог

Шаг 3 — **`DONE`** (2026-06-10). Реализованы производные рыночные данные:
индикаторы (`IndicatorValue` + 8 калькуляторов), структура рынка
(`MarketStructure` / `MarketStructureResolver`), фаза рынка (`MarketPhase`) —
jobs, модели, сервисы, расчёт/чтение/сохранение значений, запрошенных
стратегией. Концепт доведён docs-first до чистого `DOCS_CHECK_7`, код написан
и синхронизирован (`SYNC_DOCS_FROM_CODE`), затем — отдельный трек **ревизия D**
поверх закрытого CODE.

## Путь шага (итерации)

- `DOCS_CHECK_1..7` / `GAPS_CLOSE_*` — доведение концепции производных данных:
  условная фаза (скоринг распущен → авторские `phaseRules`), fork A (ER в
  каталог индикаторов), Н3/Н6/Н8/Н10/Н11/Н12, семантика объёмных условий
  (ТР2 + ТР1 книжная). Чистый `DOCS_CHECK_7` → гейт `CODE`.
- `CODE` + `SYNC_DOCS_FROM_CODE` — инкременты D1-D3 + fork-A; рациональ-батч
  `docs/decisions/derived-market-data-code-increments.md`; всплыл STRUCT-Q2.
- **Ревизия D** (отдельный трек, snapshot v42/v43): owner-ключевание
  результатов (реестры/шаринг убраны), настройки рыночных данных → собственные
  strategy-scope-строки, фаза `MarketPhase` stateless (не персистится,
  вычисляется на лету), `MarketPhaseClassifier` → `MarketPhaseResolver`.
  Доведена docs-first + реализована в коде (миграции `V4`/`V5`, компилируется).
- Пост-D `DOCS_CHECK` обоих фокусов (`concept-review` + `trading-review`) —
  **чисто**; пост-хок концепт-гейт §6a (D1) и торговый гейт пройдены → `DONE`.

## Открытый хвост (non-gating)

- **STRUCT-Q1** — калибровка числовых порогов структуры (фаза 2).
- **STRUCT-Q2** — закрыт реверсом ключевания (ревизия D).
- **PHASE-Q1** — липкость/гистерезис фазы при stateless-резолве (`trading-review`).
- **PHASE-Q2** — размещение `MarketPhase` как вычисляемого значения (классификация).
- **IND-Q1** (крипто-часть) — крипто-надёжность спот-объёма (фаза 4).

## Архив

Детальные прогресс-артефакты шага (итерации `DOCS_CHECK`/`GAPS_CLOSE`,
перепрогоны fork-A, редизайн условной фазы, sync-доки, торговые находки
ТР1/ТР2, пост-D `DOCS_CHECK`) — в подпапке
`.claude/work/history/2026-06-10-phase-1-step-3-derived-market-data/`.

Решения шага живут в `docs/decisions/` (market-data-result-identity-keying,
market-phase-stateless, market-phase-conditional-classification,
derived-market-data-code-increments, efficiency-ratio-as-catalog-indicator).
