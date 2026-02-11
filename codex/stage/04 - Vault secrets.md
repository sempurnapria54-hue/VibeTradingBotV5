# Stage 04 — Vault: секреты для OKX и PostgreSQL (локально через docker-compose)

## Цель

Поднять HashiCorp Vault локально в Docker и вынести туда:

* креды биржи OKX (apiKey/secretKey/passphrase)
* креды подключения к PostgreSQL

Сервис должен получать значения конфигов из Vault (не хранить секреты в `application.properties`).

---

## Скоуп этапа

### 1) Docker Compose

* Добавить `vault` сервис в `docker-compose.yml`.
* Vault поднимаем локально (для dev) с persisted storage (volume) и UI.

### 2) Хранилище секретов

* Используем **KV v2**.
* Пути:

    * `secret/tradingbot/okx`
    * `secret/tradingbot/postgres`

### 3) Интеграция приложения

* Приложение читает конфиги из Vault через Spring Cloud Vault.
* В репозитории не должно быть секретов в явном виде.

### 4) Документация

* Добавить инструкцию по локальному развёртыванию Vault и заполнению секретов.

---


### 5) Уточнение по конфигу (обязательно)

Чтобы не было неоднозначности в следующих реализациях:

* В `application.yaml` не использовать значения вида `vault://...#...` для бизнес-свойств (`okx.*`, `spring.datasource.*`).
* Для наглядности в документационном блоке указывать плейсхолдеры формата `{OKX_API_KEY}`, `{OKX_SECRET_KEY}`, `{OKX_PASSPHRASE}`, `{OKX_BASE_URL}`, `{SPRING_DATASOURCE_URL}`, `{SPRING_DATASOURCE_USERNAME}`, `{SPRING_DATASOURCE_PASSWORD}`.
* В коде приложение должно читать стандартные Spring config properties (`okx.*`, `spring.datasource.*`), а не прямые Vault URI.

## Definition of Done

1. `docker compose up -d` поднимает Vault и Postgres.
2. Vault инициализирован, unseal выполнен (или используется dev-режим, если так решим).
3. Секреты записаны в KV v2 по указанным путям.
4. Приложение стартует и успешно читает:

    * OKX creds
    * Postgres creds
5. В `application.properties/yml` нет реальных секретов.
