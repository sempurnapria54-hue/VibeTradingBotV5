# MarketPhase

## На какой вопрос отвечает этот файл

Что это за `MarketPhase`: структура, енум `Type`, и почему она
**вычисляется на лету** из текущих индикаторов/структур, а не хранится.

## Назначение

`MarketPhase` — фаза рынка, **вычисляемая по запросу** из текущих
(последних доступных) `IndicatorValue` и `MarketStructure`. `Type`
определяется **авторскими условиями** (`StrategyMarketPhaseSetting.phaseRules`):
`docs/components/MarketPhaseService.md` зовёт
`docs/components/MarketPhaseResolver.md` — упорядоченный first-match
поверх `StrategyConditionEvaluator` (первая клауза с истинным `condition`
задаёт `Type`, ни одна → `UNKNOWN`; см.
`docs/models/domain/other/MarketPhase.md`).

`MarketPhase` **не персистируется**: своего персист-слоя, своих
часов и своего срока свежести у фазы нет — она производная от своих
входов. `EntryScannerJob` по `MarketPhase.Type` выбирает `StrategyDetail`
(`MarketPhase.Type → StrategyDetail.marketPhaseType`); раздачей актуальной
фазы занимается `MarketPhaseService` (вычисляет, не читает из БД).

## Структура

Лёгкое runtime-значение (не сущность): `instrumentId` и `type: Type`.
Технического `id`,
хранимого `candleTimestamp` и `confirmedAt` **нет** — они принадлежали
персист-слою, которого больше нет. Точный состав полей runtime-значения —
деталь реализации (`CODE`); `MarketPhase` переносится в `CalculationContext`
/ `DealContext` как вычисленное значение.

## Енум `Type`

`BULL_TREND`, `BEAR_TREND`, `RANGE`, `UNKNOWN`.

`UNKNOWN` — консервативный дефолт: ни одна клауза `phaseRules` не сработала
**или** нужный клаузе вход (индикатор/структура) устарел/отсутствует
(операнд недоступен). Отдельного `Status` нет.

## Вычисление и свежесть (на лету)

- **Вычисляется на чтение.** `MarketPhaseService` собирает текущие
  `IndicatorValue` / `MarketStructure` (по `key`-ссылкам операндов
  `phaseRules` на strategy-scope-настройки) и зовёт `MarketPhaseResolver`.
  Считается только по уже готовым результатам (которые сами посчитаны по
  закрытым свечам — без look-ahead).
- **Свежесть наследуется от входов.** Своего `expirationDuration` у фазы
  нет. Если вход, нужный сработавшей клаузе, устарел или отсутствует —
  операнд недоступен, фаза консервативно `UNKNOWN`. Свежесть входов
  проверяет `MarketDataExpirationChecker` по `expirationDuration`
  соответствующих `StrategyIndicatorSetting` / `StrategyMarketStructureSetting`
  (правило — `docs/rules/market-data-freshness.md`).
- **Не персистируется.** Строк/таблицы `MarketPhase`, `UNIQUE(...)`,
  истории и retention у фазы нет (в отличие от `IndicatorValue` /
  `MarketStructure`, которые хранят ряд и ключуются настройкой-владельцем).
  Если когда-нибудь понадобится хранить ряд фаз — отдельным решением
  (`docs/rules/market-data-retention.md` пересмотра).
- **Анти-whipsaw — операнд-уровневый.** Сглаживающие периоды индикаторов и
  структурный `breakoutConfirmationBars`; отдельной подтверждаемости/
  гистерезиса фазы нет — открытый торговый вопрос PHASE-Q1
  (`.claude/work/questions/open-questions.md`,
  `docs/models/domain/other/MarketPhase.md` торговый вопрос).
