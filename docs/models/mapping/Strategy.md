# Strategy — mapping между слоями

## На какой вопрос отвечает этот файл

Как `Strategy` переходит между слоями.

## api ↔ domain

- Полное дерево принимается/отдаётся как один документ
  (`CreateStrategyApiRequest` / `StrategyApiResponse`); вложенные
  api-модели shared между запросом и ответом; аудит-поля — только на
  корне ответа.
- Enum'ы и `Duration` в api — строки (`name` доменного enum;
  ISO-8601, например `PT30M`).
- **Полиморфизм действий** — JSON-дискриминатор `actionKind`
  (`ORDER`/`ALGO_ORDER`/`POSITION`) на api-базе действия (только
  форма сериализации, не поле домена); диспатч в подтипы —
  MapStruct `@SubclassMapping`.
- **Полиморфизм params индикатора** — внешний тег `indicatorType`
  настройки-владельца (Jackson `EXTERNAL_PROPERTY` + `visible`,
  поле-тип `WRITE_ONLY`; механика —
  `docs/rules/persistence-representation.md`): в JSON ключ один, в
  payload `params` тег не дублируется.
- `instrumentInternalId` запроса резолвится в доменный
  `instrumentId` сервисом (проекция `InstrumentDataService`);
  в ответ наружу — `internalId` стратегии и `instrumentInternalId`,
  не id из БД.
- `status` запросом create не передаётся (система ставит `CREATED`);
  смена статуса — отдельная форма `PUT …/status` `{status}`.

## domain ↔ persistence

- **Реляционный каркас** мапится узел-в-узел (root / настройка фазы /
  детали / шаги / действия с JOINED-видами); back-ссылки
  (`strategy`/`detail`/`step`) проставляет wiring после маппинга.
- **JSONB-навес** — сериализованный доменный JSON строками
  (`StrategyJsonConverter`: только непустые значения, Duration —
  ISO-8601): листовые настройки рыночных данных и их params, params
  фазы, условие шага, политика устаревания, `placement` /
  `attachedProtection` / `stopLossSettings` / `trailingSettings`.
- **`stepsByStatus` ↔ плоские строки** `strategy_steps`: ключ map →
  `deal_status`, позиция в списке → `step_index`; обратно — map
  пересобирается группировкой и сортировкой.
- **Порядок действий пакета** кодируется порядком вставки строк
  (LinkedHashSet при записи) и читается по `id` ASC.
- **`targetActionKey` → `target_action_id`**: после вставки дерева
  `StrategyDataService` резолвит ключ в self-FK по действиям той же
  детали (managed-update в той же транзакции; FK deferrable).
- Деревом наружу: чтение — одним join-fetch-запросом
  (`findByInternalIdWithTree`); корневые операции статуса дерево не
  грузят и не перезаписывают.

## Резолв статуса

Статус — административный (`docs/lifecycles/Strategy.md`), биржевой
проекции не имеет; хранится строкой (= `name`), конвертация
enum ↔ строка — на границе persistence.
