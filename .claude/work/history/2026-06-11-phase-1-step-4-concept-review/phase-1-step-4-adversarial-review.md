# Шаг 4 — агентское адверсариальное ревью (достройка пропущенного гейта)

## На какой вопрос отвечает этот файл

Каков зафиксированный исход адверсариальных ревьюер-фокусов по коду
шага 4 и какие находки как закрыты.

## Контекст

Шаг 4 ушёл в `DONE` мимо предписанного агентского ревью (на `CODE`
фокусы не прогонялись, на синке `divergence` не запускался). По
ужесточённому гейту (`roadmap-step-execution.md` §7) статус откатан в
`CODE`, прогнаны **независимые** фокусы (general-purpose субагенты, не
автор кода): `conventions`, `performance`, `disaster`, `divergence`.
Этот отчёт — зафиксированный исход (условие гейта `DONE`).

## Сводка находок

| Фокус | Blocker | Major | Minor |
|---|---|---|---|
| conventions | 0 | 3 | 2 |
| performance | 0 | 3 | 4 |
| disaster | 4 | 6 | 3 |
| divergence | — | 1 gating (C4) | 1 (C5) + добор |

Дефект гейта подтверждён эмпирически: независимый disaster-фокус нашёл
**4 деньги-блокера**, которые авторская + ручная проверка пропустили.

## Закрыто правкой кода (this session, компилируется)

- **[BLOCKER] D-B1** `OkxIntegrationService.closePosition` фабриковал
  `success=TRUE` без проверки `sCode` → позиция считалась закрытой при
  реджекте. **Фикс:** `closePosition` → `toOrderAck` (success из `sCode`).
- **[BLOCKER] D-B2** `verifyCode` доверял top-level `code`; бизнес-реджект
  в `"0"`-конверте / `code="1"` уходил в retry. **Фикс:** убран
  `verifyCode` с write-путей (place/cancel/placeAlgo/cancelAlgo/close);
  непустой `data` → ack по `sCode` (бизнес-исход, не throw); пустой `data`
  → `writeFailure` с реальными code/msg (транспорт, retryable).
- **[BLOCKER] D-B4** KillSwitch-отмены не best-effort: одна ошибка отмены
  рушила teardown + финальный безусловный close. **Фикс:**
  `cancelOrderBestEffort`/`cancelAlgoBestEffort` (catch+log per element).
- **[MAJOR] D-M2** Диспетчер `classify` был **перевёрнут**:
  `ControlledExchangeException` (терминал) → retryable; транспорт
  (`ExchangeIntegrationException`, не подтип Controlled) → non-retryable.
  **Фикс:** Controlled → `VALIDATION_ERROR` (терминал, FAILED); транспорт
  → `EXCHANGE_ERROR` (retryable).
- **[MAJOR] D-M3** Cancel/CancelAlgo/ClosePosition писали `closeReason`
  write-once + `SUBMITTED` **до** проверки ACK; `return failure` коммитил
  мутацию (без rollback, без retry). **Фикс:** проверка ACK **до**
  мутации; на реджекте — `failure` без advance. Код реджекта выровнен на
  `VALIDATION_ERROR` (D-m3).
- **[MAJOR] perf P-M1/P-M2** FK-индексы: `V6` —
  `ix_order_deal`/`ix_attached_algo_order_order`/`ix_algo_order_deal`;
  `V7` — `ix_balance_container`. (Миграции не применялись — БД не
  поднималась, правка безопасна.)
- **[MAJOR×3] conventions M1-M3** `.equals` → `Objects.equals` в
  `RefreshOrderExecutor` (×2) / `RefreshAlgoOrderExecutor`.
- **[MINOR] conventions m1** `"buy"/"sell"` → `Constants.Okx.SIDE_BUY/SELL`.
- **[MINOR] D-m3** несогласованность кодов реджекта — выровнено
  (`VALIDATION_ERROR`).

## Закрыто правкой доков (divergence)

- **[GATING] C4** `RefreshFillsExecutor.md` переобещал: код матчит fills
  только с ordinary `Order` (не AlgoOrder/Position). **Док сужен** до
  Order; AlgoOrder/Position fill-matching → forward-debt.
- **[MINOR] C5** `mapping/Order.md`: `mmp_canceled` closeReason в коде
  `null` (док: «UNKNOWN, можно расширить»). Помечено.

## Закрыто осознанным принятием (с обоснованием)

- **[MINOR] conventions m2** `getRequiredByInternalId` (Order/AlgoOrder
  DataService) формально не вызывается. **Принято** как near-term
  scaffolding под step-6/7 оркестрацию / lookup'ы; удаление+возврат —
  чистый churn. Не блокер.
- **[MAJOR] D-M4** `RefreshPosition`: пустой positions-ответ → CLOSED от
  одного чтения (нет evidence-cycle как у order/algo). **Соответствует
  докам** (`RefreshPositionExecutor.md`: close позиции = отсутствие в
  единственном `GET /account/positions`; асимметрия с order/algo — у
  позиций один эндпоинт). Корроборация (защита от транзиентных пустых
  ответов) — **форвард-вопрос дизайна**, не код↔спека-баг. Занесено
  форвард-долгом.
- **[MINOR] D-m1/D-m2** clock-skew подписи; лишние циклы при пустом
  clOrdId-эхе — отмечены, не блокеры.
- **[MINOR×3] perf minors** saveAll-батчи (attached/balances), churn
  delete+insert баланса, save-в-цикле RefreshFills — оптимизации, не
  блокеры; форвард-долг.

## Вынесено форвард-долгом (gating для step-6/7, не для DONE шага 4)

- **[BLOCKER] D-B3** SUBMIT recovery-by-clientId: краш между `placeOrder`
  и сохранением `externalId` → дубль ордера при ресабмите. **Латентен в
  шаге 4** (нет авто-реплея SUBMIT — оркестратор/FSM это step-6/7), но
  обязателен **до** включения авто-реплея. Требует `getOrder`
  null-on-not-found (OKX `51603`). → `backlog.md`, **gating step-6/7**.
- **[MAJOR] D-M1** нет concurrency-guard вокруг исполнения команды
  (двойной SUBMIT при перекрытии тика/ручного триггера). Сериализация/
  оптимистик-лок — у оркестрационной петли (step-6/7). → `backlog.md`,
  **gating step-6/7**.
- **[MAJOR] D-M5/R5** fills-пагинация назад по `billId` + отсутствующее
  звено `orders-history-archive` в order-цикле. Недобор fills искажает
  PnL (step 7). → `backlog.md`.
- **[MAJOR] perf P-M3** `getRequiredById` грузит attached даже для
  submit/cancel (которым не нужно). Оптимизация → `backlog.md`.

## Вывод

Все blocker'ы и gating-находки, актуальные **в пределах шага 4**, закрыты
правкой (код компилируется) или доком. Латентные деньги-риски (D-B3,
D-M1), материализующиеся только с оркестрационной петлёй step-6/7,
вынесены форвард-долгом как **gating для step-6/7** (не для DONE шага 4).
`divergence` пройден (add/change/remove реконсилированы). Гейт `DONE`
по новому правилу — закрываемо после пост-хок концепт-гейта (§6a).
