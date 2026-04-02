# Codex — Code style

Правила оформления кода и общие технические конвенции проекта.

---

## 1) Язык и стиль

* Java 21, Spring Boot 3.x.
* Код пишем читаемо, без «магии», придерживаемся Clean Code.
* Между объявлением класса и его первой строкой должен быть интервал - 1 пустая строка.
* Импорты не схлопываем.
* Аннотацию @Data не используем, вместо неё указывай отдельные нужные.
* Никаких magic number - всё в константы с осмысленным названием

---

## 2) Обязательные правила (строго)

### 2.1. Никаких множественных объявлений переменных через запятую

❌ Нельзя:

```java
int a = 1, b = 2;
```

✅ Нужно:

```java
int a = 1;
int b = 2;
```

### 2.2. Фигурные скобки всегда обязательны

Фигурные скобки ставим **всегда** после `if/else/for/while/do`.

❌ Нельзя:

```java
if (x > 0) return;
```

✅ Нужно:

```java
if (x > 0) {
    return;
}
```

❌ Нельзя:

```java
if (data == null) {
    ... 
};
```

```java
if (data != null) {
        ...
};
```

✅ Нужно:

```java
if (isNull(data)) {
    ... 
};
```

```java
if (nonNull(data)) {
        ...
};
```

❌ Нельзя:

```java
a.equals(b);
```

✅ Обязательно:

```java
Objects.equals(a, b);
```

❌ Нельзя:

```java
collection != null  && !collection.isEmpty();
```

✅ Нужно:

```java
использовать CollectionUtils.isNotEmpty(collection) из Apache Commons Collections
```

❌ Запрещено:
!"0".equals(x)
!"0".equals(response.getCode())
любой вариант “литерал слева + equals” для OKX code

✅ Обязательно:
использовать isFalse() / isTrue() из Apache Commons BooleanUtils

### 2.2.1. Null-check policy (строго)

Для null-проверок в Java используем только:

- `Objects.isNull(x)`
- `Objects.nonNull(x)`

### 2.2.2. Policy по отрицаниям (строго)

Избегаем прямого отрицания в условиях для boolean/equals-проверок.

Запрещено:

- `!flag`
- `!Objects.equals(a, b)`
- `!"CONST".equals(value)`

Нужно:

- `BooleanUtils.isFalse(flag)` / `BooleanUtils.isTrue(flag)`
- `BooleanUtils.isFalse(Objects.equals(a, b))`
- для сравнения строк: сначала `Objects.equals(...)`, затем при необходимости оборачивать в `BooleanUtils.isFalse(...)`

Запрещено использовать `== null` и `!= null` в любых выражениях, не только в `if`:

- в `if/else`
- в тернарных выражениях
- в присваиваниях
- в составных boolean-условиях

❌ Нельзя:

```java
if (data == null) {
    ...
}
```

```java
if (data != null) {
    ...
}
```

```java
String body = bodyObject == null ? "" : toJson(bodyObject);
```

✅ Нужно:

```java
if (Objects.isNull(data)) {
    ...
}
```

```java
if (Objects.nonNull(data)) {
    ...
}
```

```java
String body = "";
if (Objects.nonNull(bodyObject)) {
    body = toJson(bodyObject);
}
```

### 2.3. Lombok вместо ручных конструкторов/геттеров/сеттеров

Вместо ручной генерации **конструкторов/геттеров/сеттеров/equals/hashCode/toString** предпочтительно использовать
Lombok.

Рекомендации:

* DTO/REST/Domain модели: `@Getter/@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` (по необходимости).
* Для неизменяемых моделей: `@Value` (если подходит).
* Для билдеров: `@Builder`.

Ограничения:

* Lombok не должен скрывать бизнес‑логику: сложные методы пишем явно.
* Не злоупотребляем `@Data` на доменных сущностях (использовать осознанно).

---

## 3) Время и таймфреймы

