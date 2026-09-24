# Фокус `test` шага 12: проход по ящику `market-data`

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по коду чёрного ящика `market-data` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением кода; заходом, пишущим
отчёт, перепроверяются. Пути — от
`services/market-data/src/test/java/com/example/marketdata/box/`; `md:N` —
строки `.claude/tests/cases/market-data.md`.

## Невалидность — проверено не всё обещанное клеткой

1. **B8.2** — `AccessContourBoxTest.java:69-71`: error-DTO у трёх `401` не
   проверен (md:1088); с проверкой клетка краснела бы, как B8.1.
2. **B9.1, B9.5, B9.7** — `UnconfiguredAccessContourTest.java:31-34`,
   `ScheduleInputBoxTest.java:64-66`, `StockDatabaseImageBoxTest.java:36-39`:
   `isInstanceOf(Exception.class)` — причина неподъёма не установлена.
3. **B9.5** — `ScheduleInputBoxTest.java:55-63`: на пустом каталоге четыре тика
   молчат и включёнными; `CRON_KEYS hasSameSizeAs SWITCH_KEYS` — тавтология
   литералов; шестой `@Scheduled` клетку не уронит.
4. **B7.7** — `CatalogReadsBoxTest.java:148-156`: пропущены `…/indicator-values/latest`,
   `…/market-structures/latest`.
5. **B7.9** — там же `:199-209`: пропущены `/rules`, `/market-structures/latest`,
   `/prices`; фичи читаются телом `{}`.
6. **B9.2** — `UnconfiguredConnectorAddressBoxTest.java:120`: класс отказа не
   проверен.
7. **B8.11 (`debt`)** — `AccessContourBoxTest.java:220-221`: при погашении долга
   позеленеет без строки; нужен прирост `access_denials` ровно на 1.
8. **B3.15** — `CandleLoadingBoxTest.java:259-264`: `afterFirst` не закреплён,
   0 против 0 зелено.
9. **B3.14** — там же `:236-247`: продвижение первой группы и журнал отказа
   второй не проверены.
10. **B3.8** — `RepairAttemptsBoxTest.java:33,57`: цикл до `ERROR` с бюджетом 10
    тиков вместо «ровно на третьем».
11. **B3.7** — `CandleLoadingBoxTest.java:144-148`: сужение окна вдвое не
    проверено.
12. **B4.3** — `DerivativesBoxTest.java:85-88`: «после прогрева» не проверено,
    только число.
13. **B4.6** — там же `:121-122`: границы окна — `isNotNull`.
14. **B7.3** — `CatalogReadsBoxTest.java:75`: значения полей не сверены.
15. **B7.4** — там же `:99`: нижняя граница не сверена.
16. **B1.3, B1.4** — `RequirementsBoxTest.java:82`, `:95-97`: «не убавилось» на
    нуле свечей; «свечи на месте» не проверено.
17. **B9.3** — `TwoInstrumentTypesBoxTest.java:233,241`; **B9.4** —
    `SnapshotPassBoxTest.java:239-243`: чтение листинга по типу и индексы
    обеих карт не наблюдаются.
18. **B6.12** — `FeatureReadBoxTest.java:256`; спорно **B2.4** —
    `CatalogSyncBoxTest.java:100`: сверяются числа строк, не содержимое.
19. **B10.5** — `AbsentOutputsBoxTest.java:92-96`: `>=` проходит удаление плюс
    вставку; старые строки заведены в двух таблицах из четырёх.
20. **`202` не проверен** — B2.4 `CatalogSyncBoxTest.java:96`, B2.9 `:128`,
    B5.12 `SnapshotPassBoxTest.java:224`: `overlappingTicks`
    (`MarketDataBox.java:310-315`) выбрасывает ответы.
21. **B8.4** — `AccessContourBoxTest.java:95`: `isNotEqualTo(200)` пропускает
    `404`; контракт — `401`.

## Изоляция — форма

22. **Прямая запись состояний, у которых писатель в сервисе есть** (md:1488-1493
    разрешает только `CREATED`, `DELETED`, сдвиг статуса группы):
    `instruments.status` — `CandleLoadingBoxTest.java:216`,
    `SnapshotPassBoxTest.java:205`, `CatalogReadsBoxTest.java:36-37` (писатель —
    `InstrumentCatalogService.java:121`); `external_rules = null` —
    `CatalogSyncBoxTest.java:108`, `CatalogReadsBoxTest.java:51`; старые строки —
    `AbsentOutputsBoxTest.java:83-86`. `CLOSED` (B7.10) писателя не имеет, но в
    перечне md:1491 не назван.

## Детерминизм

23. **`barsAgo` пересчитывается внутри теста** — `CandleLoadingBoxTest.java`
    (B3.2, B3.3, B3.7, `punchHole`), `RepairAttemptsBoxTest.java:48/52`,
    `RepairCounterRestartBoxTest.java:144/148`: на границе часа якорь сдвигается
    на бар. Правка: якорь один на тест.
24. **Спорно** — `AppLog.java:60`: `List.copyOf` без синхронизации при
    асинхронной записи.

## Спорные и форма

- B1.6, B3.1, B3.6, B6.5 — «ни одного запроса площадки» проверено одним путём,
  нужен `connector.count()`; B2.2 — «у каждого» проверено у одного; B8.10 —
  пустой список против пустого; B10.1/B10.2 — держатся отсутствием Kafka в
  `pom.xml`; B8.12 — нет предусловия, что токен ушёл к коннектору;
  `OrderBookDepthBoxTest` — вторую половину клетки делает истинной сам вход.
- Кодстайл: `AppLog.java:65`, `:91`, `:95`; `Bodies.java:172`;
  `RepairAttemptsBoxTest.java:57`.
- Прежние пробы в корне пакета (семь классов без меток) снимать рано: три
  поведения не закрепляет ни одна клетка B — настоящая фаза при свежем входе
  (`MarketFeatureReadTest.java:116`), индекс по паре валют
  (`SnapshotCollectionTest.java:84`), пустой срок свежести
  (`MarketDataFreshnessTest.java:56`).

## Охват прохода

Все классы ящика и оснастка; чисто — `RulesCursorBoxTest`, `SyncDisabledBoxTest`,
`IndicatorDisabledBoxTest`, `PassWindowBoxTest`, `UnavailableIdentityBoxTest`,
`ConfigurationInputBoxTest`. Моков и подмены бинов нет.
