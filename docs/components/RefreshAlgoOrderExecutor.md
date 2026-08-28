# RefreshAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `REFRESH_ALGO_ORDER_COMMAND` (компонент-executor): что делает,
границы.

## Назначение

Получает `REFRESH_ALGO_ORDER_COMMAND`. Загружает локальный algo-order и проходит
**algo evidence-cycle внутри одной команды** (эскалация, обрыв на первом
успешном; полный обход — только при не-найдено):

```text
GET /trade/order-algo        (по algoId; нет externalId → по algoClOrdId)
  → orders-algo-pending
  → orders-algo-history (3m)   (ordType обязателен из conditionType)
```

Обновляет сущность, прогоняет внешний статус через
`AlgoOrderExternalStatusResolver`, при необходимости обновляет
`DealActionState` (см. `docs/rules/external-status-resolution.md`,
`docs/models/mapping/AlgoOrder.md`).

Сам выносит терминал: не найден после **полного** цикла →
`ExternalNotFoundException` → `AlgoOrder.ERROR` + `MISSING_AFTER_REFRESH`
(архива глубже 3m у algo нет). Обновляет `AlgoOrder`; cross-entity
refresh (`REFRESH_ORDER_COMMAND` / `REFRESH_POSITION_COMMAND`) — отдельные
команды, выбирает FSM. Pending/history-эндпоинты — звенья цикла; их судьба
как самостоятельных `ServiceCommandType` — CMD-Q3. Владение циклом —
`docs/decisions/refresh-evidence-cycle-ownership.md`. Общая семантика
`REFRESH_*` — `docs/components/ServiceCommandExecutor.md`.

## Исключение из «обновляет только `AlgoOrder`» — числа риска на сделке

**Наблюдение алго-ордера меняет операнд четвёртого числа сделки**, поэтому
executor той же транзакцией пересчитывает **все четыре** числа риска на
`Deal` — по общему правилу «кто меняет любой операнд, пересчитывает всю
четвёрку» (`docs/models/domain/aggregate/Deal.md` §«Взятый риск», таблица
триггеров — место истины; формулы здесь не пересказываются).

Меняемых операндов два, и оба наблюдаемы только здесь (C3 + A7
`DOCS_CHECK_19`):

- **уровень трейлинговой защиты.** Трейлинг двигает **биржа**;
  наблюдаемое значение приходит полем
  `AlgoOrder.condition.trailing.externalPrice` (`moveTriggerPx`) —
  **не** плоским `AlgoOrder.externalPrice`, которое несёт `actualPx`,
  фактическую цену срабатывания (B2 `DOCS_CHECK_20`;
  `docs/models/domain/core/AlgoOrder.md` §«Поля фактического
  срабатывания»). Без пересчёта на наблюдении «риск, снятый защитой»
  не обновлялся бы вовсе на том классе конфигураций, где стоп
  **непрерывно** снижает риск;
  - **Приземление операнда утверждено** (B2 `DOCS_CHECK_20`): рефреш
    обновляет вложенный `condition` **пополевым мерджем** из снапшота
    — маппером `updateFromSnapshot(snapshot, @MappingTarget algoOrder)`
    с `nullValuePropertyMappingStrategy = IGNORE`
    (`.claude/rules/codestyle.md` §Маппинг). Целиком `condition` не
    заменяется: снапшот рефреша несёт не все её поля (заявленные
    параметры трейлинга приходят при постановке, `moveTriggerPx` —
    при наблюдении), и замена целиком затирала бы разницу null'ами.
    Сериализация в jsonb идёт после мерджа, всей моделью условия —
    отдельного частичного апдейта колонки не вводится;
- **исчезновение защиты без замещения.** Рефреш обнаруживает
  терминальный статус защитной ноги (`MISSING_AFTER_REFRESH`,
  сработавшая или снятая биржей) — операнд «действующая защита»
  пропадает, и объявленное «действующей защиты нет ⇒ поле пусто»
  получает здесь своего писателя.

Cross-entity refresh это не вводит: читаются защитные ноги и ноги входа
**той же** сделки, чей `AlgoOrder` обновлён.
