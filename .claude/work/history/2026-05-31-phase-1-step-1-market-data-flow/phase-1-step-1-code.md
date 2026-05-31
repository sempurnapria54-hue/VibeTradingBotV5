# CODE — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы в под-шаге `CODE` шага 1 (написание кода потока
рыночных данных по проработанной концепции) и каковы рамочные
решения этого под-шага.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных (коннект к
  OKX, инструменты, цены/свечи, свежесть)». Статус — `CODE`.
- Концепция доведена до целостности: `DOCS_CHECK_4` пройден чисто
  (см. `phase-1-step-1-docs-check-4.md`). Под-шаг — написание кода
  по `.claude/processes/roadmap-step-execution.md` (под-шаг 5):
  `code-writer` + ревью-итерации (`conventions`/`performance`/
  `disaster`) + аппрув. Синхронизация доков — отдельный под-шаг
  `SYNC_DOCS_FROM_CODE` (после аппрува).

## Рамочные решения под-шага (приняты в чате 2026-05-31)

1. **Смысл `CODE` при существующем кодобейзе — переписать с нуля по
   концепции.** В `src/` был полноценный закоммиченный legacy-код
   (365 файлов, собран ранее «with codex app»); концепт-доки были
   извлечены обратным ходом из него (миграции, см.
   `.claude/decisions/migration-triad.md`). Решение: legacy — не
   база для правок, а референс; свежий код пишется с нуля строго по
   концепции, шаблонам (`.claude/templates/code/`) и `codestyle`/
   `tech-radar`.

2. **Сосуществование с legacy — чистый старт, legacy в архив.**
   Весь legacy `src/` перенесён `git mv` в
   `.codebase-archive/2026-05-31/src/` (референс, не источник
   истины; reversible через git). Канонические пакеты свободны.
   Новый `src/`-дерево строится с нуля по роадмапу; шаг 1 — первый
   кирпич. Бот не собирается/не работает целиком, пока не собраны
   дальнейшие шаги — это ожидаемо для docs-first ребилда.

3. **Стек — Java 25 / Spring Boot 4 (строго по концепции).**
   `tech-radar`/`CLAUDE.md` декларируют Java 25 + Spring Boot 4;
   legacy-pom был Java 21 / SB 3.3.2. Решение: свежий pom — под
   Java 25 + SB4. **Среда сборки этого не поддерживает** (в системе
   максимум JDK 21, нет JDK 25, нет SB4, нет mvn на PATH; сборка —
   в IDEA пользователем). Следствие: код этой сессии **не
   верифицируется** локально; пользователю нужно поставить JDK 25 и
   настроить IDEA на SB4. Точные координаты SB4/Spring Cloud/
   springdoc — под проверку при настройке тулчейна.

## Отложено по концепции (не пробел — flag, не молчаливый пропуск)

- **Владелец оркестрации онбординга инструмента — ORCH-Q1
  (открыт).** Кто драйвит переходы `Instrument.Status` и
  координирует с `CandleGroup.Status` (orchestrator + handler'ы по
  образцу FSM сделки, или иной механизм) — не материализуется до
  решения (`docs/lifecycles/Instrument.md`, `candle-loading.md`).
  В коде шага 1 строятся составные части (сервисы, способные
  выполнить каждый переход; `CandleJob` как производитель свечей),
  но не верхний orchestrator.
- **`MarketDataExpirationChecker` / реакция на устаревание свежести
  — отложено.** Срок свежести — атрибут strategy settings
  (`docs/rules/market-data-freshness.md`), а `Strategy` появляется
  на шаге 2. Сам чекер строится, когда есть settings; в шаге 1 —
  только носители свежести (`Auditable.externalCreatedAt/
  externalModifiedAt`).
- **`MarketPriceData`-кластер (RVO + snapshot + `MarketPriceDataService`
  + ticker-fetch) — отложено.** `docs/models/integrations/okx/OkxTickerResponse.md`
  явно выносит раздачу цены и тикер-фетч в «зону FSM/потребителей
  более поздних шагов, вне кода шага 1». Потребители (калькуляторы/
  FSM) появляются с шага 2. Поэтому ни `MarketPriceData`, ни
  `PriceTickerResponse`/ticker-эндпоинт в коде шага 1 не материализуются
  (по потребности шага). Роадмап-«цены» покрыты концептуально; код —
  с потребителями. **Под подтверждение пользователя.**

## Инвентарь шага 1 (по слоям, канонические пакеты)

База: `domain.model.Auditable` (`OffsetDateTime`-аудит; UTC —
`docs/rules/time-utc.md`).

