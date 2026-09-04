# Владелец-сервис как вторая ось размещения знания

## На какой вопрос отвечает этот файл

Какое у нас правило второй оси размещения — какой сервис владеет
носителем знания.

## Правило

**У каждого продуктового носителя ровно один владелец: либо названный
сервис, либо признак «сквозной».** Первая ось — тип знания по вопросу
(`.claude/rules/structure.md`) — не меняется и определяет **каталог**;
вторая ось определяет **владельца** и каталогом не выражается: `docs/` и
`.claude/` лежат одним корпусом на корне монорепозитория
(`.claude/decisions/monorepo-restructuring-in-place.md`).

- **Сквозной** носитель читают два и больше сервиса; его правка задевает
  всех, и владельца-сервиса у него нет.
- **Сервисный** носитель читает и правит один сервис; его `DOCS_CHECK`
  идёт на шаге, который этот сервис строит.
- **Делимый** носитель называет обе стороны явно (строка карты содержит
  «делится: … — …»). Деление — не отсутствие владельца, а два владельца у
  двух разных предметов внутри одного файла.

**Для чего ось нужна.** Шаг, строящий сервис, обязан знать, какие доки
его: они — вход его `DOCS_CHECK` и его порта
(`.claude/work/roadmap/phase-2.md`). Без оси этот вход выбирается на
глаз, и расхождение «док описывает монолит» остаётся незамеченным ровно у
тех доков, которые никто не отнёс к строящемуся сервису.

**Ось — разметка, не раскладка.** Она не проверяется прогоном и потому
стареет молча; цена принята сознательно, якорь погашения —
`.claude/work/backlog.md` §«Энфорсер оси владельца-сервиса». До энфорсера
дисциплина держится на переносящем: **завёл или переименовал носитель —
строка карты тем же ходом**, как и `.claude/knowledge-tree.md`
(`.claude/rules/curation.md`).

**Карта — гранулярностью кластера.** Носитель, не попавший в карту
поимённо, наследует владельца своего кластера; носитель, чей владелец
кластером не определяется, вносится в карту строкой.

## Карта владельцев

