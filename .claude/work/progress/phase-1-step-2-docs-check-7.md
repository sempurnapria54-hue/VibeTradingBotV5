# DOCS_CHECK_7 — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в проверке целостности концепции доков под код
шага 2 (седьмая итерация — верификация правки «плечо динамическое»)
и какие пробелы найдены.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия (абстракция: объявляет
  нужные индикаторы и условие сигнала; одна реализация)».
- Под-шаг: `DOCS_CHECK_7` (седьмая итерация), после правки плеча по
  решению чата (2026-06-04): снят `StrategyDetail.maxLeverage`,
  правило плеча в `trading-constraints.md` переписано («плечо
  динамическое, единственный предел — биржевой максимум»),
  `LEVERAGE_EXCEEDED` убран из `RiskCheckCode`, биржевой
  `EXCHANGE_MAX_LEVERAGE_EXCEEDED` сохранён, адаптерные сверки
  перенацелены на `externalMaxLeverage`.
- Тулинг: роль `reviewer`, фокус `concept-review`. Граница охвата —
  **только доки**, код не читается.
- **Фокус итерации:** (1) правка легла согласованно, новых doc↔doc
  хвостов нет, остаточных ссылок на снятый потолок не осталось;
  (2) адъюдикация напряжения `Instrument.leverage` («задаётся при
  создании») ↔ «плечо динамическое на сделку»: новый пробел или
  покрыто INSTR-Q2. Формула вывода плеча **не достраивается**
  (риск-движок, шаг 5).

## Охват

### Проверено (в охвате)

- **Семь правленых файлов:** `docs/rules/trading-constraints.md`
  (правило + enforcement), `docs/models/domain/aggregate/Strategy.md`
  (§StrategyDetail), `docs/components/models/RiskCheckResult.md`
  (§RiskCheckCode), `docs/components/AnomalyJob.md` (anomaly-кейсы),
  `docs/models/mapping/Position.md` (invariant checks ядра + OKX
  validation), `docs/models/integrations/okx/OkxPositionResponse.md`,
  `docs/models/integrations/okx/OkxOrderResponse.md`.
- **Смежные по плечу:** `docs/lifecycles/Position.md` (`lever >
  allowed`), `docs/components/RiskValidator.md` (метрики),
  `docs/components/models/CalculationContext.md`,
  `docs/components/InstrumentExternalRulesService.md`,
  `docs/components/InstrumentExternalRulesSyncJob.md`,
  `docs/components/EntryFinalizedHandler.md` («strategy/risk policy
  по защите»), `docs/components/PriceCalculator.md` /
  `docs/components/models/CalculatedPrice.md` (liquidation guard).