- **Domain:** `core.exchange.Exchange`(+`Status`);
  `core.instrument.Instrument`(+`Status`,`MarginMode`),
  `core.instrument.external_snapshot.InstrumentExternalSnapshot`;
  `trade.candle.CandleGroup`(+`Status`), `trade.candle.TimeFrame`,
  `trade.candle.Candle`,
  `trade.candle.external_snapshot.CandleExternalSnapshot`;
  `trade.market.MarketPriceData` (RVO, не persisted) +
  `MarketPriceDataExternalSnapshot`.
- **OKX client DTO + adapter:** `OkxApiResponse<T>`,
  `OkxInstrumentResponse`, `OkxCandleResponse` (массив[9]),
  `OkxTickerResponse`, request-параметры; `OkxRestClient` (HTTP +
  подпись + DTO); `ClientService` (boundary, nullable contract) +
  `OkxClientService`.
- **Mapping (MapStruct):** `InstrumentMapper`, `CandleMapper`,
  `TimeFrameMapper` (`domainToOkxClient`/`okxClientToDomain`,
  строгий), `MarketPriceDataMapper`.
- **Persistence:** `AuditableEntity`, `ExchangeEntity`,
  `InstrumentEntity`, `CandleGroupEntity`, `CandleEntity`;
  репозитории; data-services (граница domain↔persistence); Flyway
  миграции под таблицы шага 1.
- **Service (domain):** `ExchangeService`, `InstrumentService`,
  `CandleGroupService`, `MarketPriceDataService`, `CandleJob`.
- **API:** контроллеры (`api/controller/...`) + api request/response
  DTO + мапперы; пакет `api/` (не legacy `rest/`).
- **Config:** `application.yaml` (DB/OKX/flyway), `OkxConfig`,
  HTTP-клиент конфиг, JPA-audit конфиг; main-класс.

## Прогресс

- [x] Legacy `src/` → `.codebase-archive/2026-05-31/src/` (git mv).
- [x] Роадмап: шаг 1 → `CODE` (`phase-1.md`).
- [x] Свежий `pom.xml` (Java 25 / SB4). Vault/Security отложены на шаг 9.
- [x] Scaffold: `TradingBotApplication` (`@EnableScheduling`), `JpaAuditConfig`
  (auditor = `"system"` до шага 9), `application.yaml`.
- [x] Domain-слой: `Auditable`, `core.exchange.Exchange`,
  `core.instrument.Instrument`(+`Status`,`MarginMode`),
  `InstrumentExternalSnapshot`, `trade.candle.TimeFrame`,
  `CandleGroup`(+`Status`,`expectedCount()`/`isDense()`), `Candle`,
  `CandleExternalSnapshot`, `util.PriceConstants`.
- [x] OKX client DTO + adapter: `OkxProperties`/`OkxConfig` (RestClient,
  demo-header), `OkxApiResponse<T>`, `InstrumentResponse`,
  `CandleResponse` (array[9]→named, валидация длины), `OkxRestClient`
  (публичные endpoint'ы, без подписи), `ClientService` (boundary,
  nullable contract), `OkxClientService`, `ExchangeClientException`.
- [x] Mapping: `TimeFrameMapper` (strict, `ONE_DAY→"1Dutc"`),
  `InstrumentMapper` (clientToSnapshot / updateFromSnapshot),
  `CandleMapper` (clientToSnapshot / snapshotToDomain; String→BigDecimal/
  Long, confirm-фильтр).
- [x] Persistence + Flyway: `AuditableEntity` (+JPA auditing),
  `Exchange/Instrument/CandleGroup/CandleEntity`, 4 репозитория,
  4 data-service, `InstrumentMapper`/`CandleMapper` расширены
  domain↔entity, `ExchangeMapper`/`CandleGroupMapper`,
  `V1__create_market_data_tables.sql`.
- [x] Domain services + `CandleJob`: `ExchangeService`,
  `InstrumentService` (онбординг-переходы), `CandleLoader`
  (BACKFILL/SYNC/CHECK/REPAIR с бинарным поиском дыр), `CandleJob`
  (CRON + tail-sync + провизорная координация готовности),
  `CandleLoadingProperties`.
- [x] API: `ExchangeController`/`InstrumentController`/`CandleGroupController`
  (пакет `api/`), api request/response DTO, api-методы мапперов.
- [x] Ревью-итерации (self-review `conventions`/`performance`/`disaster`).
  Формальный аппрув = ревью пользователя в IDEA после сборки.

