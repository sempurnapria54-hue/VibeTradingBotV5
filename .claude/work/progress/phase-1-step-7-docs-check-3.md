# DOCS_CHECK_3 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Каков исход подтверждающего прогона концепции (`concept-review` ×2 +
`trading-review`) после `GAPS_CLOSE_2` — чисто ли доспецифицированы стадии 1-2
под CODE, или остались пробелы.

## Контекст

- **Под-шаг:** `DOCS_CHECK_3` (процесс `roadmap-step-execution.md`),
  подтверждающий прогон после материализации носителей/механики на `GAPS_CLOSE_2`.
- **Прогон:** три независимых ревьюер-субагента — concept линза-1 (механика/
  командный слой/финализация/handler'ы/процессы), concept линза-2 (модели/mapping/
  native/персистентность, field-level), trading (корректность материализованной
  механики). CC верифицировал несущие атрибуции грепом/`ls` (V1-V5): отсутствие
  fee-полей и **отброшенный `groupId`** в `mapping/InstrumentExternalRules.md`,
  отсутствие `OkxTradeFeeResponse`, отсутствие trade-fee в
  `InstrumentExternalRulesSyncJob`; сброс native `pnl`/`fee` в `OkxAccountBillResponse`;
  неполный finalization-список в `risk-validator-scope`; отсутствие
  `MARK_EMERGENCY_CLOSED` в `lifecycles/DealFinalizationState`; `Instant` в
  `PositionCloseResult`.

## Охват

Все носители/механика `GAPS_CLOSE_2` + смежное: командный слой (`ServiceCommand`,
`DealFinalizationCommandFactory`, `DealFinalizationState` + lifecycle), 6
executor'ов + `ServiceCommandExecutor`, handler'ы (`ExitPendingHandler`,
`ErrorHandler`), процесс `deal-management.md`, `lifecycles/Deal.md`, `Deal.md`;
носители (`OkxPositionsHistoryResponse` ↔ `PositionCloseResult` ↔ `Deal`;
`OkxAccountBillResponse` ↔ `DealCashFlow` model+mapping ↔ `account-bills.md`);
N9-путь (`InstrumentExternalRules` ↔ `mapping/InstrumentExternalRules` ↔
`trade-fee.md` ↔ `SizeCalculator`/`RiskValidator` ↔ `InstrumentExternalRulesSyncJob`);
test-план §AG1.5; decision'ы. Грунт trading — дистиллят корпуса.

## Стадия остановки

