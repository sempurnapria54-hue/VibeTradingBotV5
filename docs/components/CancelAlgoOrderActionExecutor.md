# CancelAlgoOrderActionExecutor

## На какой вопрос отвечает этот файл

Кто планирует `CANCEL`-действие над standalone algo-order за проход.

## Назначение

`CancelAlgoOrderActionExecutor` — per-pass `StrategyActionExecutor` (см.
`docs/components/StrategyActionExecutor.md`) снятия защиты, объявленного
стратегией (`StrategyAlgoOrderAction` + `actionType = CANCEL`). По стадии
`DealActionState` выдаёт следующую команду:

```text
PLANNED   -> преконтроль снятия -> CANCEL_ALGO_ORDER_COMMAND
SUBMITTED -> REFRESH_ALGO_ORDER_COMMAND
```

Стадии `CREATED` у снятия нет: локальной сущности оно не создаёт. Факт
снятия подтверждает добыча, а не приём команды
(`docs/rules/ack-not-runtime-truth.md`).

## Снятие проходит преконтроль

Снятие защиты при живой экспозиции риск не снимает, а **увеличивает**, и
потому валидируется по ветке ослабления защиты
(`docs/rules/risk-validator-scope.md`). Операнд — покрытие транша после
того, как снимаемая защита исчезнет (`docs/spec/protection-coverage.json`,
величина `removalAllowed`); ниже экспозиции этого транша — отказ
`PROTECTION_COVERAGE_REDUCED`.

**Отказ — отложение, а не авария.** Действие не исполняется, транш
остаётся в своём статусе, позицию всё это время держит прежняя защита
(`docs/rules/live-risk-protection.md` §«Снятие защиты — риск-увеличивающее
действие»). Верхней границы ожидания нет.

**Преконтроль стои́т в двух местах, и это не дубль.** Гейт готовности
(`readiness`) отвечает **до** того, как заведена строка исполнения:
отложенное действие исполнения не начинает вовсе, и бюджет попыток не
расходуется. Проверка на самой команде отвечает **между проходами**:
строка живёт дольше одного прохода, а покрытие за это время меняется —
защита-заместитель могла не подтвердиться либо уйти.

## Резолв цели снятия

Цель объявляется ключом действия (`targetActionKey`) и резолвится через
**строку исполнения целевого действия** на текущем эпизоде транша:
ключ → `DealActionState` → `targetEntityId` → живая отдельная
защита транша. Цели среди живых защит нет — снимать нечего: действие
неактуально, пакет шага берёт следующее
(`docs/components/StrategyActionOrchestrator.md` §«Порядок выбора
действия»).

**Резолв по корню цепочки замещений** (`docs/spec/strategy-walkthrough.json`,
величина `cancelTargetCandidates`) здесь не воплощён — названное
ограничение: цепочек в рантайме не существует, пока фабрика `REPLACE`-ног
их не порождает.

## Причина снятия

`CANCELED_BY_STRATEGY` — снятие объявлено стратегией. Замещение
(`REPLACED_BY_STRATEGY`) и аварийные тропы (`KILL_SWITCH`) этой командой
не эмитятся: у них свои владельцы.

## Границы

- Статус исполнения сам не пишет — его двигают исполнители команд.
- Условие шага не проверяет: это сделал обработчик.
- Более одной команды за проход не выдаёт.
- Встроенную защиту не адресует: у неё своя команда
  (`docs/components/CancelAttachedProtectionExecutor.md`).

## Связи

- Исполнитель команды — `docs/components/CancelAlgoOrderExecutor.md`.
- Диспетчер — `docs/components/StrategyActionOrchestrator.md`.
- Преконтроль — `docs/components/RiskValidator.md`, `docs/components/ActionRiskGate.md`.
- Покрытие защитой — `docs/rules/live-risk-protection.md`.
