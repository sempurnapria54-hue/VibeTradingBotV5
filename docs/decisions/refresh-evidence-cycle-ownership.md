# Владение evidence-cycle refresh-команд: обход внутри исполнителя

## На какой вопрос отвечает этот файл

Кто проходит многоэндпоинтный evidence-cycle refresh-команд и где живёт
терминальное решение (`MISSING_AFTER_REFRESH` / not-found).

## Контекст

Находка F1 (выведена при выкладке «команды → запросы к OKX», уже после
чистого `DOCS_CHECK_2` шага 4): evidence-cycle
(`order → pending → history → archive`) задокументирован как единица и как
предусловие `MISSING_AFTER_REFRESH`, но **кто его проходит** — не
зафиксировано. Противоречие в доках:

- `docs/rules/command-lifecycle.md`: команды атомарны, «одна команда за
  проход», следующую выбирает FSM;
- `docs/components/RefreshOrderExecutor.md` / `docs/models/mapping/Order.md`:
  «`ExternalNotFoundException` — только после **полного** evidence-cycle» —
  атрибутирует терминал исполнителю.

Если `REFRESH_ORDER_COMMAND` атомарен (один эндпоинт), один вызов не делает полный
цикл и не может бросить not-found-terminal; если walks все эндпоинты — не
«один запрос». Развилка: **(a)** обход в исполнителе vs **(b)**
FSM-секвенс отдельных команд.

## Решение (вариант (a), валидировано в чате)

> **Обобщено до класса (`DOCS_CHECK_10`, 2026-08-02).** Ниже решение
> зафиксировано в редакции, перечислявшей команды поимённо; каждая новая
> многоэндпоинтная команда требовала отдельной ноты-обновления (см. ноты
> ниже) — признак того, что записан был случай, а не класс. **Действующая
> формулировка принципа — `docs/rules/command-lifecycle.md` §Правило,
> клауза «Принцип обхода — общий для класса `REFRESH_*`»**: команда
> ходит столькими запросами, сколько нужно, и останавливается, когда
> добыла искомый факт. Ноты ниже сохранены как провенанс — новые
> per-command ноты сюда **не добавляются**, лестница эндпоинтов
> конкретной команды живёт в её компонент-доке.

- **Refresh-исполнители** (тогда — `REFRESH_ORDER_COMMAND`,
  `REFRESH_ALGO_ORDER_COMMAND`, `REFRESH_FILLS`) проходят свой evidence-cycle
  **внутри одной команды** (эскалация live → pending → history → archive),
  **обрываются на первом успешном эндпоинте**, полный обход — только при
  не-найдено, и **сами выносят терминал** (`MISSING_AFTER_REFRESH` /
  not-found).
- **Атомарность — на уровне команды, не HTTP-запроса.** FSM выбирает
  следующую *команду*; эндпоинты внутри refresh-команды секвенсит
  исполнитель. Цикл и решение `MISSING` остаются в шаге 4, FSM их не
  секвенсит.
- **Атрибуция терминала исполнителю** (executor / `mapping/Order.md` /
  `mapping/AlgoOrder.md`) — канонична.
- **F2 (та же модель):** `REFRESH_FILLS` эскалирует 3d (`/trade/fills`) →
  3m (`/trade/fills-history`) **внутри команды**. Архив 3m+ (async-флоу) —
  остаётся `OKX-Q2` (шаг 7), не трогаем.

  > **Обновление (шаг 7, `docs/decisions/pnl-finalization-mechanics.md`
  > реш.1):** `REFRESH_FILLS` **снимается** на `CODE` шага 7 (в коде пока
  > жив — H15, `GAPS_CLOSE_6`) — order-fill-метрики идут из
  > `OkxOrderResponse` через `REFRESH_ORDER_COMMAND`, fills для P&L не нужны;
  > его within-command 3d→3m-обход больше не актуален. Ту же within-command
  > модель **наследует новая `REFRESH_BILLS_COMMAND`**: пагинация bills 7d
  > (`/account/bills`) → 3m (`/account/bills-archive`) **внутри одной
  > команды**.

  > **Обновление (шаг 7, H1/H3 `GAPS_CLOSE_7`): `REFRESH_POSITION_COMMAND` тоже
  > становится многоэндпоинтной.** Цикл — live `/account/positions` → при
  > not-found `/account/positions-history` по `posId`; вторая нога
  > наполняет поля положения закрытия на той же `Position`
  > (`docs/models/domain/core/Position.md` §«Положение закрытия»).
  > Отличие от `REFRESH_ORDER_COMMAND`: **терминала цикл не выносит** — статус
  > позиции определяет уже первая нога, а пустая вторая — легитимный
  > исход «число недоступно», не `MISSING_AFTER_REFRESH`. Прежняя
  > редакция шага 7 заводила под этот эндпоинт **отдельную команду**
  > `REFRESH_POSITIONS_HISTORY`; она снята — сущность одна.

