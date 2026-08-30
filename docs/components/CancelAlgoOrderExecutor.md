# CancelAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CANCEL_ALGO_ORDER_COMMAND`.

## Назначение

Получает `CANCEL_ALGO_ORDER_COMMAND`. Загружает algo-order по
`payload.algoOrderId`, отправляет cancel — endpoint ветвится по
семье algo (ordinary → `cancel-algos`, advance/trailing →
`cancel-advance-algos`; И-1 исход (а), семья из `conditionType` —
`docs/models/mapping/AlgoOrder.md`), сохраняет ACK / command result;
`AlgoOrder` в `CANCELED` по ACK не переводит — факт отмены подтверждается
refresh/search/history. Если refresh/history показывает другой факт,
верим exchange facts (см.
`docs/models/mapping/AlgoOrder.md`).

После рестарта pending cancel в очереди не восстанавливается (см.
`docs/rules/command-lifecycle.md`). ACK не runtime truth (см.
`docs/rules/ack-not-runtime-truth.md`); общая семантика `CANCEL_*` —
`docs/components/ServiceCommandExecutor.md`.

## CancelAlgoOrderCommandPayload

`algoOrderId`, `cancelReason`.

## Тропы, на которых снятие эмитится

1. teardown выхода;
2. kill-switch;
3. снятие встроенной защиты доборной ноги после подтверждения новой
   основной;
4. **cancel-old ноги входа при `REPLACE`** — attached-защита уходит вместе
   с родителем (`docs/lifecycles/Order.md`). При непустом филле порядок ног
   ветвится так, чтобы к этому моменту покрытие уже несла новая нога
   (`docs/rules/live-risk-protection.md`);
5. **снятие ступени защитной лестницы стратегией** (`CANCEL` защитного
   algo-order). Преконтроль эту тропу не видит — команда вне scope
   валидатора; падение покрытия ловит выходная проверка `MANAGING`
   (`docs/components/TrancheManagingHandler.md`).

## Числа риска здесь не пересчитываются

**Операнд «действующая защита» меняет не эта команда, а добыча.** Живость
защиты резолвится её статусом, а статус двигает наблюдённый факт:
снятие не финализирует заявку (`docs/lifecycles/AlgoOrder.md`). До того
момента заявка считается живой, и пересчёт четвёрки здесь был бы
пересчётом **по намерению**, запрещённым домом четырёх чисел
риска (`docs/models/domain/aggregate/Deal.md`). Основание то же, по
которому не пересчитывает и отмена обычной заявки
(`docs/components/CancelOrderExecutor.md`): обе команды записывают
намерение, а не факт.
