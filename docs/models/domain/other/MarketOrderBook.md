# MarketOrderBook

## На какой вопрос отвечает этот файл

Что это за доменная модель `MarketOrderBook`.

## Назначение

`MarketOrderBook` — **срез книги заявок инструмента на момент**:
верхние уровни обеих сторон, снятые одним проходом сбора
(`docs/processes/snapshot-collection.md`). Слой — `domain/other` (прочая
хранимая модель). Java-класс —
`domain.model.trade.market_snapshot.MarketOrderBook`.

**Ряд невосполнимый:** пропущенный срез не добывается потом — у площадки
нет чтения «каким был стакан в прошлый вторник». Отсюда и правило
хранения: ряд не чистится — с областью действия и порядком сокращения,
объявленными в доме (`docs/rules/market-data-retention.md`).

## Почему в имени нет слова «снимок»

В прозе это **срез**, но имя модели его не несёт, и причина
механическая: маркер уровня в проекте — суффикс `ExternalSnapshot` у
формы ответа площадки (`InstrumentExternalSnapshot`,
`OrderExternalSnapshot` и прочие). Назови доменную модель
`OrderBookSnapshot` — её граничная форма стала бы
`OrderBookSnapshotExternalSnapshot`: маркер уровня перестал бы читаться,
потому что то же слово уже стои́т в основе
(`.claude/rules/naming.md` §«Разведение уровней абстракции» — способ
разведения выбирается **до** имён).

Отсюда семейство `Market*`: `MarketOrderBook`, `MarketTicker` рядом с
`MarketPriceData`, `MarketStructure`, `MarketPhase`. Граничные формы —
`MarketOrderBookExternalSnapshot` и `MarketTickerExternalSnapshot`.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор среза. |
| `instrumentId` | `Long` | Инструмент (`Instrument.id`). |
| `externalTimestamp` | `Long` | Метка времени **площадки**, UTC миллисекунды: момент, к которому относится книга. |
| `observedTimestamp` | `Long` | Наша метка приёма, UTC миллисекунды. |
| `bids` | `List<OrderBookLevel>` | Уровни покупки, от лучшего к худшему. |
| `asks` | `List<OrderBookLevel>` | Уровни продажи, от лучшего к худшему. |

**Две метки времени, и обе несущие.** Биржевая отвечает на вопрос «к
какому моменту относится книга», наша — «когда мы её увидели»; их
разность и есть задержка, по которой разбирают инцидент
(`docs/architecture/market-data-collection.md` §«Невосполнимые срезы»,
`docs/rules/time-utc.md`). Одна метка на обе роли делает задержку
неизмеримой.

**Идентичность среза — пара `(instrumentId, externalTimestamp)`.**
Повторный проход, заставший ту же книгу (площадка отдаёт серверный кэш с
шагом 50 мс, `docs/integrations/okx/contracts/order-book.md`), второй
строки не создаёт (`docs/rules/idempotency-via-unique.md`).

**Пустая сторона законна** и означает «заявок на этой стороне нет», а не
отказ чтения: у неликвидного инструмента такое состояние достижимо.
Отсутствие **строки** означает «срез не снят» — это другое состояние, и
различает их наличие строки, а не пустота поля
(`docs/rules/absent-value-semantics.md`).

## Уровень книги

`OrderBookLevel` — вложенная величина, своей таблицы не имеет.

| Поле | Тип | Назначение |
|---|---|---|
| `price` | `BigDecimal` | Цена уровня. |
| `size` | `BigDecimal` | Объём на уровне. |
| `orderCount` | `Integer` | Число заявок на уровне; площадка отдаёт его, и по нему видно, один это крупный участник или много мелких. |

## Персистентность

- **Таблица** — `order_book_snapshots`, гипертаблица временного ряда по
  `external_timestamp` (`docs/architecture/data-ownership.md`
  §«Временные ряды»).
- **Уровни — JSONB** в строке владельца, не отдельной таблицей: они
  навешаны на каркас и FK на них ниоткуда не ведёт
  (`docs/rules/persistence-representation.md` §«Реляционно или JSONB»).
  Нормализация дала бы сорок строк на срез вместо одной — при проходе
  раз в минуту по всему листингу это разница на два порядка в объёме
  ряда, который **не чистится**.
- **Ключ уникальности** — `(instrument_id, external_timestamp)`;
  операнды ключа выходят из JSONB в колонки по тому же правилу.
- **Глубина среза** (сколько уровней пишется) — величина сбора, не
  модели: `docs/architecture/market-data-collection.md`.

## Связи

- Процесс сбора — `docs/processes/snapshot-collection.md`.
- Правило сбора и величины — `docs/architecture/market-data-collection.md`.
- Хранение — `docs/rules/market-data-retention.md`.
- Контракт чтения площадки — `docs/integrations/okx/contracts/order-book.md`.
