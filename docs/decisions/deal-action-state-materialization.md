# Материализация `DealActionState`: представление и размещение

## На какой вопрос отвечает этот файл

Почему `DealActionState` материализован именно так: `RuntimeTarget`
отдельным объектом, retry через базу `Retryable`, отдельный lifecycle,
вложенные объекты в БД как jsonb, размещение в `domain/other`.

## Контекст

`DealActionState` — центральная для command-layer операционная модель:
держит связь `StrategyAction ↔ Order/AlgoOrder/Position`,
идемпотентность/recovery/retry исполнения. До шага 4 не
материализовалась (открытый вопрос **DEAL-Q3**): структура известна из
архива в двух несогласованных вариантах, размещение и наличие отдельного
lifecycle открыты. `DOCS_CHECK_1` шага 4 поднял её как блокер `CODE`
(находка N1).

Два архивных варианта представления:
- **СК §6** — `RuntimeTarget` отдельным объектом (`entityType`,
  `entityId`), retry — наследованием от `Retryable`.
- **ЖЦ §7** — `targetEntityType`/`targetEntityId` инлайн-полями + retry-
  поля прямо в классе.

## Решение

Материализована модель `docs/models/domain/other/DealActionState.md` +
lifecycle `docs/lifecycles/DealActionState.md`. Выбор (валидирован в
чате на `GAPS_CLOSE_1`):

- **«Куда нацелено» — отдельный объект `RuntimeTarget`** (`entityType:
  TargetEntityType`, `entityId`), не инлайн-поля.
- **Retry — через общую базу `Retryable`** (`attemptCount`,
  `maxAttempts`, `nextRetryAt`, `lastError: RetryError`), как у прочих
  повторяемых сущностей (`docs/components/RetryPolicyService.md`).
- **Статусные переходы — отдельным lifecycle** (паритет с
  `Order`/`AlgoOrder`), а не статусы разделом модели.
- **Вложенные `RuntimeTarget`/`RetryError` в БД — jsonb** на строке
  `DealActionState` (по `docs/rules/persistence-representation.md`).
- **Размещение — `docs/models/domain/other/`**: операционное состояние
  сопровождения исполнения, не торговый бизнес-агрегат (тем владеет
  `Deal`); прецедент — `AnomalyReport` (операционная модель + lifecycle
  в `other`, `.claude/decisions/model-layer-ontology.md`).

## Обоснование

- `RuntimeTarget` объектом + база `Retryable` согласуется с rich-доменом
  (`.claude/rules/codestyle.md`) и единой retry-механикой, уже заявленной
  в `RetryPolicyService.md` (там `DealActionState` уже описан как
  наследник `Retryable`); инлайн-вариант ЖЦ §7 размазал бы target и retry
  по плоским полям.
- Отдельный lifecycle оправдан: 7 статусов со строгими переходами —
  такая же статусная механика, как у `Order`/`AlgoOrder`.
- Размещение в `other` — рутинная классификация (`knowledge-curator`):
  модель не про PnL/бизнес-цикл сделки, а про операционное исполнение;
  совпадает с осью разделения `aggregate` (Deal/Strategy) vs `other`
  (операционные/аудит/данные).

## Альтернативы (отвергнуты)

- **Инлайн `targetEntityType`/`targetEntityId` + retry-поля (ЖЦ §7).**
  Отвергнуто: теряет инкапсуляцию target и переиспользуемую базу retry.
- **Статусы разделом модели без отдельного lifecycle.** Отвергнуто:
  нарушает паритет с `Order`/`AlgoOrder`, где статусная механика — в
  lifecycle.
- **Размещение в `domain/aggregate`.** Отвергнуто: `DealActionState` не
  торговый агрегат, а операционное состояние; прецедент `AnomalyReport`
  кладёт аналогичное в `other`.

## Закрытие вопроса

DEAL-Q3 закрыт на `GAPS_CLOSE_1` шага 4 фазы 1 (2026-06-10). Смежный
DEAL-Q1 (persisted retry-state финализации сделки) — **не** этим решением:
финализация получила собственный дом — отдельную сущность
`DealFinalizationState` (DEAL-Q1 закрыт на `GAPS_CLOSE_1` шага 6,
`docs/decisions/deal-finalization-state-materialization.md`), а не
обобщение `DealActionState`.
