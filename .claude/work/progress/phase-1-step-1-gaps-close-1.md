# GAPS_CLOSE_1 — шаг 1 Фазы 1 (поток рыночных данных)

## На какой вопрос отвечает этот файл

Что сделано на под-шаге `GAPS_CLOSE_1` шага 1 (закрытие пробелов
`DOCS_CHECK_1`): что размещено, какие вопросы закрыты/обновлены, что
осталось на `DOCS_CHECK_2`.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 1 — «Поток рыночных данных».
- Под-шаг: `GAPS_CLOSE_1` (`.claude/processes/roadmap-step-execution.md`).
- Вход — gap-отчёт `phase-1-step-1-docs-check-1.md` (2 несогласованности,
  3 name-level кандидата, OKX-Q4/TIME-Q1, 6 эскалаций Э1-Э6).
- Эскалации разобраны в чате; решения применены ниже.
- Источник полей моделей — доменные Java-классы (`Instrument`,
  `Exchange`, `Candle`, `CandleGroup`, `InstrumentExternalSnapshot`,
  `Auditable`) и entity (`*Entity`), не архив.

## Решения по эскалациям — как применены

- **Э1 (WS/REST).** Шаг 1 — REST-first; WS отложен до микросервисов.
  Приведены к REST-first: `docs/integrations/okx/contracts/market-price-data.md`,
  `docs/models/mapping/MarketPriceData.md`. OKX-Q4 — не блокер шага 1.
- **Э2 (свежесть).** Шаг 1 свежесть только производит: корректные
  таймстемпы + audit-поля (`externalCreatedAt`/`externalModifiedAt`)
  из `Auditable`; проверка устаревания — у потребителей позже.
  Зафиксировано в `docs/models/domain/other/Auditable.md`.
- **Живая цена.** `MarketPriceDataService`/тикер-фетч — зона FSM
  (поздний шаг), вне кода шага 1. Помечено в `OkxTickerResponse.md` и
  `mapping/MarketPriceData.md`.
- **Э3/Э4 (модели/источник).** `Instrument`/`Exchange`/`Candle`/
  `CandleGroup` материализованы из доменных классов (закрывает
  name-level N1/N2). Битые указатели на `docs/deprecated/` в backlog
  поправлены (Н2).
- **Э5 (сырые OKX DTO).** Заведены инвентари `OkxTickerResponse`,
  `OkxInstrumentResponse`, `OkxCandleResponse` (закрывает N3).
- **Э6 (backfill).** Lifecycle `CandleGroup` (8 статусов) +
  раздел в процессе `market-data-calculation` на уровне, заданном
  классами; детали политики дозагрузки/глубины истории — отложены
  на `DOCS_CHECK_2`.

## Что размещено (новые доки)

| Файл | Тип/слой | Закрывает |
|---|---|---|
| `docs/models/domain/core/Instrument.md` | domain/core | N1 (Э3/Э4) |
| `docs/models/domain/core/Exchange.md` | domain/core | N1 (Instrument.exchangeId) |
| `docs/models/domain/other/Candle.md` | domain/other | N2 (Э3/Э4) |
| `docs/models/domain/other/CandleGroup.md` | domain/other | N2 + TIME-Q1 (§TimeFrame) |
| `docs/models/domain/other/Auditable.md` | domain/other | Э2 (audit/свежесть) |
| `docs/lifecycles/CandleGroup.md` | lifecycle | Э6 (статусы загрузки) |
| `docs/models/integrations/okx/OkxTickerResponse.md` | integrations/okx | N3 (Э5) |
| `docs/models/integrations/okx/OkxInstrumentResponse.md` | integrations/okx | N3 (Э5) |
| `docs/models/integrations/okx/OkxCandleResponse.md` | integrations/okx | N3 (Э5) |

Схема хранения (типы, nullability, уникальность, индексы) — в разделе
«Персистентность» каждой доменной модели (из `*Entity`), без отдельных
файлов `docs/models/persistence/` (слой остаётся скаффолдом, конвенция
как в существующих моделях).

## Что изменено (реконсиляции)

