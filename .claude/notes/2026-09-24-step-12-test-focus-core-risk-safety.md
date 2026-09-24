# Фокус `test` шага 12: проход по юнитам риска и защитных ступеней ядра

## На какой вопрос отвечает этот файл

Какие находки дал проход фокуса `test` по юнитам риска и защитных ступеней `trading-core` 2026-09-24?

## Статус

Вход отчёту фокуса `test`, а не отчёт. Проход — субагент захода 146; вернулся
после конверта сессии. Находки проверены им чтением теста и продуктового кода;
заходом, пишущим отчёт, перепроверяются. Пути ниже — от
`services/trading-core/src/test/java/com/example/tradingcore/`; строки кейсов —
`.claude/tests/cases/trading-core-risk.md` (U11-U29) и
`.claude/tests/cases/trading-core-safety.md` (U1-U14).

## Находки

1. **U22.1, U22.7, первая половина U22.8 — невалидность, торгово значимая.**
   `unit/risk/ReactionUnderLiveRiskTest.java:35-44`, `:94-102`, `:104-113`.
   Кейс (`trading-core-risk.md:670`) требует, чтобы стадия сама означала живой
   риск. Вход — временные коды карв-аута (`RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
   `INSTRUMENT_SAFETY_HOLD`): ветка «до живого риска» даёт тот же `SKIP_ACTION`,
   и резолвер, не читающий стадию, проходит. Правка: бессрочные коды карв-аута
   (`STOP_DISTANCE_BELOW_FLOOR`, `SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET`) —
   документ требует этого в шапке U24 (`:699-702`).
2. **U6.12 (`debt`), U6.13 — форма, торгово значимая.**
   `unit/safety/AbsorptionAndRetryTest.java:250-258`, `:261-267`. Кейс
   (`trading-core-safety.md:364-365`) различает входы стоящей строкой ручного
   ключа; тесты — кодом сигнала, строка не подаётся. `react(..., true)` зовёт
   только `ManualHaltService:95` с `MANUAL_HALT_REQUESTED`, поэтому вход U6.13
   недостижим, а долг U6.12 позеленит неверная правка. Правка: оба —
   `MANUAL_HALT_REQUESTED`, различие — стабом стоящей строки у `reports`.
3. **U8.5 — невалидность.** `unit/safety/KillSwitchAggregationTest.java:113-123`:
   кейс (`:402`) — подтверждена последняя сделка; тест подаёт `ok()` →
   `notCompleted()`, то есть первую. Правка: обратный порядок ответов.
4. **U11.5 — невалидность (тавтология).** `unit/safety/BlindPassLimitTest.java:128-135`:
   признак вычисляется в `AnomalyJob.run/observe`, гейту подаётся готовым
   булевым. Правка: перенести в тест `AnomalyJob` — полный срез плюс бросающий
   детектор дают `passGate.apply(false, …)`.
5. **U10.4, U10.5 — невалидность и детерминизм.**
   `unit/safety/AnomalyHysteresisTest.java:134-148`, `:150-164`: сравнение с
   `OffsetDateTime.now()` в одну сторону; окно, сдвинутое в прошлое, проходит
   оба. Правка: `now` до и после вызова, обе границы в вилке.
6. **U15.6, U15.10 — невалидность.** `unit/risk/CatastrophicNotionalCeilingTest.java:99-107`,
   `:144-155`: только «ровно на потолке — отказа нет»; нога без вклада тоже
   зелена. Документ требует границу трижды (`:144-146`). Правка: парная
   проверка «на волос ниже → `DEAL_NOTIONAL_EXCEEDED`», как у U15.5 и U15.8.
7. **U6.4 — половина выхода.** `AbsorptionAndRetryTest.java:104-114`: проверено
   только `open`, доведение по статусам — нет. Правка: `InOrder`, как в U5.5.
8. **U11.8 — форма.** `BlindPassLimitTest.java:164-173`: совпадение ключа не
   проверено. Правка: `raisedSignal()` равен
   `HoldSignal.exchangeAccountJournal(ANOMALY_PASS_INCOMPLETE)`.
9. **U12.10 — половина выхода.** `ManualHaltRaiseTest.java:152-161`: не проверен
   вызов `holdService.raise`. Правка: `verify`, как в U12.9.
10. **U13.15 — половина выхода.** `ManualHaltClearTest.java:237-246`: не
    проверено применённое снятие. Правка: `verify(accounts).clearRung(ACCOUNT_ID, HOLD, ACTIVE)`.
11. **U7.7 — форма.** `ReportTerminalGateTest.java:148-161`: порядок
    `enforceHardRung` по сделкам 41 и 42 не проверен. Правка: `InOrder` по `statusEdges`.
12. **U3.1 — половина входа.** `HoldRoutingTest.java:53-65`: «любого радиуса» —
    подан только инструментный. Правка: добавить `HoldSignal.exchangeAccount(CODE)`.
13. **U19.3 — форма.** `ProtectionRemovalTest.java:76`: `contains("5")` совпадает
    с «15». Правка: проверять строку пояснения целиком.
14. **U28.7 — форма.** `RiskNumbersCurrentAndWriteTest.java:113-124`: защиты
    `[2950, 2910]` — «берётся последняя» тоже зелена. Правка: порядок `[2910, 2950]`.
15. **U28.12, U28.13 — половина выхода.** `RiskNumbersCurrentAndWriteTest.java:206-213`:
    проверено одно число из четырёх. Правка: остальные три пусты.
16. **U29.12 — форма.** `RiskLayerAbsenceTest.java:182-190`: вход с пустым
    исходом утечки состояния не увидит. Правка: блокирующий вход, например U14.4.
17. **U26 — форма.** `ActionRiskGateTest.java:43` мокает `RiskValidator`, а
    документ (`trading-core-risk.md:123-133`) объявляет подменённым только
    резолвер. Правка: объявить подмену в документе либо снять её.

Изоляция уровня соблюдена: контекст не поднимается, доменные модели не
мокаются; `Thread.sleep` и случайности нет.

## Охват прохода

Прочитаны safety U1, U3, U5-U8, U10-U14 и risk U6, U8, U11, U13-U15, U17, U19,
U22, U26, U28, U29. **Не прочитаны:** `HoldSignalFactoryTest` (сверен только
механически), `ShutdownReasonResolveTest`, `AnomalyReportDedupTest`,
`ActRiskAndNotionalTest`, `CeilingBaseTest`, `CeilingsWithoutActTest`,
`CodePermanenceTest`, `CollapseWindowTest`, `CumulativeCeilingTest`,
`EntryAnchorTest`, `FeeRateNeedTest`, `LiveRiskPredicateTest`,
`PrecheckInputGateTest`, `ReactionBeforeLiveRiskTest`,
`ReactionGraphIncompleteTest`, `RiskCreatingEntryProtectionTest`,
`RiskNumbersLegSelectionTest`, `StopDistanceFloorTest`,
`TradingConstraintsTest`, `VerdictAggregationTest`. Ящик ядра, юниты
`calc`/`fsm` и прочие сервисы этим проходом не покрыты.
