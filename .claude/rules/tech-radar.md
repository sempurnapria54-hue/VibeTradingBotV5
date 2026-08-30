# Tech radar

## На какой вопрос отвечает этот файл

Что мы используем при написании кода (стэк и библиотеки/техники со
статусом).

## Назначение

Радар технологий проекта: зафиксированный стэк плюс библиотеки и
техники со статусом по модели tech radar. Опора `code-writer` при
выборе технологий и фокуса `conventions-review` при проверке кода.

## Стэк

- **Java 25**
- **Spring Boot 4**
- **PostgreSQL**
- **Persistence:** Hibernate (JPA).

Стэк расширяется по мере выбора.

## Статусы

Модель tech radar:

- `adopt` — используем по умолчанию;
- `trial` — пробуем в этом шаге, оцениваем;
- `assess` — на радаре, ещё не пробовали;
- `hold` — отказались / временно не используем.

## Записи

| Технология | Назначение | Статус |
|---|---|---|
| Hibernate (JPA) | ORM / persistence-слой | `adopt` |
| Spring Security | аутентификация / авторизация | `adopt` (вводится; полноценная инфраструктура — на шаге «Безопасность» Фазы 1) |
| Apache Commons (Lang3 `BooleanUtils`, Collections `CollectionUtils`) | утилиты boolean/collection-проверок (см. `codestyle`: запрет прямых отрицаний, проверки коллекций) | `adopt` |
| Lombok | boilerplate (геттеры/сеттеры/конструкторы/`@Builder`/`@Value`) | `adopt` |
| MapStruct | маппинг между слоями (api↔domain↔persistence↔client) | `adopt` |
| springdoc-openapi | OpenAPI/Swagger-документация нашего API | `adopt` |
| Spring `@Async` / `@EnableAsync` | асинхронный запуск джоб вне расписания (через фасад, не блокируя HTTP-ответ) | `adopt` |
| spring-cloud-vault (`spring-cloud-starter-vault-config`) | секреты из Vault через `spring.config.import: vault://` per-profile: datasource (prod→`tradingbot/postgres`, test→`tradingbot/postgres-test`) **и** OKX-креды (prod→`tradingbot/okx`, test→`tradingbot/okx-test`) | `adopt` (Vault-привязка секретов введена; за шагом 9 «Безопасность» — остаточный хардненинг: политики/approle, ротация, unseal, Spring Security. BOM `spring-cloud-dependencies:2025.1.x` под SB4) |
| Jackson 2 (`com.fasterxml.jackson`, модуль `spring-boot-jackson2`) | JSON: сериализация JSONB-навеса (`RuntimeJsonConverter`/`StrategyJsonConverter`), DTO-аннотации, веб-слой | `adopt` (интерим) — кодовая база на Jackson 2; в SB4 дефолт уехал на Jackson 3, поэтому бин `ObjectMapper` даём совместимостным автоконфигом `spring-boot-jackson2` (`Jackson2AutoConfiguration`) |
| Jackson 3 (`tools.jackson`, модуль `spring-boot-jackson`) | JSON по умолчанию в SB4/Spring 7 (автоконфиг `JacksonAutoConfiguration` из `starter-web`) | `assess` — целевой end-state; миграция кода с Jackson 2 (`ObjectMapper.copy()/setDefaultPropertyInclusion`, `JsonProcessingException`) и снятие `spring-boot-jackson2` — отдельным шагом (`backlog.md` §«Инфра-долг (Boot 4 миграция / рантайм-робастность)» → §«I2. Миграция кода на Jackson 3») |
| Raw-JDBC (`DataSource`/`Connection`) ради advisory-замка | Postgres advisory lock на **одном соединении** на весь проход оркестратора для мультиинстанс-сериализации (гейт D-M1). Горизонт — фаза 3 (несколько экземпляров конкурируют за проход); в фазе 1 оркестратор — in-process `JobExecutionGuard`, как остальные джобы. Ограничение: raw-JDBC — только ради advisory-замка на одном соединении (JPA-репозиторий connection-pinning не гарантирует), не для обычного доступа к данным. | `hold` (вернётся в фазу 3; хроника снятия — `.claude/work/backlog.md` §«Унификация инфраструктуры джоб», `docs/components/DealOrchestratorJob.md`) |

Прочие модули Spring и библиотеки добавляются по ходу — по
потребности шага, не превентивно.

## Политика

Радар — **мягкая база, не жёсткая привязка**. `code-writer`
опирается на радар при выборе технологий, но может **предлагать**
новые библиотеки и техники, если они уместнее для задачи. Сейчас
роль только предлагает — решение о включении в радар принимается
через чат; автономный выбор библиотек `code-writer` — на будущее.

## Наполнение

Итеративно через чат: появилась развилка по библиотеке/технике —
обсудили — записали запись со статусом. Спекулятивные записи не
вносим.

## Связи

- Стиль написания — `.claude/rules/codestyle.md`.
- Автор кода — `.claude/agents/code-writer.md`.
- Фокус проверки — `.claude/skills/conventions-review.md`.
