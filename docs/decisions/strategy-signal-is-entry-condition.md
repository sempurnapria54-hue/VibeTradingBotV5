# «Сигнал» стратегии — это условие входа

## На какой вопрос отвечает этот файл

Почему «сигнал» в формулировке шага 2 — это условие входного шага
стратегии (а не отдельная сущность), и почему удалены
`StrategyConditionSourceType.SIGNAL` /
`StrategyConditionRuleType.SIGNAL_SCORE_REACHED`.

## Контекст

Формулировка шага 2 роадмапа — «объявляет нужные индикаторы и
**условие сигнала**». Было неоднозначно, что есть «сигнал»: входное
условие `ENTRY`/`GRID_ENTRY`-шага, либо
`StrategyConditionSourceType.SIGNAL` /
`StrategyConditionRuleType.SIGNAL_SCORE_REACHED`. Во втором случае
производитель «сигнала / signal-score» в доках не описан (name-level
без источника). Пробел зафиксирован эскалацией Э3 на `DOCS_CHECK_2`,
открыт как STRAT-Q2; решён здесь.

## Принятое решение

«Условие сигнала» шага 2 = `condition.rules` входного шага
(`ENTRY` / `GRID_ENTRY`). Стратегия самодостаточна: условие истинно →
срабатывает действие из той же стратегии. Отдельной сущности «сигнала»
нет — это и есть «сигнал от стратегии».

**Удалены из enum'ов** (`docs/models/domain/aggregate/Strategy.md`
§Условия):

- `StrategyConditionSourceType.SIGNAL`;
- `StrategyConditionRuleType.SIGNAL_SCORE_REACHED`.

Это орфаны: производитель нигде не описан (name-level), в
`Strategy API examples` не используются; при самодостаточной модели не
нужны.

**Рационал удаления (чтобы не вернуть по инерции).** Эти значения
служили бы только будущему слою скоринга (несколько под-сигналов →
confidence-score → порог `SIGNAL_SCORE_REACHED`), которого нет ни
производителем, ни потребностью. Если такая механика понадобится —
вводится заново вместе с производителем.

## Следствия

- `docs/models/domain/aggregate/Strategy.md` — `SIGNAL` убран из
  `StrategyConditionSourceType`, `SIGNAL_SCORE_REACHED` — из
  `StrategyConditionRuleType`.
- Закрывает эскалацию **Э3** (`GAPS_CLOSE_2`) и вопрос **STRAT-Q2**.

## Связи

- Модель — `docs/models/domain/aggregate/Strategy.md` (§Условия,
  §StrategyStep — `ENTRY`/`GRID_ENTRY`).
- Контракт авторинга условия —
  `docs/decisions/strategy-condition-authoring-contract.md`.