**Прошёл все стадии; НЕ чист.** Ядро механики (N6/N7/N8/N12) **проведено полно и
согласованно** (подтверждено обеими concept-линзами: enum=18, N7 tx-связка везде,
N8 терминал с владельцем, refresh-команды с эмитентами/executor'ами). Остаток —
**один гейтящий доковый пробел** (N9 fee-wiring доспецифицирован не до конца),
**один рантайм/докогейт разбивки** (гранулярность bills) + известный рантайм-гейт
N11, и хвост гигиены/форварда.

## Пробелы

Нумерация `H#`. Ссылки на находки субагентов: `F-A#` (mechanics), `F-B#` (models),
`F-T#` (trading).

### H1 — N9 fee-wiring доспецифицирован наполовину [F-B1]. Тип: name-level + doc↔doc. **ГЕЙТИТ CODE.**

Модель `InstrumentExternalRules` получила поля `externalTakerFeeRate`/
`externalMakerFeeRate` + аксессоры (`GAPS_CLOSE_2`), но **слой добычи ставки не
заведён**:
- `mapping/InstrumentExternalRules.md` — **нет маппинга** fee-полей (grep пуст; файл
  stale 2026-06-20); маппит только `InstrumentOkxResponse` (`/public/instruments`),
  а `trade-fee` — другой эндпоинт/DTO.
- Native `OkxTradeFeeResponse` **не существует** (`ls` подтвердил); `feeGroup[].taker`/
  `maker` нигде не инвентаризованы.
- `InstrumentExternalRulesSyncJob.md` — stale (2026-06-21): источник назван **только**
  `/public/instruments`; «дочитывание `trade-fee`» (которое требуют модель +
  `pnl-finalization-mechanics.md` реш.4) не описано.
- **Критично:** `mapping/InstrumentExternalRules.md` §«Не маппимые» **явно отбрасывает
  `groupId`**, а резолв `feeGroup` для SWAP завязан именно на `groupId` (флэт
  `maker`/`taker` в офдоке deprecated для SWAP) → **CODE не из чего резолвить нужную
  группу ставок**. Итог: `takerFeeRate()` останется null → прогноз комиссии в
  `SizeCalculator`/`RiskValidator` молча выпадет.

Доки должны задать: native-инвентарь `trade-fee` (или секцию в
`mapping/InstrumentExternalRules`), маппинг `feeGroup[].taker/maker`→`externalTakerFeeRate/
externalMakerFeeRate` **по `groupId`** (снять отброс `groupId`), обновить
`InstrumentExternalRulesSyncJob` вторым чтением `trade-fee`, зафиксировать boundary-снапшот.

### H2 — гранулярность bills ломает разбивку `DealCashFlow` [F-T2]. Тип: неотвеченный вопрос (рантайм) + докорешение. **ГЕЙТИТ корректность разбивки** (не заголовочного числа).

Маппинг берёт `amount ← balChg` и **выбрасывает** native `fee`/`pnl`
(`OkxAccountBillResponse:55-57`, «знак несёт уже `balChg`»). Для суммы-сверки
безопасно (Σ`balChg` = net при любой гранулярности). Но если OKX эмитит
**комбинированный** trade-bill (`balChg = pnl + fee`, не отдельные записи), весь
`pnl+fee` уходит в одну категорию → **`TRADE_FEE` недосчитан**, `REALIZED_PNL`
пересчитан. Разбивка (единственный смысл `DealCashFlow`) искажена; ломает N13-вход
«комиссия-в-R / funding-в-expectancy» для фазы 2 [Carver ST гл.12 с.233-235;
Kaufman гл.1 «все издержки учтены?»]. Доки/рантайм должны: верифицировать
гранулярность bills (**та же фикстура §AG1.5**: закрытая позиция → инспекция
bill-записей); если отдельного fee-bill нет — **вернуть native `fee` в used** и
резолвить `TRADE_FEE` по нему, не по знаку `balChg`.

### H3 — N11: инвариант агрегации positions-history [подтверждён F-T (Проверено), concept B]. Тип: рантайм-гейт (не доковый). **ГЕЙТИТ корректность числа, до CODE.**

Уже трекается: план §AG1.5 (⏳ PENDING). Trading подтвердил — **стоящий блокер**:
заголовочное число целиком зависит от непроверенного допущения агрегации
partial-close; смещение коррелирует с partial-exit/grid (целевое семейство бота), знак
любой [Tharp гл.6 с.158-159]. План адекватен; предложение (не дыра): добавить
**промежуточное чтение** positions-history после partial-close до full-close —
охарактеризовать момент финализации. **Доково закрыто; открыт рантайм-факт.**

### H4 — гигиена командного слоя (5 рассинхронов от параллельной работы) [F-A1..F-A5]. Тип: несогласованность/name-level. **НЕ гейтит.**

- **F-A1:** `lifecycles/DealFinalizationState.md` не упоминает `MARK_EMERGENCY_CLOSED`/
  `MarkDealEmergencyClosedExecutor` (owner-list стар относительно модели; матрица
  статусов type-agnostic → CODE не блокируется).
- **F-A2 (содержательнее):** `risk-validator-scope.md:43-44` даёт **исчерпывающий**
  список finalization-команд без RiskValidator — **без `MARK_DEAL_EMERGENCY_CLOSED`**,
  при том что `MarkDealEmergencyClosedExecutor` ссылается на этот док как на авторитет.
  Добавить команду в список.
- **F-A3:** мёртвые ссылки на удалённый `RefreshFillsExecutor.md` в
  `result-profit-source.md:110` и `refresh-evidence-cycle-ownership.md:76`.
- **F-A4:** cross-ref-списки finalization-executor'ов не дополнены
  `MarkDealEmergencyClosedExecutor` (`ServiceCommandExecutor`, `DealFinalizationState`
  §Связи, `deal-finalization-state-materialization` §Связи).
- **F-A5:** устаревшая строка refresh-набора (CMD-Q3-слепок с `REFRESH_FILLS`) в
  `refresh-evidence-cycle-ownership.md:84` (контекстуализирована update-нотой рядом).

### H5 — `Instant` vs `OffsetDateTime` в снапшоте [F-B2]. Тип: несогласованность. **НЕ гейтит** (транзитное поле, в `Deal` не пишется).

`PositionCloseResult.externalUpdatedAt` = `Instant`, тогда как конвенция проекта
(`Balance` mapping, `Auditable`, `DealCashFlow.externalTs`) — `OffsetDateTime`. Один
`uTime` → два типа. Выровнять на `OffsetDateTime` (или явно обосновать `Instant`).

### H6 — форвард-смещение ожидаемости: null-drop не нейтрален [F-T1]. Тип: торговая находка, **cross-cutting/форвард (не гейтит)**.

N8 исключает null-EMERGENCY из R-выборки как unknown. Но пропуск **outcome-коррелирован**
(null возникает на аварийной тропе, коррелирующей с крупными убытками) → простой drop
**не устраняет** смещение, а воспроизводит его омиссией: «вычислимая» выборка смещена к
благоприятным исходам → ожидаемость завышается [Vince гл.5 с.63; Tharp формула 6-2,
гл.6 с.158-159]. Проектное «пометки (AnomalyReport/лог) достаточно» — **торгово неверно**.
Форвард (владелец — шаг ожидаемости/фаза 2): нужен один из — (a) **добор числа** до
истечения positions-history (3 мес; тот же эндпоинт, «недоступен» обычно временно);
(b) worst-case импутация для ожидаемости; (c) метрика счёта дыр рядом с ожидаемостью.
Само решение «не ноль» верно; корректировать надо утверждение о достаточности пометки.

### H7 — якорь epsilon сверки [F-T3]. Тип: провизор (уточнение). **НЕ гейтит.**

Epsilon `max(0.01 USDT, 0.5%·|net|)` привязан к **|net|**, а охраняемая ошибка — по
природе ошибка композиции разбивки, чей масштаб = **валовые потоки** (Σ|amount|), не net.
При большом net `0.5%·|net|` глотает реальную дыру; при net≈0 с валовыми потоками
относительный член вырождается. Переякорить на Σ|amount| (или fixed per-flow tolerance).
Уже помечено провизорным.

### H8 — допущение OKB-комиссии [F-T4]. Тип: инвариант конфигурации (принять/зафиксировать). **НЕ гейтит.**

Если аккаунт платит комиссию в OKB (скидка), комиссия вне USDT `realizedPnl` →
заголовочное `resultProfit` **завышает** net на OKB-комиссию; cross-ccy guard помечает,
но число не корректирует. Зафиксировать инвариант «комиссии в settle-ccy (USDT), не OKB»
либо принять как известный провизор [Kaufman гл.1 — полнота издержек].

## Проверено-ОК (подтверждено обеими concept-линзами + trading)

- **N7** (FINALIZE_EXIT пишет число на `Deal` в tx с COMPLETED; MARK_CLOSED ассертит) —
  согласовано **везде**; рестарт/идемпотентность консистентны; «MARK_CLOSED пишет число» /
  «стейджит на runtime graph» нигде не осталось.
- **N8** (владелец `MARK_DEAL_EMERGENCY_CLOSED` проведён enum/factory/модель/эмитент/
  executor/lifecycle; best-effort null-маркер «не ноль») — терминал больше не «без
  владельца»; торговое ядро «не зануляется» корректно.
- **PositionCloseResult-путь и DealCashFlow-путь** — model↔mapping↔native↔персистентность
  взаимно-согласованы field-level (имена/типы/enum/схема `deal_cash_flows`).
- **N6 refresh-команды** — эмитенты/executor'ы/within-command пагинация согласованы.
- **N9 seam** (чтение ставки через `CalculationContext.instrumentExternalRules`) валиден —
  дефект не в seam, а в **наполнении** поля (H1).
- **`CashFlowCategory`** покрытие для линейного SWAP адекватно; `REALIZED_PNL` не двоится с
  заголовком (независимый positions-history net; категория — лишь слагаемое суммы-сверки).
- **taker в сайзинге** консервативен; **N13** разделяющий довод торгово корректен, funding
  realized-захвачен (не гейтит).

## Сводка

**8 находок (H1-H8).** **Гейтят — 3:** H1 (N9 fee-wiring, **доковый**), H2 (гранулярность
bills — доко-решение + рантайм), H3/N11 (рантайм, уже трекается). **Не гейтят — 5:**
H4 (гигиена ×5), H5 (`Instant`), H6 (форвард-смещение null-drop), H7 (epsilon-якорь),
H8 (OKB-инвариант).

- **Только докогейт для `GAPS_CLOSE_3`** — **H1** (доспецифицировать fee-путь:
  native/mapping/`groupId`/sync-job) + H2-докочасть (вернуть native `fee` в used под
  резолв `TRADE_FEE`) + гигиена H4/H5. Владелец — `integrator` (native/mapping/sync,
  H1/H2) + `knowledge-curator` (гигиена H4/H5).
- **Рантайм-гейты до CODE** (не доки): **N11** (§AG1.5) + **H2-рантайм** (гранулярность
  bills — **та же фикстура**). Владелец — `integrator`/`tester` (source-api контур).
- **Форвард/провизор:** H6 (фаза 2 — с исправлением утверждения «пометки достаточно»),
  H7 (переякорить epsilon), H8 (инвариант конфигурации).
- **Торговый синтез (Lens C):** три независимых механизма смещают левый хвост
  **оптимистично** согласованно — N8 null-drop (H6), N11 недосчёт агрегации (H3),
  опущенный гэп-проскок (TR2). Каждый учтён/отложен, но их **конвергенция** усиливает риск
  завышения ожидаемости — держать единым форвард-фокусом фазы ожидаемости.

**Исход: `DOCS_CHECK_3` НЕ чист → `GAPS_CLOSE_3`** (узкий: H1 fee-wiring + H2-доко +
гигиена; форвард-флаги H6-H8). После закрытия — `DOCS_CHECK_4`. **Отдельно от доков:**
рантайм-верификация N11 + H2 (source-api фикстура) — обязательна до `CODE`.