| Файл | Что |
|---|---|
| `docs/processes/market-data-calculation.md` | Н1: битая ссылка backfill→backlog п.8 заменена на lifecycle `CandleGroup`; добавлен раздел загрузки/целостности свечей (Э6). |
| `docs/components/CandleJob.md` | Добавлен раздел жизненного цикла загрузки + связи (Э6). |
| `docs/models/mapping/Candle.md` | Добавлен маппинг `→ domain Candle`, ссылки на новые модели. |
| `docs/models/mapping/InstrumentExternalRules.md` | N1: убрана неверная фраза «base/quote/settle хранятся в domain Instrument» → приходят в `InstrumentExternalSnapshot`. |
| `docs/models/mapping/MarketPriceData.md` | Э1: WS «основной» → REST-first, WS отложен (OKX-Q4). |
| `docs/integrations/okx/contracts/market-price-data.md` | Э1: то же. |
| `docs/models/mapping/TimeFrame.md` | TIME-Q1: канон enum → `CandleGroup.md` §TimeFrame. |

## Несогласованности (Н1, Н2) — починены

- **Н1** — ссылка backfill в `market-data-calculation.md` вела в backlog
  п.8 (Strategy). Заменена на реальный адрес (lifecycle `CandleGroup` +
  раздел процесса).
- **Н2** — указатели backlog п.5/п.6 на пустой `docs/deprecated/`
  поправлены на `.claude-archive/2026-05-21/docs/deprecated/models/domain/old/`;
  для `Candle`/`Instrument` указано, что источник теперь — доменные
  классы (материализованы).

## Вопросы — закрыто / обновлено

- **TIME-Q1** — продвинут: каноническое размещение enum определено
  (`CandleGroup.md` §TimeFrame, первоисточник — свечная подсистема).
  Для кода шага 1 закрыт. Остаточный хвост — свёртка раздела в
  `Strategy.md` (шаг 2). Вопрос оставлен открытым, но сужен.
- **OKX-Q4** — оставлен открытым; снят статус «потенциальный блокер
  шага 1» (REST-first); якорь пересмотра — рефакторинг на микросервисы.
- Остальные 10 вопросов шага 1 не касаются — без изменений.

## Классификационные решения (на ревью)

- **Instrument/Exchange → `domain/core`.** Java-пакет `core.*` +
  дискриминатор «биржевое воплощение» (instId / идентичность биржи).
  Расширяет задокументированный состав core (был Position/Order/
  AlgoOrder/BalanceContainer) reference-core сущностями.
- **Candle/CandleGroup/Auditable → `domain/other`** (онтология явно:
  «свечи», «аудит»). `CandleGroup.Status` — отдельный lifecycle-файл
  (как Order/AlgoOrder), не раздел модели.
- **timeframe/marginMode** — в доке зафиксирован целевой enum-вид;
  в классе `CandleGroup.timeframe` пока `String` (помечено расхождение
  класс↔концепция, приведение — на `CODE`).
- **base/quote/settle** — на `InstrumentExternalSnapshot`, не на domain
  `Instrument`; маппинг snapshot↔domain не материализован (зависит от
  реконсиляции, см. ниже).

## Что осталось на DOCS_CHECK_2

1. Разграничение `Instrument` ↔ `InstrumentExternalSnapshot` ↔
   `InstrumentExternalRules`: где живут base/quote/settle и прочие
   справочные поля, нет ли дублирования. `InstrumentExternalRules.java`
   **не существует** (модель только в доках); `InstrumentResponse`
   (coded DTO) — подмножество vs богатый OKX-маппинг в
   `mapping/InstrumentExternalRules.md` (state/lever/ctType…). Нужен
   маппинг `mapping/Instrument.md` (snapshot↔domain).
2. Детали backfill/repair: политика дозагрузки при дыре (окна, шаги
   бинарного поиска, число попыток), глубина «всей» истории с учётом
   предела OKX REST, расписание `SYNC → CHECK`.
3. Приведение `CandleGroup.timeframe` (и при необходимости статусов) к
   enum в коде — на `CODE`, но отслеживается.
4. Свёртка раздела `TimeFrame` в `Strategy.md` до ссылки — на шаге 2.

## Статус роадмапа

- Шаг 1: `DOCS_CHECK_1` → `GAPS_CLOSE_1` (`phase-1.md`).
- Фаза 1: `IN_PROGRESS` (ролляп без изменений; шаг 1 не-HOLD, прочие
  HOLD).
- Следующее — `DOCS_CHECK_2` (повторная сквозная проверка по
  стадийному обходу `concept-review`).

## Сводка

- Новых доков: 9. Изменено доков: 7. Вопросов обновлено: 2 (TIME-Q1
  сужен, OKX-Q4 разблокирован для шага 1). Несогласованностей
  починено: 2 (Н1, Н2). Затронуто работы-трекинга: open-questions,
  backlog (п.5/6/9), phase-1, прогресс, снапшот.
- Эскалации Э1-Э6 — разобраны и применены.
- Не чисто: открыт `DOCS_CHECK_2` (4 пункта выше).
