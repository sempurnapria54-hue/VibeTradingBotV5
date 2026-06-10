# DOCS_CHECK_3 — шаг 4 фазы 1 (Команды и их жизненный цикл, `ServiceCommand`)

## На какой вопрос отвечает этот файл

Что нашёл подтверждающий прогон сквозной проверки концепции шага 4 после
F1/F2 (владение evidence-cycle) и CMD-Q3 (подрезка refresh-набора).

## Контекст прогона

- **Под-шаг:** `DOCS_CHECK_3` (подтверждающий, после `GAPS_CLOSE_2`:
  F1/F2 + CMD-Q3).
- **Фокус:** легли ли чисто F1 (обход evidence-cycle внутри исполнителя),
  F2 (`REFRESH_FILLS` 3d→3m), CMD-Q3 (refresh-набор — по одной команде на
  сущность, 4 bulk сняты); рябь от ~25 правок; новые doc↔doc
  несогласованности / битые ссылки / dangling-команды.
- **Охват:** command-layer шага 4 + затронутые правкой step-6/7 handlers,
  lifecycles, контракты, mapping, правило risk-scope.

## Стадия остановки

Стадия 2 (компоненты + модели) — прошёл все стадии. Стадии 0-1 без
изменений (механика/процессы правками не затронуты — правки структурные:
владение циклом + подрезка набора).

## Проверка закрытий `GAPS_CLOSE_2`

| Находка | Статус |
|---|---|
| **F1** — владение evidence-cycle | **Закрыта.** `command-lifecycle` (атомарность ≠ один HTTP-запрос), `ServiceCommandExecutor` + `RefreshOrder/AlgoOrder/FillsExecutor` (within-command обход + терминал на исполнителе), `mapping/Order`+`AlgoOrder` (цикл атрибутирован исполнителю), решение `refresh-evidence-cycle-ownership.md`. Согласовано. |
| **F2** — `REFRESH_FILLS` 3d→3m | **Закрыта.** `RefreshFillsExecutor` + `fills.md`: эскалация внутри команды; отдельной `REFRESH_FILLS_HISTORY` нет; архив 3m+ → `OKX-Q2`. |
| **CMD-Q3** — refresh-набор | **Закрыта** (steer). Набор — 5 команд (`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`, `REFRESH_POSITION`, `REFRESH_BALANCE`, `REFRESH_FILLS`); 4 bulk сняты из enum'а. |

## Проверка ряби (grep-верификация)

- **REFRESH-токены по `docs/`:** все упоминания — ровно 5 выживших команд.
  4 снятые (`REFRESH_PENDING_ORDERS` / `REFRESH_ORDER_HISTORY` /
  `REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY`) встречаются **только**
  в трёх нотах-о-снятии (`ServiceCommand` §набор, `ServiceCommandExecutor`
  §note, `refresh-evidence-cycle-ownership` §Следствия) + `REFRESH_FILLS_HISTORY`
  — только в ноте «такой команды нет» (`fills.md`). **Dangling-ссылок нет.**
- **Enum:** `ServiceCommand.md` — 19 значений, набор задокументирован нотой.
- **Handler command-lists:** Precheck / EntrySubmitted / EntryFinalized /
  ProtectionSwitched / Managing / ExitPending / Error — все на per-entity
  `REFRESH_ORDER` / `REFRESH_ALGO_ORDER`; списки связны, дублей нет.
- **Lifecycle/contract/mapping:** Order §«Exchange facts» (pending/history →
  под-пункты `REFRESH_ORDER`), Position recovery-контур, OKX-контракты
  (pending/history → «звено цикла») — согласованы.
- **Новых doc↔doc несогласованностей нет.**

## Закрытия `DOCS_CHECK_2` — интактны

`DealActionState` (модель+lifecycle), `AttachedAlgoOrderStateResolver`,
маркер-база `ServiceCommandPayload` — правками F1/F2/CMD-Q3 не затронуты,
остаются согласованными.

## CMD-Q4 — классификация (форвард, не гейтит шаг 4)

CMD-Q4 (перечисление **неизвестных** live orders/algo по инструменту,
открыт снятием bulk-команд) — **владелец step-6/8, не step-4**:

- потребители — `PrecheckHandler` (входная «нет чужих live orders/algo» —
  шаг 6 FSM), `AnomalyJob` (orphan-детекция — шаг 8),
  `ErrorHandler`/`ExitPendingHandler` (неизвестные хвосты — шаг 6/7);
- **command-layer шага 4 полон без него:** 5 refresh-команд + write-команды
  + executors + владение циклом + lifecycle'ы `Order`/`AlgoOrder`/`Position`
  + `DealActionState` — закодируемы как есть;
- вероятный remedy (инструмент-скоупный read **вне** command-layer в
  `IntegrationService`) — **не** новая команда (CMD-Q3 закрыл набор), а
  IntegrationService-метод, заводимый когда упрётся step-6/8.

⇒ **Переклассификация:** ранняя пометка «перед `CODE` закрыть CMD-Q4»
(нота `GAPS_CLOSE_2`) — пересмотрена: CMD-Q4 **форвард/non-gating** для
шага 4, как RISK-Q2 (шаг 5) / OKX-Q1 (шаг 7). `REFRESH_POSITION` уже
инструмент-скоупный → позиции-orphan покрыты; остаток (orders/algo
enumeration) — у step-6/8. Финальный «блокер vs форвард» — за
пользователем (он же ведёт переход в `CODE`).

## CODE-level заметки (не гейтят, автору кода)

Без изменений с `DOCS_CHECK_2`: рёбра `SKIPPED` в lifecycle
`DealActionState` (уточнить abandon vs cleanup); `maxAttempts` на
`Retryable` vs `ServiceCommandRetryPolicy` (snapshot vs политика).

## Торговый фокус (`trading-review`)

Без изменений: F1/F2/CMD-Q3 — структурные правки исполнительной модели,
торговую модель реконсиляции не трогают. Новой блокирующей торговой
находки нет. Форвард cross-cutting — RISK-Q2 (шаг 5), без изменений.

## Сводка

- **Чисто** (механика): F1/F2/CMD-Q3 закрыты и согласованы; рябь без
  dangling-ссылок и doc↔doc несогласованностей; закрытия `DOCS_CHECK_2`
  интактны.
- **CMD-Q4** переклассифицирован **форвард/non-gating** (step-6/8); рекомендация
  — не держит гейт `CODE` шага 4. Подтверждение «блокер vs форвард» — за
  пользователем.
- 2 не-гейтящие CODE-level заметки. Торговый гейт — без блокеров.
- **Концепт-гейт `CODE`:** пройден (при принятии форвард-классификации
  CMD-Q4).

## Рекомендация

Шаг 4 **готов к `CODE`** при подтверждении форвард-статуса CMD-Q4. Перевод
в `CODE` — за пользователем. Открытый хвост вне шага 4: CMD-Q4 (шаг 6/8),
DEAL-Q1 / OKX-Q1/Q2/Q3 (шаг 7), RISK-Q1/Q2 (шаг 5) — не гейтят.
