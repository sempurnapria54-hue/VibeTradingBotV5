# Task 040 — Реализовать Stage 04: Vault (OKX + Postgres secrets) через docker-compose

## Контекст

Реализуй Stage 04 из `codex/stage/04 - Vault secrets.md`.

Цель: поднять Vault локально и вынести в него секреты OKX и Postgres. Приложение должно читать конфиги из Vault.

Соблюдать `codex/Code style.md`.

---

## Важные ограничения (строго)

* Не хранить реальные секреты в репозитории.
* Не менять бизнес-логику и OKX proxy endpoints.
* Добавления должны быть минимальными и воспроизводимыми.

---

## Что нужно сделать

### 1) docker-compose: Vault

Обнови `docker-compose.yml`:

* добавить сервис `vault` на базе `hashicorp/vault:1.15` (или совместимой версии)
* включить UI
* порты:

    * `8200:8200`
* volume для данных Vault (persist)
* capability:

    * `IPC_LOCK`

Vault конфигурация (dev-friendly, но с persisted storage):

* использовать `file` storage (внутри контейнера, с volume)
* listener на `0.0.0.0:8200`, `tls_disable=1`

Примечание: если используешь `-dev`, опиши это в инструкции и зафиксируй root token через env `VAULT_DEV_ROOT_TOKEN_ID`.

### 2) Инструкция развёртывания Vault (файл)

Создай `docs/ops/vault_local.md` с инструкцией:

* запуск compose
* как зайти в UI
* как инициализировать Vault (init)
* как выполнить unseal
* как залогиниться
* как включить KV v2 (если нужно)
* как записать секреты по путям:

    * `secret/tradingbot/okx`
    * `secret/tradingbot/postgres`

Поля секретов:

* OKX:

    * `okx.api-key`
    * `okx.secret-key`
    * `okx.passphrase`
    * `okx.base-url`
* Postgres:

    * `spring.datasource.url`
    * `spring.datasource.username`
    * `spring.datasource.password`

### 3) Spring: чтение конфигов из Vault

Добавь зависимости:

* `spring-cloud-starter-vault-config`
* `spring-cloud-starter-bootstrap` (если требуется для твоей версии Spring Cloud)
* зафиксируй BOM Spring Cloud, совместимый со Spring Boot 3.x.

Добавь конфиг (предпочтительно `src/main/resources/application.yml`):

* `spring.application.name=tradingbot`
* `spring.cloud.vault.enabled=true`
* `spring.cloud.vault.scheme=http`
* `spring.cloud.vault.host=localhost`
* `spring.cloud.vault.port=8200`
* `spring.cloud.vault.authentication=TOKEN`
* `spring.cloud.vault.token=${VAULT_TOKEN}` (без значения в репо)
* `spring.cloud.vault.kv.enabled=true`
* `spring.cloud.vault.kv.backend=secret`
* `spring.cloud.vault.kv.application-name=tradingbot`

Требование:

* datasource и OKX конфиги должны приходить из Vault.
* В репозитории оставить только placeholder-значения или ничего.

### 4) README / запуск

Обнови корневой `README.md`:

* как поднять Postgres+Vault
* как экспортировать `VAULT_TOKEN`
* как стартовать приложение

---

## Definition of Done

1. `docker compose up -d` поднимает Postgres и Vault.
2. Vault доступен по `http://localhost:8200`.
3. После записи секретов приложение стартует без секретов в репо и подключается к Postgres.
4. OKX конфиги доступны в рантайме из Vault.

---

## Не делать

* Не коммитить токены/ключи.
* Не менять OKX API контракты.
