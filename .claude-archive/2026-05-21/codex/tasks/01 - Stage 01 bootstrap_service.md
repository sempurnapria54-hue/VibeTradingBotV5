# Task 010 — Реализовать Stage 01 (Bootstrap: сервис + зависимости + docker-compose)

## Контекст

Нужно реализовать **Stage 01** из `codex/stage/01 - Bootstrap: поднять сервис и зависимости.md`.
Цель: поднять минимальный Spring Boot сервис (Java 21) со всеми зависимостями из `codex/Tech radar.md` и подключением к PostgreSQL, который запускается через `docker compose`.

Важно: внешний порт Postgres занят → используем **`5440:5432`** (host:container).

---

## Что нужно сделать (чёткий список)

### 1) Docker Compose

Создай/обнови `docker-compose.yml` в корне репозитория:

* Сервис `db` на базе `postgres:16`
* env:

    * `POSTGRES_DB=tradingbot`
    * `POSTGRES_USER=postgres`
    * `POSTGRES_PASSWORD=password`
* ports: **`5440:5432`**
* volume: `pgdata:/var/lib/postgresql/data`
* объяви volume `pgdata` в конце.

### 2) Maven + Spring Boot каркас

Создай Maven-проект (если ещё нет) с:

* Java **21**
* Spring Boot **3.x**
* Packaging: jar

Добавь зависимости:

* `spring-boot-starter-web`
* `spring-boot-starter-data-jpa` (Hibernate)
* `postgresql` driver
* `spring-boot-starter-actuator`
* Lombok (+ annotationProcessor)
* MapStruct (+ annotationProcessor)

### 3) Базовая структура приложения

Пакет по умолчанию:

* `com.example.tradingbot`

Классы:

* `TradingBotApplication` (SpringBootApplication)
* `rest/controller/HealthController` с `GET /api/health` → возвращает простой текст/JSON "OK".

Actuator должен быть доступен по умолчанию (endpoint health).

### 4) Конфигурация приложения

Создай `src/main/resources/application.properties`:

* datasource:

    * `spring.datasource.url=jdbc:postgresql://localhost:5440/tradingbot`
    * `spring.datasource.username=postgres`
    * `spring.datasource.password=password`
* JPA:

    * `spring.jpa.hibernate.ddl-auto=update` (допустимо в Stage 01)
    * `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`
* (опционально) выстави `server.port=8080` (если не задано)

### 5) README проекта (корневой)

Создай/обнови корневой `README.md` (не `codex/main.md`) с минимальными командами запуска:

* `docker compose up -d`
* `./mvnw spring-boot:run` или `mvn spring-boot:run`
* проверки:

    * `GET http://localhost:8080/actuator/health`
    * `GET http://localhost:8080/api/health`

---

## Инварианты и стиль

* Соблюдай правила из `codex/Code style.md`:

    * не объявлять несколько переменных через запятую
    * всегда ставить `{}` после `if/for/while`
* Время — UTC.
* Используем Hibernate/JPA (не jOOQ).

---

## Definition of Done (проверяемое)

1. `docker compose up -d` поднимает Postgres на `localhost:5440`.
2. Приложение стартует без ошибок и подключается к БД.
3. `GET /actuator/health` возвращает `UP`.
4. `GET /api/health` возвращает `OK`.

---

## Что НЕ делать в этом таске

* Не добавлять бизнес-логику OKX, свечи, модели и т.п.
* Не добавлять этапы/таски сверх Stage 01.
* Не вводить миграции (Liquibase/Flyway) — это отдельный этап.