## Self-review (conventions / performance / disaster)

- **conventions:** поправлено `CandleMapper.toConfirm` →
  `Objects.equals` (вместо `"CONST".equals`). Возможный спорный
  момент: `codestyle` требует статический импорт статических методов —
  оставлены квалифицированные `Objects.isNull`/`BooleanUtils.isFalse`
  (риск конфликтов `isEmpty`); под решение реого. Статус-переходы
  инструмента ставятся в сервисе (решение готовности — на домене
  `Instrument.isReadyForActivation`); rich-domain-пуристы могут
  предложить перенести и сами переходы на модель.
- **performance:** `CandleLoader.reconcile` — 3 запроса (count/min/max)
  на группу за тик; при росте числа групп можно свернуть в 1. Для
  шага 1 (малое N) приемлемо. Батч-вставка свечей — `saveAll` после
  фильтра существующих (не построчно). Индексы покрыты уникальностью
  `(candle_group_id, open_timestamp)`.
- **disaster:** ошибки биржи → `ExchangeClientException`, ловятся
  по-группно в `CandleJob` (одна группа не валит остальные). Рестарт:
  `count`/границы реконсилируются из БД на каждом CHECK (authoritative);
  `advance()` намеренно НЕ в одной транзакции (нельзя держать tx на
  время HTTP) — частичный сбой лечится reconcile-from-DB. Идемпотент-
  ность — уникальность + фильтр. Известные хвосты: счётчик REPAIR
  в памяти (теряется при рестарте); битый массив свечи →
  `IllegalArgumentException` (не обёрнут в `ExchangeClientException`,
  но ловится по-группно).

## Что не верифицировано (нет JDK 25 / SB4 / mvn)

Сборка/прогон — за пользователем в IDEA. Зоны наибольшего риска:
MapStruct-генерация (дженерики `OkxApiResponse<List<String>>`,
enum↔String, `@BeanMapping(ignoreByDefault)`), точные координаты
SB4/springdoc/lombok/mapstruct в `pom`, бинарный REPAIR в
`CandleLoader`, JPA-аудит `OffsetDateTime`.

## CODE-level микро-решения (не из концепции — под ревью)

- `PriceConstants`: `PRICE_PRECISION=36`, `PRICE_SCALE=18` (концепция —
  «общие константы цены» без значений).
- `TimeFrame` несёт `durationMillis` — нужно доменной проверке плотности.
- Density-инвариант на домене: `CandleGroup.expectedCount()`/`isDense()`
  (rich-domain, `business-logic-on-domain-model`).
- Конфиг свечей (`candle-loading.*` в `application.yaml`): cron,
  page-size=100, max-repair-attempts=5, default-planned-start (концепция
  — «конфигурируемы, на CODE»).
- Domain использует плоские id (`exchangeId`/`instrumentId`/
  `candleGroupId`); entity-слой — реальные связи там, где концепция их
  задаёт (`Instrument`→`candleGroups` OneToMany cascade/orphanRemoval).
  Соотнесение — в data-services/мапперах (следующий срез).
- `TimeFrameMapper`: `ONE_DAY → "1Dutc"` (UTC-выровненный дневной бар)
  ради `time-utc`; "1D" открывается в UTC+8. **Под подтверждение.**
- OKX-клиент: HTTP через Spring `RestClient` (SB4); публичные endpoint'ы
  без подписи. Ошибка → `ExchangeClientException` — **placeholder**,
  т.к. конвенция обработки ошибок в `codestyle` TBD (эскалируется).
- `PriceConstants` precision/scale применятся к колонкам свечей в
  persistence-срезе (Flyway numeric(36,18)).

## Связи

- Процесс — `.claude/processes/roadmap-step-execution.md`.
- Концепция шага — `docs/models/domain/core/Instrument.md`,
  `docs/lifecycles/Instrument.md`, `docs/processes/candle-loading.md`,
  `docs/models/domain/other/CandleGroup.md` / `Candle.md`,
  `docs/models/mapping/Instrument.md` / `Candle.md` /
  `MarketPriceData.md` / `TimeFrame.md`,
  `docs/rules/raw-exchange-dto-boundary.md` /
  `market-data-freshness.md` / `time-utc.md` /
  `business-logic-on-domain-model.md`.
- Стиль/стек — `.claude/rules/codestyle.md`, `.claude/rules/tech-radar.md`.
- Шаблон контроллера — `.claude/templates/code/Java/Controller.md`.
- Чистая проверка концепции — `phase-1-step-1-docs-check-4.md`.
</content>
</invoke>