* Всё время — **UTC** (и в коде, и в БД).
* Таймфреймы OKX **case-sensitive**.
* Используем **только** константы из `com.example.tradingbot.util.OkxTimeframes`.
* Запрещено нормализовывать таймфреймы в lower‑case.

---

## 4) Persistence (Hibernate/JPA)

* Используем **Hibernate (JPA)**.
* Сущности: аккуратно с lazy-loading, не тащим сущности наружу в API.
* Уникальность/идемпотентность обеспечиваем через **UNIQUE индексы** + безопасные upsert-паттерны.

---

## 5) MapStruct и мапперы

Правила для маппинга слоёв (client DTO → domain → REST):

* Для **каждой доменной сущности** — отдельный mapper (например: `OrderMapper`, `PositionMapper`, и т.д.).
* В каждом mapper — отдельные методы под направления маппинга:

    * `clientToDomain(...)`
    * `domainToClient(...)` (если потребуется)
    * `domainToRest(...)`
    * `restToDomain(...)` (если потребуется)
* Название метода должно отражать **какой слой в какой** мапим.
* Методы могут называться одинаково, но иметь разные входные параметры.
* **Client DTO OKX наружу не возвращаем**.
* Даже если REST модель сейчас 1:1 с domain, шаги **client→domain→rest** оставляем обязательными.
* В коде не маппим вручную, только через Mapstruct.

---

## 6) Идемпотентность и рестарт

* Любая джоба/бэкофисный процесс должен корректно переживать рестарт.
* Повторный запуск не должен создавать дубли.
* Ключевые UNIQUE-инварианты фиксируются в документации моделей (`docs/models/domain/`).

---

## 7) Логирование и ошибки

* Логи: без секретов (apiKey/secret/passphrase никогда не логируем).
* Ошибки внешних API логируем с контекстом: endpoint, параметры (без секретов), code/msg.

---

## 8) Контроллеры

* Можно использовать @RequestParam, но если их более 2, то лучше использовать @ParameterObject

---

## 9) Нейминг

* Не нужно сокращать названия в доменном слое, надо использовать названия из описания доменных моделей. Если описания
  такой модели ещё нет, то всё равно надо полные слова, без сокращений.
  Например: Вместо private String bal; нужно private String balanceExternalSnapshot;
* Вот пример моделей и нейминга по слоям. OrderRequest(REST) -> Order (Domain) -> OkxClientOrder (ClientService) ->
  OkxRestClient -> OkxClientOrder (

---

## 10) Зоны ответственности

### Controller

* REST → Domain
* вызывает Service
* Domain → REST
* никогда не видит client DTO и не вызывает OkxRestClient

### Service (application/service)

* принимает Domain
* вызывает ClientService
* добавляет прикладную логику

### ClientService (domain-in / domain-out)

* Domain → client DTO
* вызывает OkxRestClient
* client DTO → Domain
* возвращает Domain наверх

### DataService

* Domain → Persistence
* вызывает Repository
* Persistence → Domain

### OkxRestClient

* чистый HTTP + подпись + DTO

### Repository

* Просто интерфейсы с методами - запросами в бд
* Могут содержать нативные запросы через аннотации над методами

---

## 11) Документация рядом с кодом

Если меняется поведение/контракт — обновляем соответствующий файл в:

* `docs/api/okx/` (если затронуты методы OKX)
* `docs/models/domain/` (если менялись доменные модели/инварианты)
* `codex/stage/` (если это часть этапа)

---

## 12) Конфигурация и секреты (Vault)

* В коде и бинах используем только стандартные Spring properties (например `okx.*`, `spring.datasource.*`).
* Для бизнес-свойств в `application.yaml` запрещено указывать значения формата `vault://...#...`.
* Допускаются плейсхолдеры окружения (`${OKX_API_KEY:}` и т.п.) и/или документационные маркеры вида `{OKX_API_KEY}`.
* Прямые Vault URI допустимы только в `spring.config.import` для подключения источника конфигурации, но не как значения
  доменных/бизнес-полей.
* Реальные секреты в репозиторий не коммитим.

