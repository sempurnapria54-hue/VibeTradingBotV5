# Фокус `test` шага 12: проход по юнитам `calc` и `fsm` торгового ядра

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по юнитам `calc` и `fsm` `trading-core` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением теста, кода и клетки;
заходом, пишущим отчёт, перепроверяются. Пути — от
`services/trading-core/src/test/java/com/example/tradingcore/unit/`; строки
кейсов — `.claude/tests/cases/trading-core-fsm.md` и `trading-core-calc.md`.
Тестов, закрепляющих дефект выхода, ERROR-состояния или терминала контрактом,
проход не нашёл; долговые клетки стоят под `@Tag("debt")` с ожиданием из дома
(исключение — находка 17).

## Находки — `trading-core-fsm`

1. **U3.8, U3.13 — невалидность, зелены вхолостую.** `fsm/DealTerminalContractsTest.java:117-121`,
   `:161-169`: у факта «число записано финализацией» нет носителя ни в `Deal`,
   ни в `DealContext`; гейт `emergencyTerminalContract` мерит только
   `riskProvenAbsent`, `computationAllowed` не читает — ни один дизъюнкт не
   предъявлен. Правка: U3.8 — под `debt` рядом с U3.11, клетки U3.8/U3.13 —
   «до F-1 зелено по построению».
2. **U3.2 — половина выхода.** `DealTerminalContractsTest.java:56-64`: не
   проверено, что переход в `CLOSED` не разрешён.
3. **Записи лога, объявленные выходом (`trading-core-fsm.md:87-91`), не
   проверяются — невалидность.** U7.2/U7.3 `DealActiveInputChecksTest.java:133-144`;
   U10.2, U10.6 `DealExitPendingPassTest.java:99-105`, `:139-146`; U19.5
   `TrancheEntryFinalizedPassTest.java:111-122`; U20.8
   `TrancheManagingPassTest.java:119-128`. Правка: `FsmLogCapture.attach(…)`.
4. **U10.8 — невалидность.** `DealExitPendingPassTest.java:163-171`: рёбра
   траншей не проверены, свод отдаёт пустой перечень.
5. **U10.7 — форма.** Там же `:150-159`: транши `baseDeal()` несут экспозицию —
   вход не соответствует клетке и повторяет U10.4.
6. **U21.1, U21.2 — невалидность.** `TrancheExitPendingPassTest.java:57-66`, `:70-75`:
   «защита не трогается» не проверено — лишняя `CANCEL_ALGO_ORDER` прошла бы.
   Правка: `containsExactly(CANCEL_ORDER_COMMAND)`.
7. **U9.8 — невалидность.** `DealActiveExitChecksTest.java:145-153`: транш один —
   «старшая причина» не различается с «первой».
8. **U15.7 — форма.** `TrancheStateMachinePassTest.java:183-201`: ложны два
   условия из трёх.
9. **U15.1 — форма.** Там же `:57-78`: `hasSize(6)` — размер перечня статусов,
   а не шесть боевых обработчиков.
10. **U2.2 — невалидность.** `DealRiskProvenAbsentTest.java:54-59`: порядок
    охраны не наблюдается на базовой сборке.
11. **U8.1 — невалидность.** `DealActiveCascadeReactionTest.java:53-59`: переход
    к отбору агрегатного шага не подтверждён.
12. **U11.2 — невалидность, слабая.** `DealErrorStateTest.java:75-81`: тропа
    добычи не отличена от аварийной.
13. **Клейм документа «моков ровно три» (`trading-core-fsm.md:120-128`) шире
    тестов — форма.** Подменены также `TrancheCascade`, `DealTrancheStateMachine`,
    `StrategyStepSelector`, в `TrancheWorkPassTest.java:75-77` —
    `StrategyActionOrchestrator`, `RetryPolicyService`,
    `DealActionStateDataService`. Доменные модели не мокаются.
14. **Кодстайл оснастки — форма.** `fsm/FsmFixture.java:434` — `value == null`;
    `calc/CalcLayerCompositionTest.java:169` — `!Modifier.isStatic`.

## Находки — `trading-core-calc`

15. **U5.4 — невалидность.** `calc/ReconciliationToleranceTest.java:112-120`:
    «допуск вырождается в пол» без парного входа внутри пола.
16. **U7.14 — половина выхода.** `TerminalBreakdownAndBenchmarkTest.java:186-212`:
    проверены два поля модели из четырёх.
17. **U10.13 (`debt`) — невалидность.** `AttachedParentClassTest.java:158-172`:
    только `doesNotThrowAnyException`; после починки F4 зелёным станет любой
    исход. Правка: назвать ожидаемую резолюцию в клетке и проверять её.
18. **U13.15 — невалидность.** `RetryBackoffDelayTest.java:175-187`:
    `isAfterOrEqualTo` зелен и у момента из поля строки.
19. **U4.13 — форма.** `ReconciliationPairsTest.java:233`: сообщение называет
    миграцию `V4`, колонка — в `V1__trading_core_baseline.sql:697`.
20. **Проба состава — детерминизм, мягкий.** `CalcLayerCompositionTest.java:50-76`:
    относительный `src/main/java` — вне каталога модуля падает.

## Охват прохода

Тридцать классов: девятнадцать `fsm`, одиннадцать `calc`. Не прочитаны:
`NetCloseAllowedTest`, `StepFreshnessGateTest` (кроме захвата лога),
`StrategyStepSelectionOrderTest`, `DealResultCrossCurrencyTest`.