## Обоснование

- Терминал `MISSING` требует знания «полный цикл исчерпан» — держать это в
  одном владельце (исполнителе) локальнее, чем размазывать трекинг
  завершённости цикла по проходам FSM (где состояние «на каком эндпоинте
  цикл» пришлось бы хранить между проходами).
- Сохраняет границу слоёв: FSM — domain-решения «какую команду дальше», не
  знает endpoint-механику биржи; executor — «как технически».
- Согласуется с уже существующей атрибуцией терминала исполнителю.

## Альтернатива (отвергнута)

- **(b) FSM секвенсит отдельные атомарные `REFRESH_*` по проходам** и сам
  трекает завершённость цикла → `MISSING`. Отвергнута: тащит
  endpoint-механику биржи в FSM, размазывает терминал-решение, усложняет
  recovery (между-проходное состояние цикла).

## Следствия

- `docs/rules/command-lifecycle.md` — атомарность переформулирована
  (команда ≠ один HTTP-запрос; refresh-команда может обходить эндпоинты
  внутри себя).
- `docs/components/ServiceCommandExecutor.md`,
  `RefreshOrderExecutor.md`, `RefreshAlgoOrderExecutor.md` — явная
  within-command-семантика обхода + терминал. (Тогда же — док
  `RefreshFillsExecutor`; **впоследствии снят вместе с командой**, см. ноту о
  `REFRESH_FILLS` ниже.)
- `docs/integrations/okx/contracts/fills.md` — `REFRESH_FILLS` эскалирует
  3d→3m внутри команды. **Устарело:** `REFRESH_FILLS` снимается решением
  `GAPS_CLOSE_2` шага 7 (N12; исполнение — `CODE`);
  within-command-эскалация 7d→3m живёт теперь в `REFRESH_BILLS_COMMAND`
  (`docs/decisions/pnl-finalization-mechanics.md` реш.1) — сам паттерн
  «обход внутри команды» решение пережил, сменился лишь его носитель.
- `docs/models/mapping/Order.md`, `AlgoOrder.md` — обход цикла атрибутирован
  исполнителю.
- **CMD-Q3 закрыт** (steer, 2026-06-10): refresh-набор — ровно по одной
  команде на сущность; bulk-команды `REFRESH_PENDING_ORDERS` /
  `REFRESH_ORDER_HISTORY` / `REFRESH_ALGO_ORDERS` / `REFRESH_ALGO_ORDER_HISTORY`
  сняты из `ServiceCommandType`, их эндпоинты — звенья внутреннего цикла.
  Снятие обнажило **CMD-Q4** (перечисление **неизвестных** live orders/algo по
  инструменту — Precheck-cleanliness / `AnomalyJob`; см.
  `.claude/work/questions/open-questions.md`).
  - **Живёт принцип** «одна команда на сущность», не конкретный состав набора:
    состав меняется с сущностями. Слепок на 2026-06-10 был `REFRESH_ORDER_COMMAND` /
    `REFRESH_ALGO_ORDER_COMMAND` / `REFRESH_POSITION_COMMAND` / `REFRESH_BALANCE_COMMAND` /
    `REFRESH_FILLS`; на шаге 7 (N12/N6) `REFRESH_FILLS` снимается, добавляется
    `REFRESH_BILLS_COMMAND` — **по тому же принципу**, по одной на новую сущность
    (`DealCashFlow`; `docs/decisions/pnl-finalization-mechanics.md` реш.1).
    positions-history новой сущности **не даёт** — это та же `Position` после
    закрытия, поэтому эндпоинт лёг **ногой цикла**, а не командой (H1/H3
    `GAPS_CLOSE_7`); тот же принцип, применённый к обратному случаю.
    Актуальный состав — `docs/components/models/ServiceCommand.md`.

## Закрытие

F1 закрыта на `GAPS_CLOSE_2` шага 4 фазы 1 (2026-06-10). F2 — той же
моделью; архив 3m+ — `OKX-Q2` (шаг 7).
