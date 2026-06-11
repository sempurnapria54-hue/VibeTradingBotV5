# DOCS_CHECK_5 — шаг 4 фазы 1 (Команды и их жизненный цикл, `ServiceCommand`)

## На какой вопрос отвечает этот файл

Что нашёл подтверждающий прогон сквозной проверки концепции шага 4
после `GAPS_CLOSE_3` — разнесения принятого решения REPLACE-only
(`docs/decisions/replace-not-amend.md`, закрытие К-1/Т-1 и
следствия И-3).

## Контекст прогона

- **Под-шаг:** `DOCS_CHECK_5` (подтверждающий, после `GAPS_CLOSE_3`:
  валидация (г-2) — чистая, дельта исполнена).
- **Фокус:** легло ли чисто снятие AMEND из доменного словаря
  (enum −2, удаление executors/payload'ов, `StrategyActionType`
  REPLACE, identity-цепочка, порядок ног, риск-скоуп) — рябь от
  ~29 правок; новые doc↔doc несогласованности / битые ссылки /
  dangling-токены.
- **Охват:** command-layer шага 4 (ServiceCommand,
  ServiceCommandExecutor/Payload, executors, DealStateMachine,
  handlers), `Strategy.md` (действия/валидация),
  `Order`/`AlgoOrder`/`DealActionState` (модели + lifecycles +
  mapping), правила (`command-lifecycle`, `risk-validator-scope`,
  `exchange-hold`, `ack-not-runtime-truth`), контракты OKX +
  манифест + скилл `integration-okx`, `CalculatedPrice`. Вне охвата:
  market-data-доки шагов 1-3 (не задеты), код (вне предмета).

## Стадия остановки

Прошёл все стадии (0-2). Стадия 0 — чисто (CMD-Q4 — форвард; И-2 —
провалидирован, ждёт кредов demo; REPLACE-вопросов уровня механики
нет — оркестрация на существующих командах). Стадия 1 — чисто
(`command-lifecycle` дополнен REPLACE в перечне составных процессов,
противоречий нет; `deal-management` амендных упоминаний не имел).
Стадия 2 — чисто (ниже).

## Проверка закрытий `GAPS_CLOSE_3`

| Закрытие | Статус |
|---|---|
| **Enum 19 → 17** | **Закрыто.** `ServiceCommand.md`: `AMEND_ORDER`/`AMEND_ALGO_ORDER` сняты, нота-о-снятии с указателем на decision; REPLACE добавлен в перечень составных flow (вместе с protection switch). |
| **Executors/payload'ы** | **Закрыто.** `AmendOrderExecutor.md`/`AmendAlgoOrderExecutor.md` удалены; реестр `ServiceCommandPayload.md` — 7 строк + нота; `ServiceCommandExecutor.md` — группа `AMEND_*` снята, нота REPLACE-оркестрации; `DealStateMachine`/`ManagingHandler`/`DealActionState` (модель+lifecycle) — командные списки без AMEND. |
| **StrategyActionType REPLACE** | **Закрыто.** `Strategy.md`: enum `CREATE/REPLACE/CANCEL/CLOSE_FULL` + определение REPLACE (полная палитра, оркестрация, порядок ног); правила 4/6/7 на REPLACE; §Связь с DealActionState — резолюция цели по цепочке замещений. |
| **Identity-цепочка** | **Закрыто.** `replacesInternalId` — `Order.md` + `AlgoOrder.md` (модели); `Order.CloseReason += REPLACED_BY_STRATEGY` (симметрично `AlgoOrder`, где уже был); lifecycles обеих сущностей — REPLACE-ноты (protective/entry порядок). |
| **REPLACE-механика action** | **Закрыто.** `DealActionState.md` §REPLACE-действия: две ноги из фактов, без новых статусов; замещаемая сущность — по цепочке из `DealContext.actionStates`. |
| **Правила** | **Закрыто.** `risk-validator-scope` (риск-контроль на place-ноге, cancel-нога не валидируется), `exchange-hold` (REPLACE блокируется парой `SUBMIT_*`), `ack-not-runtime-truth` (секвенс ног по фактам), `command-lifecycle` (REPLACE в составных). |
| **Mapping / контракты / манифест / скилл** | **Закрыто.** Амендный request-mapping снят из `mapping/Order`+`AlgoOrder` (биржевые поля остались поверхностью); контракты `order.md`/`algo-order.md` §Amend — «доменом не используется»; манифест — пометки строк; скилл — амендные строки командной таблицы сняты + нота. |
| **К-1/Т-1, И-3** | **Закрыты.** И-3-секция `algo-order.md` — «следствие закрыто решением REPLACE-only»; `CalculatedPrice` — «создаёт/замещает», `ORDER_AMEND_PRICE` → `ORDER_REPLACE_PRICE` (ренейм-нота); decision несёт рационал + отвергнутые (а)/(б)/(в)/(г-1). |

