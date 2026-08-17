# InstrumentExternalRulesDataService

## На какой вопрос отвечает этот файл

Кто отдаёт внешние правила инструмента (компонент — граница
domain ↔ persistence): что возвращает, как хранит.

## Назначение

`InstrumentExternalRulesDataService` — граница domain ↔ persistence для
`InstrumentExternalRules` (см.
`docs/models/domain/other/InstrumentExternalRules.md`). Отдаёт актуальные
правила из БД (`findByInstrumentId` → `Optional`, пусто — навес ещё не
материализован) и сохраняет/обновляет их (`save`). Сам правила у биржи
не запрашивает — их обновляет
`docs/components/InstrumentExternalRulesSyncJob.md`.

Хранение — JSONB-навесом на строке-владельце `instruments` (собственной
таблицы у правил нет): чтение через проекцию навеса (без вытягивания всей
сущности), запись через load-modify строки-владельца (чтобы audit-поля
инструмента обновлялись штатным JPA auditing).

## Гидрация ставки комиссии — здесь

**Ставку в отдаваемый навес наливает этот сервис** (H1, `GAPS_CLOSE_4`).
Ставка на навесе не хранится — там только ключ группы `externalFeeGroupId`, а
значение живёт строкой `TradeFeeRate` (`trade_fee_rates`,
`docs/models/domain/other/TradeFeeRate.md`). Аксессоры
`takerFeeRate()`/`makerFeeRate()` делегируют в **подгруженную** строку,
поэтому кто-то обязан её подгрузить.

- **Что делает:** отдавая `InstrumentExternalRules`, резолвит актуальную
  строку ставки по **тройке** (`exchangeId`, `externalInstrumentType`,
  `externalFeeGroupId`) — биржу сервис знает через инструмент-владельца
  навеса, поэтому в тексте «по паре» она подразумевалась; ключ и индекс
  `trade_fee_rates` — тройка (H12, `GAPS_CLOSE_6`;
  `docs/models/domain/other/TradeFeeRate.md` §Персистентность). Запрос
  ограниченный (последняя по `created_at`, limit 1;
  `docs/models/domain/other/TradeFeeRate.md` §Персистентность) — и кладёт её
  в возвращаемую модель. Ставка не резолвится → аксессоры отдают `null` →
  реджект `FEE_RATE_UNAVAILABLE` у `RiskValidator` (null-политика —
  `docs/models/domain/other/InstrumentExternalRules.md` §«Ставка комиссии»).
- **Гидрируются обе тропы чтения** — и та, что через
  `CalculationContextFactory`, и прямая из `RiskValidator` (§Использование).
  Ровно поэтому владелец — **хранилищный слой, а не фабрика контекста**:
  гидрация в фабрике накрыла бы только одну тропу, и `RiskValidator` получал
  бы негидрированный навес → `takerFeeRate()` = `null` → `FEE_RATE_UNAVAILABLE`
  блокировал бы **каждый** risk-creating вход. Единственная точка, через
  которую проходят обе тропы, — эта граница.
- **Читатели о справочнике не знают.** Ни `CalculationContextFactory`, ни
  `RiskValidator` не видят `TradeFeeRate`, не резолвят группу и не знают о
  `trade_fee_rates`: они получают готовую модель и зовут аксессор. Знание о
  том, что значение лежит в другой таблице, локализовано здесь — это и есть
  работа границы domain ↔ persistence.
- **Почему не «спец-запросом при материализации» вообще (без владельца).**
  Опора codestyle §«Вложенность и rich-модели» («нужны связанные сущности —
  грузим спец-запросом, `join fetch`») написана под **ассоциации сущностей** и
  на связку «JSONB-навес + ключ внутри JSONB + реляционная таблица ставки»
  механически не переносится: `join fetch` тут нечего фетчить. Поэтому
  гидрация — явный шаг этого сервиса, а не свойство ORM-маппинга.

## Использование

Читателей **три**:

1. **`CalculationContextFactory`** (кладёт в `CalculationContext`) — для
   округления цены/размера, расчёта размера в контрактах
   (`ctVal`/`lotSz`/`minSz`), проверки min/max limits, биржевого max
   leverage, торгуемости инструмента (`status`).
2. **`RiskValidator`** (напрямую) — то же плюс **прогноз комиссии в
   риск-сайзинге** (`takerFeeRate()` — гидрируется этим сервисом, см. выше).
3. **`FinalizeDealExitExecutor`** (шаг 7, H7 `DOCS_CHECK_11`) — **только
   `ctVal`**, для омиссионного члена epsilon: ожидаемая комиссия сделки
   извлекается из `plannedRiskAmount` вычитанием ценовой части
   (`docs/decisions/pnl-finalization-mechanics.md` реш.5 §epsilon).
   **Ставку финализатор не читает** — гидрация ему не нужна, и от
   свежести/доступности `TradeFeeRate` он не зависит.

Если актуальных правил нет — это блокирующее условие: фабрика/валидатор
возвращают controlled ошибку (`INSTRUMENT_RULES_MISSING` у
`RiskValidator`). **У финализатора пустой `ctVal` означает, что сверка не
выполняется** — `Deal.reconciliationStatus = NOT_RUN`, терминал не
блокируется (H25 `DOCS_CHECK_12`,
`docs/components/FinalizeDealExitExecutor.md` §Epsilon). Прежняя редакция
отсылала «там же, §"Ветка операнд пуст"» — **кросс-ссылка была битой**:
целевая клауза покрывает **другой** операнд (пустой `plannedRiskAmount`), а
ветки «навес отсутствует / `ctVal` пуст» не было ни в решении, ни у
владельца поведения.

**Расчётной валюты инструмента здесь нет** — её дом `Instrument`
(`docs/decisions/instrument-currencies-home.md`), поэтому три потребителя
валюты шага 7 читателями навеса **не становятся**.

## Связи

- Модель навеса — `docs/models/domain/other/InstrumentExternalRules.md`.
- Дом ставки — `docs/models/domain/other/TradeFeeRate.md`.
- Обновляет навес и ставку — `docs/components/InstrumentExternalRulesSyncJob.md`.
- Читатели — `docs/components/RiskValidator.md`,
  `docs/components/models/CalculationContext.md`.
- Решение (дом ставки, seam, ось резолва) —
  `docs/decisions/pnl-finalization-mechanics.md` реш.4.
