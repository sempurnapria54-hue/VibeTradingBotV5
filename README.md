# Vibe Trading Bot

## Запуск зависимостей

```bash
docker compose up -d
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

* `GET http://localhost:8080/actuator/health`
* `GET http://localhost:8080/api/health`