- **Instrument-кластер (для адъюдикации):**
  `docs/models/domain/core/Instrument.md`,
  `docs/models/mapping/Instrument.md`,
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/models/mapping/InstrumentExternalRules.md`,
  `docs/models/integrations/okx/OkxInstrumentResponse.md`.
- **Cross-ref-свипы по всем `docs/`:** `плечо`/`leverage`/`lever`
  (полный обход вхождений); `maxLeverage`/`max_leverage`/
  `LEVERAGE_EXCEEDED` вне `EXCHANGE_*`; «разрешённого стратегией»,
  «глобальной risk policy», «глобального лимита», «expected max
  leverage»; «ликвидаци» (опора нового текста правила).
- **Open-questions:** INSTR-Q2 целиком; остальные — на предмет
  влияния правки.

### Вне охвата

- Формула вывода рабочего плеча (риск 1% + «ликвидация за стопом» +
  волатильность) — логика риск-движка, шаг 5; по заданию итерации не
  достраивается.
- Код (`src/`) — вне охвата `concept-review`.

## Стадия остановки

**Прошёл все стадии (до стадии 2).** Гейтящих вопросов правка не
внесла; scope шага 2 не изменился.

## Верификация правки плеча — применено согласованно

- **`trading-constraints.md`.** Правило «Плечо» — динамическое,
  выводится из торговых правил и рыночных условий, наших потолков
  нет, единственный предел — биржевой `externalMaxLeverage` из
  `InstrumentExternalRules`. Пункты «не выше лимита стратегии /
  глобальной risk policy» сняты. Enforcement-блок — без
  `LEVERAGE_EXCEEDED`, `EXCHANGE_MAX_LEVERAGE_EXCEEDED` сохранён.
  Ссылка INSTR-Q2 на это правило («лимит плеча сослан на
  `externalMaxLeverage`») остаётся точной.
- **`Strategy.md`.** Строки `maxLeverage` в §StrategyDetail нет;
  `riskPerTradePercent` / `targetRiskRewardRatio` на месте.
  Колонки `max_leverage` в персистентных перечнях доков нет нигде.
- **`RiskCheckResult.md`.** `LEVERAGE_EXCEEDED` убран из
  стартового набора, `EXCHANGE_MAX_LEVERAGE_EXCEEDED` сохранён.
  Других перечислений `RiskCheckCode` с этим кодом в `docs/` нет.
- **`AnomalyJob.md`.** Кейс перенацелен: «плечо выше биржевого
  максимума (`externalMaxLeverage`)» — согласован с адаптерными
  сверками (`mapping/Position.md` invariant checks, OKX validation;
  `OkxPositionResponse.md`).
- **Адаптерные сверки.** `lever ≤ биржевой максимум
  (externalMaxLeverage)` идентично в `mapping/Position.md` (ядро и
  OKX-блок), `OkxPositionResponse.md`, `OkxOrderResponse.md`.
  Сорсинг rules-полей в шаге 1 (транзиентный снапшот, модель rules
  не материализована) — известный контур INSTR-Q1 / backlog п.9,
  правкой не ухудшен (наоборот, источник потолка теперь назван
  точно).
- **Не хвосты:** `lifecycles/Position.md:88` «`lever > allowed`» —
  родовое слово, определение сверки даёт `mapping/Position.md`
  (биржевой максимум) — согласовано;
  `EntryFinalizedHandler.md:39` «требования strategy/risk policy по
  защите» — про защиту позиции, не про плечо (термин risk policy
  решением не снят, снят только потолок плеча).
- **Опора формулировки «ликвидация за стопом»:** инвариант в доках
  представлен (`STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION` в
  `RiskCheckResult.md`, «liquidation guard distance» в
  `RiskValidator.md`, `LIQUIDATION_GUARD_PRICE` в
  `PriceCalculator.md`/`CalculatedPrice.md`) — name-level
  достаточно, формула — шаг 5.
- **Свипы на остаточные ссылки на снятый потолок — чисто:** ноль
  вхождений в `docs/`.

Новых doc↔doc хвостов правка не оставила.

## Адъюдикация: `Instrument.leverage` ↔ «плечо динамическое»

**Вердикт: покрыто INSTR-Q2 лишь частично; новый вопрос не нужен —
нужен апдейт формулировки INSTR-Q2 на `GAPS_CLOSE_7`.**

- **(а) Премисса INSTR-Q2 устарела.** Текст вопроса
  (`open-questions.md`): «рабочее `leverage` … не должно превышать
  **конфиговый максимум плеча**», аспект «как соотносится с
  конфиговым максимумом». Конфиговый максимум — наш потолок;
  решением «наших потолков нет» он снят. Это остаточная ссылка на
  снятый потолок — вне `docs/` (свип правки покрывал только
  `docs/`), но в зоне сверки `concept-review` с `open-questions.md`.
- **(б) Напряжение статика↔динамика не покрыто.** INSTR-Q2
  *предполагает* статическое рабочее плечо («задаётся при
  создании», `Instrument.md:35,74`; `mapping/Instrument.md:38,67`)
  и спрашивает про его валидацию, потолки и `HOLD`. Вопрос «нужна
  ли вообще статическому полю роль рабочего плеча, если рабочее
  плечо выводится динамически на сделку; кто и когда выставляет
  плечо на бирже» — в формулировке отсутствует.
- **Доки Instrument до решения не правятся:** поле — рабочее плечо,
  не потолок; прямого противоречия с правкой нет, напряжение
  концептуальное. Судьба поля — содержательное решение, не предмет
  ревью.
- **Гейтинг: шаг 2 не блокирует.** После правки Strategy плечо не
  трогает; `Instrument` — шаг 1, где валидация плеча и была явно
  отложена («не требует и не блокируется»); формула — шаг 5.

## Пробелы по типам

### 1. Несогласованности между доками

Одна, **вне фокуса итерации** (найдена свипом «ликвидаци»),
downstream:

- **Нейминг runtime-полей `Position`.** `mapping/Position.md:31-35`
  маппит снапшот в `Position.averageEntryPrice` / `markPrice` /
  `liquidationPrice` / `margin` / `unrealizedProfit` (без
  `external`-префикса), а `domain/core/Position.md:36-40` именует
  эти же пять полей `externalAverageEntryPrice` /
  `externalMarkPrice` / `externalLiquidationPrice` /
  `externalMargin` / `externalUnrealizedProfit`. В самой
  mapping-таблице рядом `externalSize → Position.externalSize` (с
  префиксом) — непоследовательно и внутри файла. `Position` —
  материал шагов 4/7; шаг 2 не гейтит.

### 2. Name-level без структуры (где структура нужна шагу 2)

Не найдено. Формула вывода плеча — осознанно отложенный порог
(шаг 5), в правиле зафиксировано «выводится из …» без формулы — это
и был замысел правки.

### 3. Неотвеченные вопросы (open-questions)

- **INSTR-Q2 — требует апдейта формулировки** (см. адъюдикацию):
  снять «конфиговый максимум» (потолок снят решением), добавить
  аспект «роль/судьба статического `Instrument.leverage` при
  динамическом плече на сделку». Шаг 2 не блокирует.
- Остальные открытые вопросы правкой не задеты; блокирующих шаг 2 —
  нет (без изменений к `DOCS_CHECK_6`).

## Блокирующие открытые вопросы

Нет.

## Эскалации

Нет. Адъюдикация была заданием итерации — выполнена с вердиктом
(апдейт INSTR-Q2, не новый вопрос); подтверждение вердикта и тексты
— чат / `GAPS_CLOSE_7`.

## Сводка

- **Верификация правки плеча:** легла согласованно по всем 7 файлам;
  остаточных ссылок на снятый потолок в `docs/` — 0; новых doc↔doc
  хвостов — 0.
- **Несогласованности (doc↔doc):** 1 — нейминг пяти runtime-полей
  `Position` (`mapping/Position.md` ↔ `domain/core/Position.md`),
  вне фокуса итерации, downstream (шаги 4/7), шаг 2 не гейтит.
- **Name-level без структуры:** 0.
- **Неотвеченные вопросы:** INSTR-Q2 — устаревшая премисса
  («конфиговый максимум») + непокрытый аспект (статика↔динамика);
  блокирующих шаг 2 — 0.
- **Эскалаций:** 0.
- **Итог:** правка верифицирована, **шаг 2 остаётся готов к
  `CODE`**. Рекомендация — короткий `GAPS_CLOSE_7` на два пункта:
  (1) апдейт INSTR-Q2; (2) решение по Position-неймингу — закрыть
  сейчас (дёшево) или пометить к docs-check шага 4.
