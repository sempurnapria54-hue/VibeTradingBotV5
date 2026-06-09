# Свежесть рыночных данных

## На какой вопрос отвечает этот файл

Какое у нас правило свежести рыночных данных и как оно ограничивает
data-dependent действия.

## Правило

- Срок свежести каждого вида рыночных данных задаётся через
  `expirationDuration` в strategy settings (`StrategyIndicatorSetting`,
  `StrategyMarketStructureSetting`, `StrategyMarketPhaseSetting`, см.
  `docs/models/domain/aggregate/Strategy.md`). `maxAgeBars` не используется.
- Устаревание вычисляется в runtime сервисом
  `MarketDataExpirationChecker` (см.
  `docs/components/MarketDataExpirationChecker.md`); состояние свежести в
  БД не хранится.
- **Jobs рыночных данных не меняют `Strategy.Status`.** `Strategy.ACTIVE`
  — административное разрешение, не гарантия runtime-ready данных. Если
  свежих входных данных нет, job не создаёт новый result, а старый
  постепенно становится expired.
- **Data-dependent действие не выполняется по устаревшим данным.** Если
  данные, нужные для входа или для конкретного `StrategyStep`, устарели
  или отсутствуют — это блокирующее условие; реакция задаётся
  `StrategyStep.marketDataExpiredSetting` (`WAIT` / `BLOCK_STEP` /
  `GRACEFUL_CLOSE` / `KILL_SWITCH`). `BLOCK_STEP` не блокирует
  refresh/cancel/close/safety.
- Если проблема со свежестью обнаружена уже при сборе
  `CalculationContext`, калькулятор возвращает controlled calculation
  error, а не считает по старым данным (см.
  `docs/components/models/CalculationError.md`).

## Точка отсчёта и срок свежести (на чтение)

- **Точка отсчёта устаревания (`referencePoint`)** — момент данных, от
  которого отсчитывается срок:
  - структура рынка — `windowEndAt`;
  - фаза рынка — `candleTimestamp`;
  - индикатор — `candleTimestamp`.
- `confirmedAt` — гейт «результат можно использовать без look-ahead»
  (подтверждающее свидетельство завершилось). Это **не** точка отсчёта
  свежести и не момент устаревания.
- **`expiredAt` — производная величина, вычисляемая на чтение, не
  хранимая колонка**, единообразно для всех трёх типов результатов:

  ```
  expiredAt = referencePoint + askingSetting.expirationDuration
  свежо ⟺ now < expiredAt
  ```

  Считается в уже существующем свежесть-контуре
  (`MarketDataExpirationChecker` / сервисы, принимающие запрашивающую
  настройку); новых полей в моделях и нового хранения свежести **не
  вводится**.
- **Шаримые результаты** (структура / индикатор, ключ по `config_id`):
  на общей строке единого `expiredAt` нет — свежесть оценивается под
  **каждую** запрашивающую настройку (своя `expirationDuration`).
- **Фаза** (per-strategy, ключ по контейнеру): конфликта общей строки
  нет, но `expiredAt` всё равно считается **на чтение** — единый
  механизм, без хранимого состояния свежести.
- **Якорь пересмотра:** если появятся тяжёлые запросы по устареванию
  (например, отбор устаревших фаз напрямую в SQL без знания
  запрашивающей настройки) — вернуться к вопросу хранения `expiredAt`.

## Первоисточник и смежное

Правило сквозное (применимо к нескольким сущностям и процессам, см.
`.claude/decisions/rule-source-of-truth.md`). Срок свежести как атрибут —
у settings в `Strategy`; вычисление — у `MarketDataExpirationChecker`;
реакция FSM — `docs/processes/deal-management.md` и
`docs/lifecycles/Deal.md`. Эффект graceful shutdown по policy →
`Deal.shutdownReason = MARKET_DATA_EXPIRED` (см. `docs/lifecycles/Deal.md`).
