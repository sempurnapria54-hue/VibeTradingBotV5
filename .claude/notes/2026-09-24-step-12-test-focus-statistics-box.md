# Фокус `test` шага 12: проход по группам B7-B10 ящика `statistics`

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по группам B7-B10 чёрного ящика `statistics` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением кода; заходом, пишущим
отчёт, перепроверяются. Группы B1-B6 ящика `statistics`, ящик `audit` и юниты
приёма этим проходом **не покрыты** — их сводка не вернулась. Пути — от
`services/statistics/src/test/java/com/example/statistics/box/`; строки
кейсов — `.claude/tests/cases/statistics.md`.

## Находки

1. **Полночь UTC — детерминизм, все двенадцать классов.** `StatisticsBox.java:717-746`:
   `midnightDaysAgo`, `day`, `bucket` читают `LocalDate.now(UTC)` на каждом
   вызове, проход берёт `today` в момент такта
   (`services/statistics/src/main/java/com/example/statistics/domain/jobs/AggregateRecomputeJob.java:76`).
   Полночь между расстановкой и `recompute()` сдвигает сутки: роняет B7.1
   (`NarrowedRecomputeWindowBoxTest`), B7.20
   (`OverlappingRecomputeBoxTest.java:181/203`), всякий `rowOf(…, n)`. Цепочка
   идёт ночью — полночь UTC это 03:00 по Москве. Правка: якорь даты один на
   тест плюс охрана «до полуночи меньше минуты».
2. **B8.19 — невалидность и детерминизм.** `DealGrainArithmeticBoxTest.java:717`:
   пояс JVM и сессии не задан — на машине с поясом UTC нарезка по
   `systemDefault()` прошла бы. Правка: сдвинутый `-Duser.timezone` у прогона
   ящика либо предусловие ненулевого смещения.
3. **B8.1, B8.3, B8.4, B8.15 — половина денежных сумм.** Там же `:184`, `:250`,
   `:279`, `:617`: не проверены `winResultSum`, `plannedRiskSum`, `rSum`, у B8.1
   — `resultUnavailableDeals`, `rDenominatorDeals`. Правка: все десять полей,
   как `moneyFieldsOf` в B8.7.
4. **B9.8 — ослабление.** `IncidentGrainCountersBoxTest.java:440`:
   `Collectors.toSet()` схлопывает второй `@Scheduled` на том же методе.
5. **B7.18, B7.16 — форма.** `RecomputePassShapeBoxTest.java:179` — «ключа
   конфигурации нет» не проверено; `WidenedRecomputeWindowBoxTest.java:126` —
   счёт строк `cron:` в `application.yaml` не видит `fixedRate` и зашитый CRON.
6. **B10.1, B10.13 — ослабление.** `AggregateReadBoxTest.java:209-210`, `:465-466`:
   `containsKeys` проходит при `continuityClaimable: null`.
7. **`givenReceptionStateRows` — детерминизм с условием.** `StatisticsBox.java:367`:
   счёт всей таблицы приёма при общей базе; тик `fixedDelay` раз в час живого
   кэшированного контекста вставит строку своей группы. Задевает B1.4, B9.6,
   B10.1, B10.12, B10.13. Правка: фильтр `group_id = consumerGroup()`.
8. **B8.2, B8.9, B8.11 — форма.** `DealGrainArithmeticBoxTest.java:245`, `:444`,
   `:499`: отрицание перечнем имён вместо полного перечня колонок.

## Охват прохода

Двенадцать классов групп B7-B10; чисто по прочим осям — отказы B10.2-B10.7,
тенант B10.11-B10.12, счёт `pg_stat_statements` B7, изоляция
`NestedGrainKeysBoxTest`.
