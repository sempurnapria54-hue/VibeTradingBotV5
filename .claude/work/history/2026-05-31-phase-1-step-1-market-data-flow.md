# Шаг 1 Фазы 1 — поток рыночных данных (DONE)

## На какой вопрос отвечает этот файл

Что сделано в шаге 1 Фазы 1 (поток рыночных данных) и где детальные
артефакты.

## Итог

Шаг 1 «Поток рыночных данных (коннект к OKX, инструменты, цены/свечи,
свежесть)» доведён до `DONE` по процессу docs-first
(`.claude/processes/roadmap-step-execution.md`): концепция (4 итерации
`DOCS_CHECK`/`GAPS_CLOSE`) → `CODE` (написание + ревью + аппрув) →
`SYNC_DOCS_FROM_CODE` → `DONE`.

## Что построено (код, `src/`)

- Domain: `Exchange`, `Instrument`, `CandleGroup`, `Candle`, `TimeFrame`,
  граничные `*ExternalSnapshot`, `Auditable`.
- Integration (бывш. Client): `IntegrationService` + `OkxIntegrationService`,
  `OkxRestClient`, OKX DTO; граница — нормализованные снапшоты.
- Persistence: entity + репозитории + data-services + Flyway `V1`.
- Mapping (MapStruct), domain-сервисы онбординга, `CandleLoader`,
  `CandleJob` (`domain.jobs`) + `CandleJobFacade`/`JobController`, API
  (контроллеры + request/response DTO), config.

## Ключевые конвенции, зафиксированные на ревью кода (первый реальный `CODE`-ревью)

Закреплены в `.claude/rules/codestyle.md` (+ `tech-radar.md`), а фокус
`conventions` (`.claude/skills/conventions-review.md`) наполнен по этому
ревью: поля private + Lombok; обёртки в типах контрактной поверхности;
enum только в домене (иначе String); identity наружу — `internalId`
(не `id` из БД); слой `Integration` (не Client); джобы в `domain.jobs`
+ async-триггер через фасад; Auditable по слоям; имена методов мапперов
по слоям без избыточных `@Mapping`; `util.Constants`; статик-импорты
предикатов; `Collectors.toList`; без `New` в именах; `getRequiredBy*` в
DataService; без внутренних классов в сервисах; rich-модели + низкая
вложенность; проекции вместо вытягивания сущности ради поля; без
неиспользуемых методов.

## Не реализовано осознанно (форвард-концепт)

`MarketPriceData`/ticker, `MarketPriceDataService`, `CandleGroupService`
— потребители с шага 2. Владелец оркестрации онбординга — ORCH-Q1
(в коде провизорный seam `CandleJob.refreshInstrumentReadiness`).

## Не верифицировано

Сборка — за пользователем в IDEA (JDK 25 + SB4; локально нет). Зоны
риска: MapStruct-генерация, координаты SB4 в `pom`.

## Детальные артефакты

Подпапка `2026-05-31-phase-1-step-1-market-data-flow/`: прогресс-файлы
`CODE`, `SYNC_DOCS_FROM_CODE`, 4×`DOCS_CHECK`, 3×`GAPS_CLOSE` (рамочные
решения, инвентарь, self-review, список расхождений docs↔code).
