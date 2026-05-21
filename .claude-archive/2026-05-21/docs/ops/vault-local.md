# Vault локально (Docker Compose)

Эта инструкция описывает, как поднять HashiCorp Vault локально и загрузить секреты для проекта.

> Важно: это локальная dev-настройка. Не коммить токены/ключи в репозиторий.

---

## 1) Запуск

```bash
docker compose up -d
```

Проверка:

* Vault UI: `http://localhost:8200`

---

## 2) Инициализация и unseal (если Vault НЕ в dev-режиме)

### 2.1 Init

```bash
docker exec -it vault vault operator init -key-shares=1 -key-threshold=1
```

Сохрани:

* `Unseal Key 1`
* `Initial Root Token`

### 2.2 Unseal

```bash
docker exec -it vault vault operator unseal <UNSEAL_KEY>
```

### 2.3 Login

```bash
docker exec -it vault vault login <ROOT_TOKEN>
```

---

## 3) Включить KV v2 (если не включён)

```bash
docker exec -it vault vault secrets enable -path=secret kv-v2
```

---

## 4) Записать секреты

### 4.1 OKX

Путь: `secret/tradingbot/okx`

```bash
docker exec -it vault vault kv put secret/tradingbot/okx \
  okx.api-key="<API_KEY>" \
  okx.secret-key="<SECRET_KEY>" \
  okx.passphrase="<PASSPHRASE>" \
  okx.base-url="https://www.okx.com"
```

### 4.2 Postgres

Путь: `secret/tradingbot/postgres`

```bash
docker exec -it vault vault kv put secret/tradingbot/postgres \
  spring.datasource.url="jdbc:postgresql://localhost:5440/tradingbot" \
  spring.datasource.username="postgres" \
  spring.datasource.password="password"
```

---

## 5) Запуск приложения

В отдельном терминале выставь токен Vault:

```bash
export VAULT_TOKEN="<ROOT_TOKEN_OR_APP_TOKEN>"
```

Запусти приложение:

```bash
mvn spring-boot:run
```

---

## 6) Диагностика

Проверить чтение секрета:

```bash
docker exec -it vault vault kv get secret/tradingbot/okx
```

Статус Vault:

```bash
docker exec -it vault vault status
```
