# Raw exchange DTO не выходит за adapter-layer

## На какой вопрос отвечает этот файл

Какое правило системы ограничивает распространение raw exchange
DTO по слоям.

## Правило

Raw exchange DTO (полный сырой response/DTO биржи) не выходит за
пределы `ClientService` / adapter-layer.

- `ClientService` получает raw response, валидирует структуру и
  обязательные поля, проверяет exchange-specific invariants и маппит
  **только** runtime-useful поля в validated **normalized external
  snapshot**.
- Наружу (в executor / domain / risk-layer) выходит только
  normalized snapshot, не raw DTO.
- Validation-only поля биржи используются внутри `ClientService` и в
  normalized snapshot не попадают.

## Почему

Normalized external snapshot содержит только поля, которые обновляют
доменную модель. Это держит домен независимым от формата конкретной
биржи и не даёт exchange-specific деталям протекать в торговую логику
(exchange-specific факты живут в `docs/client/<Биржа>/` — см.
`.claude/decisions/client-layer-docs.md`).

## Где применяется

Сквозное правило, действует для всех refresh-flow всех сущностей
(balance, position, order, algo-order и т. д.). Конкретные маппинги —
в `docs/client/<Биржа>/rules/`. Первоисточник правила — здесь
(сквозной слой, см. `.claude/decisions/rule-source-of-truth.md`).
