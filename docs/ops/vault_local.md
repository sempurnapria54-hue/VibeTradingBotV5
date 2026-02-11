# Локальный Vault для TradingBot

## 1. Запуск инфраструктуры

Запусти Postgres и Vault:

```bash
docker compose up -d
```

Проверь, что контейнеры `db` и `vault` в статусе `running`:

```bash
docker compose ps
```

## 2. Доступ к Vault UI

Vault UI: <http://localhost:8200>

Для CLI команд можно использовать локальный бинарь `vault` или выполнять команды в контейнере:

```bash
docker compose exec vault sh
```

Дальше примеры приведены для выполнения внутри контейнера Vault.

## 3. Инициализация Vault (init)

Выполни инициализацию (один раз для нового volume):

```bash
vault operator init -key-shares=1 -key-threshold=1
```

Сохрани:

- `Unseal Key 1`
- `Initial Root Token`

> Не коммить эти значения в репозиторий.

## 4. Unseal

Разблокируй Vault сохранённым ключом:

```bash
vault operator unseal <UNSEAL_KEY>
```

Проверь статус:

```bash
vault status
```

## 5. Логин

Залогинься root токеном:

```bash
vault login <ROOT_TOKEN>
```

## 6. Включение KV v2

Проверь список движков:

```bash
vault secrets list
```

Если `secret/` не существует как KV v2, включи его:

```bash
vault secrets enable -path=secret kv-v2
```

Если `secret/` уже есть, но не KV v2, перемонтируй согласно локальной политике стенда.

## 7. Запись секретов

### OKX (`secret/tradingbot/okx`)

```bash
vault kv put secret/tradingbot/okx \
  OKX_API_KEY="<OKX_API_KEY>" \
  OKX_SECRET_KEY="<OKX_SECRET_KEY>" \
  OKX_PASSPHRASE="<OKX_PASSPHRASE>" \
  OKX_BASE_URL="https://www.okx.com"
```

### Postgres (`secret/tradingbot/postgres`)

```bash
vault kv put secret/tradingbot/postgres \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5440/tradingbot" \
  SPRING_DATASOURCE_USERNAME="postgres" \
  SPRING_DATASOURCE_PASSWORD="password"
```

Проверка значений:

```bash
vault kv get secret/tradingbot/okx
vault kv get secret/tradingbot/postgres
```


Ключи в Vault должны соответствовать плейсхолдерам в `application.yaml`:

- `{OKX_API_KEY}`
- `{OKX_SECRET_KEY}`
- `{OKX_PASSPHRASE}`
- `{OKX_BASE_URL}`
- `{SPRING_DATASOURCE_URL}`
- `{SPRING_DATASOURCE_USERNAME}`
- `{SPRING_DATASOURCE_PASSWORD}`

## 8. Запуск приложения с Vault

Экспортируй токен в локальной shell-сессии:

```bash
export VAULT_TOKEN=<ROOT_TOKEN>
```

После этого запускай приложение:

```bash
./mvnw spring-boot:run
```

или

```bash
mvn spring-boot:run
```

Приложение загрузит datasource и OKX настройки из Vault.