| Носитель | Владелец |
|---|---|
| `docs/concept.md`, `docs/architecture/`, `docs/dictionary/` | сквозное |
| `docs/rules/`: `time-utc`, `absent-value-semantics`, `writer-named-for-every-value`, `persistence-representation`, `idempotency-via-unique`, `error-handling-policy`, `audit-not-runtime-source`, `ack-not-runtime-truth`, `raw-exchange-dto-boundary` | сквозное |
| `docs/rules/`: `risk-policy`, `live-risk-protection`, `loss-streak-halt`, `manual-halt`, `exchange-hold`, `instrument-hold`, `command-lifecycle`, `execution-hierarchy`, `exit-teardown-order`, `no-partial-close`, `replace-not-amend`, `pnl-reconciliation`, `deal-without-operations`, `risk-validator-scope`, `trading-constraints`, `runtime-error-classification`, `market-data-freshness` (как операнд гейта) | `trading-core` |
| `docs/rules/`: `strategy-validation`, `trading-configuration-ownership` | `strategies` |
| `docs/rules/`: `strategy-condition-contract`, `strategy-step-once-per-episode`, `condition-ruletype-granularity` | `strategy-engine` |
| `docs/rules/`: `market-data-retention` (и `market-data-freshness` как вычисление) | `market-data` |
| `docs/rules/`: `api-access-policy` | `auth` + `bff` (входящий доступ); исходящий отказ — у коннектора |
| `docs/rules/`: `controlled-exchange-exceptions`, `external-status-resolution` | делится: словарь статусов площадки — коннектор; доменный резолв — `trading-core` |
| `docs/models/domain/core/`: `Order`, `Position`, `AlgoOrder`, `BalanceContainer` | формы — `domain-model`; зеркало и писатели — `trading-core` |
| `docs/models/domain/core/Exchange.md` | делится: площадка и счёт-реестр — `auth`; торговое состояние счёта — `trading-core` (`docs/architecture/tenant-and-exchange.md`) |
| `docs/models/domain/core/Instrument.md`, `other/InstrumentExternalRules.md`, `other/TradeFeeRate.md` | `market-data`; проекция у `trading-core` |
| `docs/models/domain/aggregate/`: `Deal`, `DealTranche`; `other/`: `DealActionState`, `DealCashFlow`, `AnomalyReport` | `trading-core` |
| `docs/models/domain/aggregate/Strategy.md` | определение — `strategies`; исполняемая форма — `strategy-engine` |
| `docs/models/domain/other/`: `Candle`, `CandleGroup`, `IndicatorValue`, `MarketStructure`, `MarketPhase` | `market-data` |
| `docs/models/domain/other/AccessDenial.md` | `auth` + `bff` |
| `docs/models/domain/other/Auditable.md` | `domain-model` |
| `docs/models/integrations/okx/`, `docs/models/mapping/`, `docs/integrations/okx/` | `connector-okx` |
| `docs/models/api/` | `bff` (сырой вызов источника — инструмент держателя, закрыт принципалом; переходит к коннектору как отладочная поверхность) |
| `docs/lifecycles/`: `Deal`, `DealTranche`, `DealActionState`, `Order`, `Position`, `AlgoOrder`, `AnomalyReport` | `trading-core` |
| `docs/lifecycles/Strategy.md` | `strategies` |
| `docs/lifecycles/`: `Instrument`, `CandleGroup` | `market-data` |
| `docs/components/`: джобы и сервисы свечей, индикаторов, структуры, фазы, правил инструмента, `MarketDataExpirationChecker`, `MarketPriceDataService` | `market-data` |
| `docs/components/`: `IntegrationService`, `*ExternalStatusResolver`, `AttachedAlgoOrderStateResolver` | `connector-okx` |
| `docs/components/`: `StrategyConditionEvaluator`, `StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator`, `CalculationContextFactory` и их runtime-модели | `strategy-engine` |
| `docs/components/`: всё остальное (оркестратор, обработчики, исполнители, риск, холды, kill-switch, аномалии, сканер входа, ошибки, ретраи) и их runtime-модели | `trading-core` |
| `docs/processes/`: `candle-loading`, `market-data-calculation` | `market-data` |
| `docs/processes/`: `deal-management`, `fsm-execution-layering`, `risk-evaluation` | `trading-core` |
| `docs/processes/strategy-action-calculation.md` | `strategy-engine` |
| `docs/spec/` | по владельцу величины: сайзинг, риск, сделки, транши, холды — `trading-core`; свежесть, фаза — `market-data`; стратегия — `strategies` / `strategy-engine`; статусы площадки — коннектор |
| `.claude/` целиком, `tools/`, `README.md`, `CLAUDE.md` | пайплайн монорепозитория — вне оси |
| `.claude/tests/source-api/okx/` | пайплайн; **предмет** — `connector-okx` (контур проверки источника принадлежит проверочной деятельности, не сервису) |

## Чего правило НЕ означает

- **Не разрешение копировать носитель по сервисам.** Владелец у истины
  один; сервис, которому чужая истина нужна, ссылается
  (`.claude/rules/policy-home.md`, `.claude/rules/carrier-levels.md`).
- **Не переезд файлов.** Ось владельца ничего не перемещает; попытка
  выразить её каталогами снята вместе с решением о новом репозитории.
- **Не деление кода.** Карта размечает знание. Что из донора уходит в
  какой сервис, решает шаг порта, и совпадение с этой картой ожидаемо, но
  не обязано быть буквальным.

## Связи

- Первая ось — `.claude/rules/structure.md`.
- Почему ось введена и почему разметкой — `.claude/decisions/monorepo-restructuring-in-place.md`.
- Инвентарь сервисов — `docs/architecture/services.md`.
- Единственность дома — `.claude/rules/policy-home.md`.
- Курация при изменении состава файлов — `.claude/rules/curation.md`.
