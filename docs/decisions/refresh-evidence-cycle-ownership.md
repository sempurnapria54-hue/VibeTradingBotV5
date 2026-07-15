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

Если `REFRESH_ORDER` атомарен (один эндпоинт), один вызов не делает полный
цикл и не может бросить not-found-terminal; если walks все эндпоинты — не
«один запрос». Развилка: **(a)** обход в исполнителе vs **(b)**
FSM-секвенс отдельных команд.

## Решение (вариант (a), валидировано в чате)

- **Refresh-исполнители** (`REFRESH_ORDER`, `REFRESH_ALGO_ORDER`,
  `REFRESH_FILLS`) проходят свой evidence-cycle **внутри одной команды**
  (эскалация live → pending → history → archive), **обрываются на первом
  успешном эндпоинте**, полный обход — только при не-найдено, и **сами
  выносят терминал** (`MISSING_AFTER_REFRESH` / not-found).
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
  > реш.1):** `REFRESH_FILLS` **снят** — order-fill-метрики идут из
  > `OkxOrderResponse` через `REFRESH_ORDER`, fills для P&L не нужны;
  > его within-command 3d→3m-обход больше не актуален. Ту же within-command
  > модель **наследует новая `REFRESH_BILLS`**: пагинация bills 7d
  > (`/account/bills`) → 3m (`/account/bills-archive`) **внутри одной
  > команды**.

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
  3d→3m внутри команды. **Устарело:** `REFRESH_FILLS` снят на `GAPS_CLOSE_2`
  шага 7 (N12); within-command-эскалация 7d→3m живёт теперь в `REFRESH_BILLS`
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
    состав меняется с сущностями. Слепок на 2026-06-10 был `REFRESH_ORDER` /
    `REFRESH_ALGO_ORDER` / `REFRESH_POSITION` / `REFRESH_BALANCE` /
    `REFRESH_FILLS`; на шаге 7 (N12/N6) `REFRESH_FILLS` снят, добавлены
    `REFRESH_POSITIONS_HISTORY` и `REFRESH_BILLS` — **по тому же принципу**, по
    одной на новую сущность (`docs/decisions/pnl-finalization-mechanics.md`
    реш.1). Актуальный состав — `docs/components/models/ServiceCommand.md`.

## Закрытие

F1 закрыта на `GAPS_CLOSE_2` шага 4 фазы 1 (2026-06-10). F2 — той же
моделью; архив 3m+ — `OKX-Q2` (шаг 7).
