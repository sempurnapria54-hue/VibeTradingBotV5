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
| Spring Security | контур доступа к нашей поверхности: `SecurityFilterChain` (умолчание закрыто, открыта проба живости), stateless + Basic, точки входа отказа | `adopt` (построен на шаге 9 фазы 1; дом политики — `docs/rules/api-access-policy.md`. Пер-операционных `@PreAuthorize` нет намеренно: при одном принципале различать некого) |
| Apache Commons (Lang3 `BooleanUtils`, Collections `CollectionUtils`) | утилиты boolean/collection-проверок (см. `codestyle`: запрет прямых отрицаний, проверки коллекций) | `adopt` |
| Lombok | boilerplate (геттеры/сеттеры/конструкторы/`@Builder`/`@Value`) | `adopt` |
| MapStruct | маппинг между слоями (api↔domain↔persistence↔client) | `adopt` |
| springdoc-openapi | OpenAPI/Swagger-документация нашего API | `adopt` |
| Spring `@Async` / `@EnableAsync` | асинхронный запуск джоб вне расписания (через фасад, не блокируя HTTP-ответ) | `adopt` |
| spring-cloud-vault (`spring-cloud-starter-vault-config`) | секреты из Vault через `spring.config.import: vault://` per-profile: datasource (prod→`tradingbot/postgres`, test→`tradingbot/postgres-test`) **и** OKX-креды (prod→`tradingbot/okx`, test→`tradingbot/okx-test`) **и** принципал поверхности (prod→`tradingbot/api-access`, test→`tradingbot/api-access-test`) | `adopt` (Vault-привязка секретов введена; остаётся остаточный хардненинг — политики/approle, ротация, unseal: операции над самим Vault, кодом не решаются. BOM `spring-cloud-dependencies:2025.1.x` под SB4) |
| Jackson 2 (`com.fasterxml.jackson`, модуль `spring-boot-jackson2`) | JSON: сериализация JSONB-навеса (`RuntimeJsonConverter`/`StrategyJsonConverter`), DTO-аннотации, веб-слой | `adopt` (интерим) — кодовая база на Jackson 2; в SB4 дефолт уехал на Jackson 3, поэтому бин `ObjectMapper` даём совместимостным автоконфигом `spring-boot-jackson2` (`Jackson2AutoConfiguration`) |
| Jackson 3 (`tools.jackson`, модуль `spring-boot-jackson`) | JSON по умолчанию в SB4/Spring 7 (автоконфиг `JacksonAutoConfiguration` из `starter-web`) | `assess` — целевой end-state; миграция кода с Jackson 2 (`ObjectMapper.copy()/setDefaultPropertyInclusion`, `JsonProcessingException`) и снятие `spring-boot-jackson2` — отдельным шагом (`backlog.md` §«Инфра-долг (Boot 4 миграция / рантайм-робастность)» → §«I2. Миграция кода на Jackson 3») |
| Raw-JDBC (`DataSource`/`Connection`) ради advisory-замка | Postgres advisory lock на **одном соединении** на весь проход оркестратора для мультиинстанс-сериализации (гейт D-M1). Горизонт — масштабирование ядра после прод-рубежа (несколько реплик конкурируют за проход); до него оркестратор — in-process `JobExecutionGuard`, как остальные джобы. Ограничение: raw-JDBC — только ради advisory-замка на одном соединении (JPA-репозиторий connection-pinning не гарантирует), не для обычного доступа к данным. | `hold` (вернётся по масштабированию ядра; хроника снятия — `.claude/work/backlog.md` §«Унификация инфраструктуры джоб», `docs/components/DealOrchestratorJob.md`) |
| Kubernetes + Argo CD (GitOps) | развёртывание всех сервисов и данных; Argo CD приводит кластер к состоянию манифестов (`docs/architecture/platform.md`) | `assess` (решение дизайн-прохода 2026-09-04; Flux — равная альтернатива, Argo выбран за интерфейс) |
| Управляемый Kubernetes у российского провайдера (кандидаты: Yandex Cloud, MWS) | целевое место `prod`-кластера; до выбора — операторы в кластере вне облака | `assess` (класс закрыт держателем 2026-09-04, ARCH-Q1; провайдер — отдельной проработкой, `.claude/work/backlog.md` §«Проработка облака, хостинга кода и CI») |
| CI хостинга репозитория (кандидаты: GitHub Actions, GitLab CI) | сборка образов по путям монорепозитория, смена тега образа в манифестах; раннеры в кластере | `assess` (**выбор не сделан**: прежняя запись «GitHub Actions» понижена держателем до кандидата 2026-09-04 — хостинг кода и CI решаются той же проработкой, что и облако) |
| Kafka (оператор Strimzi) | несрочное взаимодействие и факты между сервисами; конверт события с `tenantId` ключом партиции (`docs/architecture/contracts.md`) | `assess` (дизайн-проход 2026-09-04; реестр схем не заводится, пока все стороны в одном монорепозитории) |
| Transactional outbox (реле в процессе владельца) | решение и его событие одной транзакцией; реле опрашивает и публикует (`docs/architecture/data-ownership.md`) | `assess` (дизайн-проход 2026-09-04; Debezium/CDC — отложенная альтернатива) |
| TimescaleDB (расширение Postgres, оператор CloudNativePG) | временные ряды рыночных данных: свечи, OI, funding, срезы стакана, ликвидации | `assess` (дизайн-проход 2026-09-04; ClickHouse — по условию вытеснения записи аналитическими сканами) |
| Keycloak | провайдер идентичности: учётные данные, сессии, токены, MFA; тенанты и роли — собственный `auth` | `assess` (дизайн-проход 2026-09-04) |
| OpenTelemetry + Jaeger, ELK, Prometheus + Grafana + Alertmanager | трейсы, логи, метрики и алерты с `tenantId`/`strategyId` (`docs/architecture/platform.md`) | `assess` (дизайн-проход 2026-09-04; Tempo — альтернатива Jaeger) |

Прочие модули Spring и библиотеки добавляются по ходу — по
потребности шага, не превентивно.

## Политика

Радар — **мягкая база, не жёсткая привязка**. `code-writer`
опирается на радар при выборе технологий и **вводит** новую библиотеку
или технику, если она уместнее для задачи: он владелец дома радара, и
решение фиксируется записью со статусом плюс строкой дайджеста (право
вето постфактум).

**Прежняя редакция оставляла выбор чату** («роль только предлагает…
автономный выбор — на будущее») — остаток доавтономного порядка, снятый
решением держателя 2026-09-04: выбор библиотеки есть деталь уровня кода,
её владелец назван картой (`.claude/processes/question-delegation.md`), и
ни риск-аппетита, ни продуктового scope он не задевает.

**Что маршрут не отменяет.** Библиотека, которой нет в среде и которую
нужно докачать, упирается в **доступность**, а не в развилку: это
источниковый дефицит, и он эскалируется всегда
(`.claude/processes/question-delegation.md` §«Источниковый дефицит —
эскалация всегда»).

## Наполнение

Итеративно: появилась развилка по библиотеке/технике — владелец решает
и записывает запись со статусом, решение уходит в дайджест. Спекулятивные
записи не вносим — запись заводится на потребность шага, не на
воображаемую.

## Связи

- Стиль написания — `.claude/rules/codestyle.md`.
- Автор кода — `.claude/agents/code-writer.md`.
- Фокус проверки — `.claude/skills/conventions-review.md`.
