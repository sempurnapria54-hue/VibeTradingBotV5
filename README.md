# Vibe Trading Bot

## Запуск зависимостей (Postgres + Vault)

```bash
docker compose up -d
```

Vault UI будет доступен по адресу: <http://localhost:8200>

Подробная инструкция по инициализации Vault, unseal и записи секретов:

- `docs/ops/vault_local.md`

## Подготовка переменной окружения VAULT_TOKEN

После инициализации и логина в Vault экспортируйте токен:

```bash
export VAULT_TOKEN=<ROOT_TOKEN>
```

## Запуск сервиса

```bash
./mvnw spring-boot:run
```

Если Maven Wrapper недоступен, используйте:

```bash
mvn spring-boot:run
```

## Проверка

- `GET http://localhost:8080/actuator/health`
- `GET http://localhost:8080/api/health`
