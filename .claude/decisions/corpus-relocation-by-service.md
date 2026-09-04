# Переезд корпуса в монорепозиторий по оси «какой сервис»

## На какой вопрос отвечает этот файл

Почему корпус переезжает в монорепозиторий именно так: что мигрирует, в
каком порядке и по какой оси размечается.

## Решение держателя (2026-09-04)

Новый монорепозиторий с сервисами; текущий репозиторий — донор кода и
знания; корпус (`.claude/`, `docs/`, решения) переезжает **целиком**;
переезд — отдельный шаг роадмапа (шаг 1 фазы 2,
`.claude/work/roadmap/phase-2.md`). Ниже — как именно; это вход того
шага, а не его исполнение.

## Ось «какой сервис» — вторая ось размещения

Первая ось — тип знания по вопросу (`.claude/rules/structure.md`); она
не меняется. Вторая — **владелец-сервис**: у каждого продуктового
носителя ровно один сервис-владелец либо признак «сквозной».

- **Сквозное** остаётся в корневом `docs/`: концепция, архитектура,
  словарь, правила, которые читают два и больше сервисов (время,
  пустота и писатель, представление в БД, идемпотентность, ошибки,
  аудит не источник рантайма), общие спеки.
- **Сервисное** живёт у сервиса: `services/<сервис>/docs/<тип>/` с теми же
  подкаталогами типов, что сегодня (`models/`, `lifecycles/`,
  `components/`, `processes/`, `rules/`, `spec/`, `integrations/`).
- **Общие артефакты** (`domain-model`, `strategy-engine`) — свои `docs/`
  в каталоге артефакта: доменные сущности и события у `domain-model`,
  детекторы и контракт условий у `strategy-engine`.
- Пайплайн `.claude/` — корневой, один на монорепозиторий; в файлах
  переписываются пути.

**Альтернативы.** (а) Всё в корневом `docs/` с полем «сервис» в шапке —
отвергнуто: поле стареет первым, а раскладка каталогом проверяется
инструментом и читается глазами. (б) Один `docs/` на сервис без
сквозного — отвергнуто: концепция и сквозные правила получили бы N
копий, против П4.

## Что куда — карта кластеров

Гранулярность — кластер; поимённая раскладка делается на шаге переезда по
этой карте. Где кластер делится, названы обе стороны.

| Носитель сегодня | Владелец |
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
| `docs/models/api/` | `bff` (сырой вызов источника — инструмент держателя, закрыт принципалом; переезжает к коннектору как отладочная поверхность) |
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
| `.claude/` целиком, `tools/`, `README.md`, `CLAUDE.md` | корень монорепозитория |
| `.claude/tests/source-api/okx/` | остаётся в `.claude/tests/` — контур проверки источника принадлежит пайплайну, предмет — коннектор |
| `src/` | **не переезжает**: код портируется по сервисам в их шагах; донор остаётся доступным только для чтения |
| `.claude-archive/` | не переезжает; остаётся в доноре |

## Порядок переезда (внутри шага 1 фазы 2)

1. **Скелет монорепозитория**: каталоги общих артефактов, сервисов,
   фронта, манифестов; корневые `CLAUDE.md`, `README.md`, `tools/`.
2. **Пайплайн `.claude/` целиком** с переписыванием путей; прогон всех
   инструментов корпуса на новом месте — код 0 либо названный долг.
3. **Сквозное `docs/`** и `docs/architecture/`.
4. **Сервисное и артефактное** по карте выше — все носители одним ходом;
   док, описывающий ещё не портированный код, остаётся истиной о том,
   что портировать.
5. **Правка `.claude/rules/structure.md`** под вторую ось: пути таблицы
   становятся шаблонами `services/<сервис>/docs/<тип>/`; правка
   `knowledge-tree.md`, дайджеста типов, инструментов, зависящих от путей
   (`anchor-check`, `spec-*`).
6. **Донор замораживается**: ветка только для чтения, в `README.md`
   донора — указатель на монорепозиторий.

Правка `structure.md` **не делается сейчас**: она описывала бы репозиторий,
которого нет, и ломала бы проверки в нынешнем.

## Что переезд не делает

- Не портирует код: порт — по сервисам, в шагах фазы 2.
- Не переписывает доки под сервисную форму: расхождение «док описывает
  монолит, целевая форма — сервис» закрывается `DOCS_CHECK` того шага, где
  сервис строится, а не переездом.
- Не переносит схему БД: у каждого сервиса своя цепочка миграций с `V1`;
  миграции донора — история.

## Связи

- Дизайн-проход — `.claude/decisions/service-architecture-design-pass.md`.
- Роадмап — `.claude/decisions/roadmap-rebuild-for-service-platform.md`.
- Размещение — `.claude/rules/structure.md`.
