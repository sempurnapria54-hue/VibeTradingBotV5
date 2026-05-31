# Codestyle

## На какой вопрос отвечает этот файл

Как мы пишем код (конвенции написания).

## Назначение

Конвенции написания кода проекта. Опора `code-writer` при письме и
фокуса `conventions-review` при проверке кода.

## Принципы

- **DDD** — проектируем от доменной модели.
- **Clean Code** — код читается как проза.
- **Гибкость и переиспользуемость** — без преждевременной
  абстракции, но с расчётом на расширение.
- **Rich-доменные модели** — бизнес-логика живёт на доменных
  моделях, не размазана по сервисам (см.
  `docs/rules/business-logic-on-domain-model.md`).
- **Lombok** — убираем boilerplate, но не прячем за ним
  бизнес-логику.

## Нейминг по слоям

Одна сущность проходит слои с суффиксом слоя. Пример для
`Instrument`:

| Слой | Класс |
|---|---|
| Домен | `Instrument` |
| Доменный снапшот ответа биржи | `InstrumentExternalSnapshot` |
| Интеграция (сырой ответ источника) | `Instrument{Exchange}Response` (например `InstrumentOkxResponse`) |
| Persistence | `InstrumentEntity` |
| API нашего сервиса | `InstrumentApiResponse` |

В домене — полные слова, без сокращений.

## Слои (зоны ответственности)

| Слой | Принимает / отдаёт | Зовёт | Чего не видит |
|---|---|---|---|
| Controller | api ↔ domain | Service | client DTO, `OkxRestClient` |
| Service | domain | ClientService | — |
| ClientService | domain ↔ client DTO | `OkxRestClient` | — |
| DataService | domain ↔ persistence | Repository | — |
| `OkxRestClient` | чистый HTTP + подпись + DTO | — | domain |
| Repository | интерфейсы-запросы (нативные — через аннотации) | — | — |

- Controller принимает api, делает `apiToDomain`, дальше работает
  только с domain; client DTO и `OkxRestClient` ему недоступны.
- Service принимает domain, держит прикладную логику.
- ClientService — единственная граница domain ↔ client DTO.
- DataService — единственная граница domain ↔ persistence.

## Маппинг

- Только **MapStruct**, ручного маппинга нет.
- Один mapper на сущность.
- Методы отражают направление: `apiToDomain`, `domainToApi`,
  `domainToEntity`, `entityToDomain` и т. п.
- Цепочка `api → domain → …` обязательна **даже при 1:1** (не
  срезаем слой).
- client/OKX DTO наружу не отдаём; доменный слой работает только с
  доменными моделями.
- Маппинг — на границах: контроллер `apiToDomain` → домен;
  `DataService` — domain ↔ persistence; `ClientService` —
  domain ↔ client DTO.

## Lombok

- `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor`
  — для DTO / api / domain.
- `@Value` — для immutable.
- `@Builder` — где уместно.
- `@Data` **не использовать** (особенно на доменных моделях).
- Lombok не скрывает бизнес-логику: сложные методы пишем явно.

## Форматирование

- Пустая строка после объявления класса.
- Импорты не схлопывать (без `*`).
- Magic-number и magic-string → в константы. Исключение —
  лог-сообщения.
- Аннотации пирамидкой: по длине строки, короткая сверху → длинная
  снизу.
- Статические методы — статический импорт, без имени класса, если
  нет конфликта.

## Строгие правила

- Не объявлять несколько переменных через запятую.
- Фигурные скобки всегда: `if` / `else` / `for` / `while` / `do`.
- `Objects.equals(a, b)` вместо `a.equals(b)`.
- `CollectionUtils.isNotEmpty` / `isEmpty` вместо ручных проверок
  коллекций.
- Null-check только `Objects.isNull` / `Objects.nonNull`. Запрет
  `== null` / `!= null` **везде**: `if`, тернарники, присваивания,
  составные условия.
- Запрет прямых отрицаний (`!flag`, `!Objects.equals(...)`,
  `!"CONST".equals(...)`) → `BooleanUtils.isFalse` / `isTrue`.

## Контроллеры / API

- Swagger-аннотации + валидация.
- `@RequestParam` — ок, но при более чем двух параметрах →
  `@ParameterObject`.
- API-пути: от общего к частному (слева → справа).

## Логирование

- Ошибки внешних API логируем с контекстом: endpoint, параметры
  (без секретов), `code` / `msg`.
- Секреты (`apiKey` / `secret` / `passphrase`) **никогда** не
  логируем.

## Доменные модель-классы

Поясняющий javadoc — на класс и на поля.

## Обработка ошибок — TBD

**Не решено.** Конвенция обработки ошибок не зафиксирована.
Открытые аспекты:

- коды ошибок;
- global `@ControllerAdvice` vs per-endpoint обработка;
- документируем ли ошибки на контроллере (`@ApiResponses`).

До решения раздел не фиксируется; код, упирающийся в эти вопросы,
эскалирует их.

## Наполнение

Итеративно через чат, как `tech-radar`: возникла конвенция в коде —
обсудили — записали. Спекулятивные правила не вносим.

## Связи

- Радар технологий — `.claude/rules/tech-radar.md`.
- Автор кода — `.claude/agents/code-writer.md`.
- Фокус проверки — `.claude/skills/conventions-review.md`.
