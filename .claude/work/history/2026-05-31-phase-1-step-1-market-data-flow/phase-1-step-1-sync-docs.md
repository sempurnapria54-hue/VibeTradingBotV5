# SYNC_DOCS_FROM_CODE — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

На каком шаге мы в под-шаге `SYNC_DOCS_FROM_CODE` шага 1: привести
`docs/` к утверждённому коду (docs←code) после аппрува `CODE`.

## Контекст

- `CODE` апрувнут пользователем 2026-05-31. Переход
  `CODE → SYNC_DOCS_FROM_CODE` (см. `phase-1.md`).
- Направление — docs←code: код истина. Скиллы под-шага —
  `.claude/skills/divergence-review.md` (детект),
  `.claude/skills/reconcile-knowledge.md` (change/remove),
  штатный curator (add).
- Решение по охвату: ренейм `Client → Integration` — **глобальный**
  по всем `docs/` (не только шаг 1).

## Расхождения (детект)

### CHANGE

1. Слой `Client → Integration`: `ClientService`→`IntegrationService`,
   `OkxClientService`→`OkxIntegrationService`,
   `ExchangeClientException`→`ExchangeIntegrationException`, пакет
   `client`→`integration`. **(Этап A — сделано, sed по всем docs.)**
2. Имена методов мапперов по слоям: `clientToSnapshot`→
   `integrationToSnapshot` (sed), `updateFromSnapshot`→`snapshotToDomain`,
   `domainToEntity`/`entityToDomain`→`domainToPersistence`/
   `persistenceToDomain`, `domainToOkxClient`→`domainToOkx`,
   `okxClientToDomain` — удалён, `CandleMapper.persistenceToDomain` —
   удалён.
3. Идентичность наружу = `internalId` (не `id` из БД); ссылки на
   связанные — `exchangeInternalId`/`instrumentInternalId`;
   path-параметры — `internalId`.
4. Enum только в домене; persistence/api/integration — `String`
   (конвертация на границе MapStruct).
5. Auditable по слоям: `AuditableEntity`, новый `AuditableApiResponse`.
6. Джобы в `domain.jobs`; внерасписанный запуск — асинхронно через
   `CandleJobFacade` + `JobController`.
7. Rich-модель: `Instrument.isCandleLoading()`/`isReadyForActivation()`
   (по своим `candleGroups`, грузятся join fetch'ем);
   `CandleGroup.hasNewClosedBar()` (+ `CLOSED_BAR_FACTOR` на модели).
8. `PriceConstants`→`util.Constants`; `saveNewCandles`→`saveCandles`;
   обёртки на контрактной поверхности; удалены неиспользуемые finders.

### ADD

- `CandleGroup.internalId` (+ колонка `internal_id`).
- `JobController` + `CandleJobFacade` (паттерн async-триггера).
- `AuditableApiResponse` (api-слой Auditable).
- `util.Constants` (вложенные `Price`/`Okx`/`Audit`).

### REMOVE / scope (форвард-концепт — НЕ удаляем)

- `MarketPriceData` (RVO), `MarketPriceDataService`, `OkxTickerResponse`/
  ticker, `CandleGroupService`, `MarketPriceDataExternalSnapshot`/маппер
  — сознательно отложены (потребители с шага 2; аппрув подтвердил).
  В реконсиляции лишь убедиться, что доки не подразумевают их наличие
  в шаге 1.

## Реконсиляция (чеклист)

- [x] Этап A: глобальный ренейм `Client→Integration` (файл
  `ClientService.md`→`IntegrationService.md` + sed-идентификаторы).
- [x] Этап A.2: ренейм имён методов мапперов (`domainToOkxClient`→
  `domainToOkx`; `domainToEntity`/`entityToDomain`/`updateFromSnapshot`
  в доках не встречались — переименовывать нечего).
- [x] `models/domain/other/CandleGroup.md`: + `internalId`,
  + колонка `internal_id`/unique, снят устаревший «класс↔концепция»
  (домен уже enum), `timeframe`/`status` — String в persistence.
- [x] `models/domain/core/Instrument.md`: identity наружу
  (`internalId`/`exchangeInternalId`); `status`/`margin_mode` — String
  в persistence. (Rich-методы — не добавлял: док концептуальный, имён
  методов не перечисляет, как и для `CandleGroup`.)
- [x] `models/mapping/Instrument.md`: `IntegrationService` (sed);
  контентно расхождений нет (api-identity — в api README).
- [x] `models/mapping/Candle.md`: `IntegrationService` (sed); удалённый
  `persistenceToDomain` в доке не описан — менять нечего.
- [x] `models/mapping/TimeFrame.md`: `domainToOkx` (одна сторона),
  обратное направление снято; бары в `Constants.Okx`.
- [x] `processes/candle-loading.md`: `domain.jobs` + async-триггер;
  провизорная координация готовности (`refreshInstrumentReadiness`,
  ORCH-Q1 seam).
- [x] `components/CandleJob.md`: `domain.jobs`; async-триггер через
  `JobController`/`CandleJobFacade`; `hasNewClosedBar` как предикат модели.
- [x] `models/persistence/README.md`: правок не требует — стандартные
  ORM-проекции туда не выносятся; enum→String/audit/`internal_id`
  зафиксированы в моделях (`CandleGroup.md`/`Instrument.md`/`Auditable.md`).
- [x] `models/api/README.md`: статус (API введён в шаге 1) + конвенции
  слоя (`internalId` не `id`, `@Schema`, enum=String, `AuditableApiResponse`).
- [x] `models/domain/other/Auditable.md`: Auditable по слоям
  (домен/persistence/api).
- [x] Идентичность наружу — `Instrument.md` + `api/README.md`.
- [x] Референты `IntegrationService.md` — sed заменил пути ссылок;
  стале-токенов нет.
- [x] Отложенные (`MarketPriceData`/ticker/`CandleGroupService`/…) —
  форвард-концепт; step-1-доки их наличие в шаге 1 не подразумевают,
  правок не требуют.

## Открытый хвост

- ~~Entity→Persistence в mapping-доках будущих шагов~~ — снят: токенов
  `domainToEntity`/`entityToDomain` в доках нет (mapping-доки
  описывают направление концептуально). Переименовывать нечего.

## Связи

- Процесс — `.claude/processes/roadmap-step-execution.md` (под-шаг 6).
- Прогресс `CODE` — `phase-1-step-1-code.md`.
- Скиллы — `divergence-review.md`, `reconcile-knowledge.md`.
