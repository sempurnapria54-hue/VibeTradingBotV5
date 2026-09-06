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
- **Тело команды создания несёт не весь корень: тенанта в нём нет.**
  `tenantId` приходит **заголовком контекста вызова** и проставляется
  приёмником (`docs/models/domain/aggregate/Strategy.md` §«У каждого поля
  контекста назван писатель и момент»); `status` телом тоже не
  передаётся. Это и есть работа api-слоя: доменная форма и форма запроса
  различаются ровно теми полями, которые вызывающий объявлять не вправе.
- **Идентичности контекста через слои не переименовываются.** Корень
  определения несёт `tenantId`, `exchangeAccountInternalId` и
  `instrumentInternalId` уже в домене
  (`docs/models/domain/aggregate/Strategy.md` §«Контекст называется
  идентичностями, а не ключами чужой базы»), поэтому api-слой их только
  переносит: резолва в числовой ключ на границе api ↔ domain больше нет.
  Наружу — те же значения плюс `internalId` стратегии, не id из БД.
  Снятая редакция резолвила `instrumentInternalId` запроса в доменный
  `instrumentId` сервисом (проекция `InstrumentDataService`) — форма
  монолита, где домен и база были одни.
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
- **`tenantId` на стороне копии отбрасывается.** Колонки у неё нет
  (`docs/models/domain/aggregate/Strategy.md` §Персистентность), и
  обратный переход даёт пустоту; читается она как «не хранится этой
  стороной», а не «тенанта нет» (`docs/rules/absent-value-semantics.md`).
  У владельца определений поле переносится как есть.
- **`exchangeAccountInternalId` / `instrumentInternalId` → числовые FK —
  только у копии ядра.** Резолв идёт по её собственным проекциям
  (`exchange_accounts.internal_id`, `instruments.internal_id`, обе
  `UNIQUE`) и живёт на границе domain → persistence, потому что числовая
  связь — деталь базы ядра
  (`docs/models/domain/core/Instrument.md` §«Проекция у торгового ядра»).
  У владельца определений такого перехода нет: он хранит идентичности как
  есть. Нерезолвенная идентичность — отказ приёма копии, а не пустая
  ссылка: копия без счёта или инструмента не читается отбором входа.
- Деревом наружу: чтение — одним join-fetch-запросом
  (`findByInternalIdWithTree`); корневые операции статуса дерево не
  грузят и не перезаписывают.

## Резолв статуса

Статус — административный (`docs/lifecycles/Strategy.md`), биржевой
проекции не имеет; хранится строкой (= `name`), конвертация
enum ↔ строка — на границе persistence.
