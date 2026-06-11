# DOCS_CHECK_2 — шаг 4 фазы 1 (Команды и их жизненный цикл, `ServiceCommand`)

## На какой вопрос отвечает этот файл

Что нашёл подтверждающий прогон сквозной проверки концепции шага 4 после
`GAPS_CLOSE_1` (закрытие находок `DOCS_CHECK_1`).

## Контекст прогона

- **Под-шаг:** `DOCS_CHECK_2` (подтверждающий, после `GAPS_CLOSE_1`).
- **Фокус:** проверить, что закрытия `DOCS_CHECK_1` (N1-N5 + CMD-Q2)
  легли чисто; проверить рябь от ~30 правок `GAPS_CLOSE_1`; нет ли новых
  doc↔doc несогласованностей и битых ссылок.
- **Охват:** тот же, что `DOCS_CHECK_1` (command-layer шага 4), плюс новые
  артефакты: `DealActionState` (модель + lifecycle), `AttachedAlgoOrderStateResolver`,
  два решения (`deal-action-state-materialization`,
  `service-command-payload-base-type`).

## Стадия остановки

Стадия 2 (компоненты + модели) — прошёл все стадии. Стадия 0 (механика/
скоуп) и стадия 1 (процессы) без изменений с `DOCS_CHECK_1` (правки
`GAPS_CLOSE_1` затронули только материализацию моделей/компонентов и
ссылки, не механику и не процессы).

## Проверка закрытий `DOCS_CHECK_1`

| # | Находка | Статус закрытия |
|---|---|---|
| **N1 / DEAL-Q3** | `DealActionState` материализован: `docs/models/domain/other/DealActionState.md` (+ `RuntimeTarget` раздел, `TargetEntityType`/`DealActionStateStatus` енумы, инвариант `UNIQUE(deal_id, strategy_action_id)`, jsonb), lifecycle `docs/lifecycles/DealActionState.md`, решение `deal-action-state-materialization.md`. **Закрыта.** Блокер `CODE` снят. |
| **N2** | `docs/components/AttachedAlgoOrderStateResolver.md` заведён; контракт/границы по образцу трёх resolver'ов; матрица «по фактам» осталась у `Order` lifecycle. **Закрыта.** |
| **N3** | Стале-ссылки на `tasks/{order,algo-order,position}.md` сняты во всех 6 местах (grep по `docs/` — чисто). **Закрыта.** |
| **N5** | Исполнитель 4 recovery-refresh команд закреплён (`ServiceCommandExecutor` §note + `RefreshOrder/AlgoOrderExecutor` — order/algo-refresh-семейство, без отдельных файлов). **Закрыта.** |
| **N4 / CMD-Q2** | Payload-разделы перенесены к 9 executor'ам; маркер-база `ServiceCommandPayload` зафиксирована (`service-command-payload-base-type.md`), дискриминатор `ServiceCommandType`, файл — дом базы. **Закрыта** (валидировано в чате). |

## Проверка ряби (правки `GAPS_CLOSE_1`)

- **Связность `DealActionState`.** Енумы (`PLANNED/CREATED/SUBMITTED/
  COMPLETED/RETRY_PENDING/FAILED/SKIPPED`), `RuntimeTarget(entityType,
  entityId)`, retry-база `Retryable` — согласованы между моделью,
  lifecycle, и **всеми** потребителями: `ServiceCommandFactory` (выбор по
  статусу), `ServiceCommandExecutor`/Create-executors
  (`RuntimeTarget(ORDER/ALGO_ORDER, id)`, `status = CREATED`),
  `RetryPolicyService` (`RETRY_PENDING`/`FAILED`), `DealContext.actionStates`,
  `runtime-error-classification`, а также step-6/7 доки (`PrecheckHandler`,
  `Strategy` §Связь, `Deal`) — grep по `RuntimeTarget`/`DealActionStateStatus`
  расхождений не дал.
- **Атрибуция ссылок.** Новые доки ссылаются только на существующие файлы
  (проверено); 7 обновлённых ссылок с «DEAL-Q3» переведены на модель;
  остаточные упоминания `DEAL-Q3`/`CMD-Q2` в `docs/` — только в двух
  decision'ах (документируют закрытие). Битых ссылок нет.
- **Базовый тип payload.** `ServiceCommand.payload : ServiceCommandPayload`
  (модель) согласован с зафиксированной маркер-базой; дискриминатор
  `ServiceCommandType` — единственный, дубля поля-дискриминатора нет.

## Пробелы

**Новых пробелов нет.** Все находки `DOCS_CHECK_1` подтверждены закрытыми;
рябь не внесла doc↔doc несогласованностей.

## CODE-level заметки (вне gap-таксономии concept-review, не гейтят)

Не концепт-пробелы (не doc↔doc несогласованность, не name-level, не
блокирующий вопрос) — детали реализации для автора кода:

- **`DealActionStateStatus` — рёбра `SKIPPED`** (`PLANNED→SKIPPED`,
  `CREATED→SKIPPED`): матрица переходов синтезирована из установленного
  flow; семантика «локально созданную, но не отправленную сущность
  abandon'ить как `SKIPPED` vs cleanup'ить» — уточнить на `CODE`. Lifecycle
  помечает матрицу как собранную из установленного.
- **`maxAttempts` в двух местах** — на `Retryable` (инстанс) и в
  `ServiceCommandRetryPolicy` (конфиг, `RetryPolicyService.md`). Не
  противоречие (snapshot инстанса vs политика), но роль инстанс-поля
  уточнить на `CODE` (эффективный максимум из политики vs самостоятельное
  поле). Пре-существующая деталь, не внесена `GAPS_CLOSE_1`.

## Торговый фокус (`trading-review`)

Без изменений с `DOCS_CHECK_1`: материализация `DealActionState` /
resolver / base-type — операционно-структурные правки, торговую модель
реконсиляции не трогают. Новой блокирующей торговой находки нет; модель
операционной безопасности (факт-по-`REFRESH`-не-ACK, evidence-cycle,
reduce-only, protection-lost) корпусно-состоятельна. Форвард cross-cutting
— RISK-Q2 (worst-case guard, шаг 5), без изменений.

## Сводка

- **Чисто.** Все находки `DOCS_CHECK_1` (N1-N5 + CMD-Q2) закрыты; новых
  концепт-пробелов и doc↔doc несогласованностей нет; битых ссылок нет.
- 2 не-гейтящие CODE-level заметки (`SKIPPED`-рёбра, `maxAttempts`) —
  автору кода, не требуют `GAPS_CLOSE_2`.
- **Гейт `CODE` — пройден** (правило `roadmap-step-execution.md` §«Гейт
  `CODE` — чистый `DOCS_CHECK`»): чистый прогон без открытых находок и
  расхождений. Торговый гейт — без блокеров.

## Рекомендация

Шаг 4 **готов к `CODE`**. Перевод за пользователем. Открытый хвост (вне
шага 4): DEAL-Q1, OKX-Q1/Q2/Q3 (шаг 7), RISK-Q1/Q2 (шаг 5) — не гейтят.
