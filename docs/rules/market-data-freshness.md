# Свежесть рыночных данных

## На какой вопрос отвечает этот файл

Какое у нас правило свежести рыночных данных и как оно ограничивает
data-dependent действия.

## Правило

- Срок свежести персистентных результатов задаётся через
  `expirationDuration` в strategy settings (`StrategyIndicatorSetting`,
  `StrategyMarketStructureSetting`, см.
  `docs/models/domain/aggregate/Strategy.md`). `maxAgeBars` не используется.
  У **фазы** своего `expirationDuration` нет: `MarketPhase` не персистится,
  вычисляется на лету, и её свежесть наследуется от входов
  (`docs/decisions/market-phase-stateless.md`) — устаревший вход →
  операнд недоступен → `UNKNOWN`.
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
  которого отсчитывается срок (только для персистентных результатов):
  - структура рынка — `windowEndAt`;
  - индикатор — `candleTimestamp`.
  - У фазы своего `referencePoint` нет — она не персистится; свежесть фазы
    = свежесть её входов (индикаторов/структур).
- `confirmedAt` — гейт «результат можно использовать без look-ahead»
  (подтверждающее свидетельство завершилось). Это **не** точка отсчёта
  свежести и не момент устаревания.
- **`expiredAt` — производная величина, вычисляемая на чтение, не
  хранимая колонка**, единообразно для всех трёх типов результатов:

  ```
  expiredAt = referencePoint + ownerSetting.expirationDuration
  свежо ⟺ now < expiredAt
  ```

  Считается в уже существующем свежесть-контуре
  (`MarketDataExpirationChecker` / сервисы); новых полей в моделях и
  нового хранения свежести **не вводится**.
- **Результаты ключуются настройкой-владельцем** (ревизия трек D,
  `docs/decisions/market-data-result-identity-keying.md`): у строки
  результата ровно **один** владелец, его `expirationDuration` и задаёт
  `expiredAt` — общей строки с несколькими запрашивающими (как при
  прежнем шаринге по `config_id`) больше нет.
- **Фаза** (`MarketPhase`) не персистируется — вычисляется на лету, своей
  свежести не имеет; её доступность определяется свежестью входов (см.
  выше). Отдельной проверки «свежести фазы» нет.
- **Якорь пересмотра:** если появятся тяжёлые запросы по устареванию
  персистентных результатов напрямую в SQL без знания настройки-владельца
  — вернуться к вопросу хранения `expiredAt` колонкой.

## Первоисточник и смежное

Правило сквозное (применимо к нескольким сущностям и процессам, см.
`.claude/decisions/rule-source-of-truth.md`). Срок свежести как атрибут —
у settings в `Strategy`; вычисление — у `MarketDataExpirationChecker`;
реакция FSM — `docs/processes/deal-management.md` и
`docs/lifecycles/Deal.md`. Эффект graceful shutdown по policy →
`Deal.shutdownReason = MARKET_DATA_EXPIRED` (см. `docs/lifecycles/Deal.md`).
