# Stage 01 — Bootstrap: поднять сервис и зависимости

## Цель

Поднять минимально работоспособный Spring Boot сервис со всеми зависимостями из `codex/Tech radar.md`. Базу данных PostgreSQL поднимаем через `docker-compose`.

---

## Что должно быть в репозитории

### 1) Docker

* `docker-compose.yml` для PostgreSQL (локальный запуск).
* Переменные:

    * `POSTGRES_DB=tradingbot`
    * `POSTGRES_USER=postgres`
    * `POSTGRES_PASSWORD=password`
* Порт: `5440:5432`
* Volume для данных.

### 2) Spring Boot приложение

Минимальный каркас проекта:

* Java 21, Spring Boot 3.x, Maven
* Зависимости:

    * spring-boot-starter-web
    * spring-boot-starter-data-jpa (**Hibernate**)
    * postgresql driver
    * lombok
    * mapstruct (+ annotation processor)
    * spring-boot-starter-actuator

### 3) Конфигурация

* `application.properties` (или `application.yml`) с подключением к Postgres из docker-compose:

    * `spring.datasource.url=jdbc:postgresql://localhost:5432/tradingbot`
    * `spring.datasource.username=postgres`
    * `spring.datasource.password=password`
    * `spring.jpa.hibernate.ddl-auto=update` *(в рамках Stage 01 допустимо)*
    * `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`

### 4) Минимальные контроллеры

* `GET /actuator/health` — должен быть доступен.
* `GET /api/health` (опционально) — простой health-check контроллер.

---

## Definition of Done (критерии готовности)

1. Команда поднимает БД:

```bash
docker compose up -d
```

2. Приложение стартует локально и подключается к Postgres без ошибок.
3. `GET /actuator/health` возвращает статус `UP`.
4. Репозиторий содержит:

* `docker-compose.yml`
* `pom.xml`
* базовый `README.md` проекта (кроме `codex/*` файлов)

---

## Ссылки

* Технологии: `codex/Tech radar.md`
* Стиль кода: `codex/Code style.md`
* Навигация Codex: `codex/main.md`