## Проверка ряби (grep-верификация)

- **`AMEND_ORDER`/`AMEND_ALGO_ORDER`/`Amend*Executor`/`Amend*Payload`
  по `docs/`:** встречаются **только** в нотах-о-снятии
  (`ServiceCommand.md`, `ServiceCommandPayload.md`) и рационале
  decision. **Dangling-ссылок на удалённые файлы нет.**
- **Generic `amend` по `docs/` вне интеграций:** только указатели на
  `replace-not-amend.md`, рационал decision и биржевые DTO-поля
  (`amendPxOnTriggerType` — имя поля OKX, остаётся). Контракты OKX —
  биржевая поверхность с пометками, согласованы.
- **`ORDER_AMEND_PRICE`:** только ренейм-нота (`CalculatedPrice.md`)
  + decision; `PriceCalculator.md` токена не содержит.
- **Link-integrity:** все доковые пути в 31 staged-файле дельты
  резолвятся (вкл. ~18 ссылок на новый
  `docs/decisions/replace-not-amend.md`).
- **Lifecycles:** `lifecycles/Order.md` cancel-intent-перечня не
  ведёт (реестр причин — модель, обновлена); `lifecycles/AlgoOrder.md`
  cancel-intent уже содержал `REPLACED_BY_STRATEGY`. Согласовано.
- **Закрытия `DOCS_CHECK_1-4` интактны:** refresh-набор (5 команд),
  `DealActionState`/payload-база, F1/F2, ветвление cancel И-1(а) с
  пометкой И-2 — правками не затронуты.

## Не-гейтящие заметки (CODE-level, автору кода)

1. **Код-дельта REPLACE-only** — существующие Java-артефакты
   (amend-executors/payload'ы из миграции, `ServiceCommandType`,
   `StrategyActionType`, пример `trend-following-ema.json`, валидатор)
   приводятся к решению на `CODE` шага 4 (concept-review код не
   сверяет — фиксация скоупа, не находка).
2. **Представление последовательности ног** в фабрике (вывод из
   фактов vs явные фазы) и эджи partial-fill entry-replace — детали
   `CODE` (зафиксировано в decision §Следствия).
3. Прежние заметки `DOCS_CHECK_2/3` (рёбра `SKIPPED`, `maxAttempts`)
   — без изменений.

## Торговый фокус (`trading-review`)

Порядок ног по риск-классу разнесён согласованно: protective —
place-first (двойная reduce-only защита; обобщение
protection-switch, грунт окна — [Vince, введение, с. 6] в decision
через проработку); entry — cancel-first (исключение двойного
риск-объёма). Окно-аномалия передана CMD-Q4 (заметка в
`open-questions.md`). Новых торговых находок нет; жёсткий гейт
(«модель не выражает торговое правило») не срабатывает — REPLACE
выражает ремодел полнее амендного пути (вкл. trailing).

## Сводка

- **Чисто.** Все закрытия `GAPS_CLOSE_3` подтверждены; рябь без
  dangling-токенов, битых ссылок и doc↔doc несогласованностей;
  закрытия прежних итераций интактны; торговый гейт — без блокеров.
- Агрегация: находок 0; не-гейтящих заметок 3 (CODE-level).
- **Концепт-гейт `CODE` шага 4 — вновь пройден** (К-1/Т-1 закрыты;
  остаток гейта `DOCS_CHECK_4` снят).

## Рекомендация

Шаг 4 **готов к `CODE`** (включая код-дельту REPLACE-only). Перевод
в `CODE` — за пользователем. Вне гейта: И-2 runtime-проверка (ждёт
кредов demo trading), CMD-Q4 (шаг 6/8, + заметка об окне двойной
защиты), RISK-Q1/Q2 (шаг 5), OKX-Q1/Q2/Q3 (шаг 7) — форварды своих
шагов.
