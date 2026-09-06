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
| `docs/rules/`: `controlled-exchange-exceptions`, `external-status-resolution` | делится: резолв сырого статуса и класс отказа границы — коннектор; назначение исхода (причина закрытия, терминал ненайденности) и резолв позиции — `trading-core`. Критерий — `docs/rules/external-status-resolution.md` §«Где резолвится — сторона выбирается по словарю источника» |
| `docs/models/domain/core/`: `Order`, `Position`, `AlgoOrder`, `BalanceContainer` | формы — `domain-model`; зеркало и писатели — `trading-core` |
| `docs/models/domain/core/Exchange.md` | делится: площадка и счёт-реестр — `auth`; торговое состояние счёта — `trading-core` (`docs/architecture/tenant-and-exchange.md`) |
| `docs/models/domain/core/Instrument.md`, `other/InstrumentExternalRules.md` | `market-data`; проекция у `trading-core` |
| `docs/models/domain/other/TradeFeeRate.md` | `trading-core`: ставка — атрибут комиссионного уровня биржевого счёта и читается с его ключами, а чтения `market-data` публичные по контракту (`docs/models/domain/other/TradeFeeRate.md` §Персистентность). У `market-data` остаётся только **ключ** группы на навесе правил |
| `docs/models/domain/aggregate/`: `Deal`, `DealTranche`; `other/`: `DealActionState`, `DealCashFlow`, `AnomalyReport` | `trading-core` |
| `docs/models/domain/aggregate/Strategy.md` | делится: определение, его состав и валидация — `strategies`; копия в базе ядра, писатель её статуса и резолв идентичностей в числовые FK — `trading-core`; исполняемая форма — `strategy-engine`. Критерий — §«Где живёт определение и где — его копия» того же дока |
| `docs/models/domain/other/`: `Candle`, `CandleGroup`, `IndicatorValue`, `MarketStructure`, `MarketPhase`, `MarketOrderBook`, `MarketTicker` | `market-data` |
| `docs/models/domain/other/AccessDenial.md` | `auth`: строку заводит сервис, у которого отказ произошёл **и** есть своя база; у периметра базы нет, и там след — лог и метрика (`docs/rules/api-access-policy.md` §«След отказа пишет тот, у кого есть база») |
| `docs/models/domain/other/Auditable.md` | `domain-model` |
| `docs/models/domain/other/AuditRecord.md`, `docs/rules/statistics-aggregates.md`, `docs/spec/statistics-aggregates.json` | `audit-statistics` |
| `docs/rules/audit-not-runtime-source.md` | сквозное: правило **запрещает** управляющей логике читать историю, то есть адресовано её читателям — ядру и всякому, кто принимает runtime-решение, — а не владельцу журнала. У аудита оно не предмет, а следствие |
| `docs/components/` — файлов владельца `audit-statistics` пока нет, и **строку catch-all ниже он не наследует** | его исполнители (потребитель событий, джоба пересчёта проекции, поверхность чтения) получают компонент-доки на своём шаге; без этой строки они достались бы `trading-core` умолчанием — тот же класс, что закрыт у `bff` и у `strategies` |
| `docs/models/integrations/okx/`, `docs/models/mapping/`, `docs/integrations/okx/` | `connector-okx` |
| `docs/models/api/` | `bff`: формы, которые периметр **порождает** сам (событие потока, оболочка агрегата, собственный отказ), — его api-модель; пересылаемое идёт формой владельца, и второго её носителя не заводится. Признак и его довод — `docs/architecture/contracts.md` §«Периметр: что `bff` отдаёт и чего не делает». **Сырой вызов источника** (`OkxRawApiRequest`) предметом периметра не является и не станет им: он инструмент держателя, закрытый принципалом и профилем, и отходит коннектору **ходом удаления донора** — тем же, что снимает донорские остатки общей библиотеки |
| `docs/lifecycles/`: `Deal`, `DealTranche`, `DealActionState`, `Order`, `Position`, `AlgoOrder`, `AnomalyReport` | `trading-core` |
| `docs/lifecycles/Strategy.md` | `strategies` |
| `docs/lifecycles/`: `Instrument`, `CandleGroup` | `market-data` |
| `docs/components/`: джобы и сервисы свечей, индикаторов, структуры, фазы, правил инструмента, `MarketDataExpirationChecker`, `MarketPriceDataService` | `market-data` |
| `docs/components/`: `IntegrationService`, `OrderExternalStatusResolver`, `AlgoOrderExternalStatusResolver` | `connector-okx` |
| `docs/components/`: `StrategyConditionEvaluator`, `StrategyActionCalculator`, `PriceCalculator`, `SizeCalculator` и их runtime-модели | `strategy-engine` |
| `docs/components/CalculationContextFactory.md` | `trading-core`: все входы сборки — персистентность ядра плюс вызов к соседу, а общий артефакт к базе не ходит (`docs/architecture/services.md` §«Что в библиотеку НЕ уезжает»). В библиотеке лежит **расчёт**, а не тропа сборки его входов |
| `docs/components/` — файлов владельца `strategies` нет, и это **решение**, а не пропуск | у трёх его исполнителей (валидатор создания, переход статуса, реле outbox) поведение уже имеет дом: `docs/rules/strategy-validation.md`, `docs/lifecycles/Strategy.md`, `docs/architecture/contracts.md` и строка ниже. Компонент-док стал бы четвёртым носителем (`.claude/rules/policy-home.md`); условие возврата — исполнитель, чьё поведение ни одним домом не покрыто |
| `docs/components/` — файлов владельца `bff` нет, и это **решение**, той же формы, что у `strategies` | поведение периметра уже имеет дом: маршрут и форма — `docs/architecture/contracts.md` §«Периметр: что `bff` отдаёт и чего не делает», контекст — §«Контекст тенанта в вызове», поток — §«Живые данные в браузер», доступ — `docs/rules/api-access-policy.md`. Компонент-док стал бы пятым носителем (`.claude/rules/policy-home.md`); условие возврата — исполнитель периметра, чьё поведение ни одним домом не покрыто. **Строку catch-all ниже периметр не наследует:** без этой строки его будущий док достался бы `trading-core` умолчанием |
| `docs/components/OutboxRelayJob.md` | сквозное: текст описывает форму реле **любого** производителя («outbox своей базы», «тема производителя»), и второго дома этой формы не заводится (`.claude/rules/policy-home.md`). Реле есть у ядра, у `strategies` и у всякого сервиса, публикующего через outbox (`docs/architecture/data-ownership.md` §«Outbox и доставка») |
| `docs/components/`: всё остальное (оркестратор, обработчики, исполнители, риск, холды, kill-switch, аномалии, сканер входа, ошибки, ретраи) и их runtime-модели | `trading-core` |
| `docs/processes/`: `candle-loading`, `market-data-calculation`, `snapshot-collection` | `market-data` |
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
