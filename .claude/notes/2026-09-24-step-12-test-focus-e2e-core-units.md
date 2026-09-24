# Фокус `test` шага 12: проход по сквозному набору и остатку юнитов ядра

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` 2026-09-24 по сквозному набору `tests/` и по юнитам ядра, которые прошлый проход не прочитал?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проходы — субагенты захода 147; вернулись
после конверта сессии. Находки проверены ими чтением теста и продуктового кода;
заходом, пишущим отчёт, перепроверяются. Пути юнитов — от
`services/trading-core/src/test/java/com/example/tradingcore/`; строки кейсов —
`.claude/tests/cases/<документ>.md:<строка>` (`risk`, `safety`, `fsm`, `calc` —
`trading-core-<…>.md`).

## Находки — сквозной набор

**Не вернулись.** Раздел сквозного набора проход не заполнил; набор `tests/` и
`smoke-live` проходятся заново.

## Находки — юниты ядра

1. **U10.2 (risk) — невалидность, торгово значимая.**
   `unit/risk/ActRiskAndNotionalTest.java:277-282` (risk:411): защитное действие
   размером 1 против потолков под размер 10 — пустой перечень и при сломанной
   классификации risk-weakening; «перенос защиты» не подан. Правка: размер 10 и
   вход с переносом.
2. **U4.5 (risk, `debt`) — долг позеленеет от неверной правки.**
   `unit/risk/EntryAnchorTest.java:78-87` (risk:301): пустой якорь тоже гасит
   проверку стороны (`RiskValidator:748`). Правка: парный вход с уровнем 3100.
   Тот же класс — **U4.2** (`:53-59`).
3. **U18.11, U18.13 (risk) — невалидность.** `unit/risk/CeilingsWithoutActTest.java:145-149,
   157-168`: соседняя ветка даёт тот же пустой перечень. **U18.14 — пробел
   покрытия:** перестановка охран `RiskValidator:445/448` не роняет ничего.
4. **U10.4 (risk) — невалидность.** `ActRiskAndNotionalTest.java:292-299`:
   пустая ставка, прочитанная нулём, даёт тот же исход.
5. **U25.11 (risk) — невалидность.** `unit/risk/CodePermanenceTest.java:113-126`:
   второй носитель бессрочности не ищется у `RiskBlockResolver`, который её читает.
   **U25.6-U25.8 — пробел дока:** `docs/components/models/RiskCheckResult.md`
   §«Бессрочность отказа» не называет `INSTRUMENT_SAFETY_HOLD`,
   `RISK_CREATING_UNDER_COLLAPSE`, `PROTECTION_COVERAGE_REDUCED`, продукт держит их
   временными. **U25 — пробел покрытия:** коды с объявленной бессрочностью не
   прибиты.
6. **U9.2 (risk) — половина выхода.** `unit/risk/CeilingBaseTest.java:160-166`:
   наблюдается один потолок из четырёх.
7. **U4.6, U9.12 (safety) — половина выхода.** `ShutdownReasonResolveTest.java:228-239`,
   `AnomalyReportDedupTest.java:248-267`. **U9.16 — форма** (`:311-323`).
8. **U23.19 (fsm) — невалидность.** `unit/fsm/StrategyStepSelectionOrderTest.java:257-264`
   (fsm:754): тело совпадает с U23.14, биржевая ступень не подаётся.
9. **U24.8, U24.9 (fsm) — невалидность, ось покрытия не различена.**
   `unit/fsm/StepFreshnessGateTest.java:176-197` (fsm:774-775): `branchCovered`,
   всегда читающий `allTranchesCovered()`, проходит всю группу U24.
10. **U24.1 (fsm), U2.1, U2.11 (calc) — невалидность, соседняя ветка даёт тот же
    исход.** `StepFreshnessGateTest.java:99-106`;
    `unit/calc/DealResultCrossCurrencyTest.java:52-63, 185-199`. **U2.6, U2.7 —
    половина выхода** (`:116-142`).
11. **U24.13, U24.7, U23.17 (fsm) — половина выхода.** Эскалация, уровень лога
    `WARN` (у `FsmLogCapture.levels()` вызовов нет), «пропущен второй» не проверены.
12. **U4.7, U23.18 (fsm) — пробел дока.** `NetCloseAllowedTest.java:85-91`: вход
    не производит писатель (`DealContextService.java:151-156`);
    `StrategyStepSelectionOrderTest.java:246-255`: «чужую надобность» выразить
    нечем, тест сменил предмет.

Изоляция и детерминизм у этих файлов соблюдены: контекст не поднимается,
доменные модели не мокаются, `now()` в U9.8 взят с обеих сторон.

## Охват прохода

Прочитаны целиком: `HoldSignalFactoryTest`, `CumulativeCeilingTest`,
`CollapseWindowTest` (чисто), `ActRiskAndNotionalTest`, `EntryAnchorTest`,
`CeilingsWithoutActTest`, `CeilingBaseTest`, `CodePermanenceTest`,
`ShutdownReasonResolveTest`, `AnomalyReportDedupTest`, `NetCloseAllowedTest`,
`StrategyStepSelectionOrderTest`, `StepFreshnessGateTest`,
`DealResultCrossCurrencyTest`. **Не прочитаны:** `FeeRateNeedTest`,
`LiveRiskPredicateTest`, `PrecheckInputGateTest`, `ReactionBeforeLiveRiskTest`,
`ReactionGraphIncompleteTest`, `RiskCreatingEntryProtectionTest`,
`RiskNumbersLegSelectionTest`, `StopDistanceFloorTest`, `TradingConstraintsTest`,
`VerdictAggregationTest`; сквозной набор целиком.
