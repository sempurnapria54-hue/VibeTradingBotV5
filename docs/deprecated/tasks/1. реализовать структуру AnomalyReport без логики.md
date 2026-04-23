# Задание для Codex — реализовать структуру AnomalyReport без логики

## Цель

Нужно реализовать **только структуру** для `AnomalyReport` по примеру остальных сущностей проекта.

На этом этапе **не нужно** реализовывать бизнес-логику обработки аномалий.

Нужно подготовить:

* доменную модель;
* enum'ы;
* persistence entity;
* repository;
* data service;
* mapper;
* базовый service-слой без сложной логики.

---

## Важно

### Не нужно делать сейчас

Не реализовывать:

* `TradeRuleValidator`;
* `KillSwitchService` интеграцию;
* полную бизнес-логику `AnomalyService`;
* обработку статусов по флоу;
* сериализацию snapshot-объектов в runtime;
* job;
* orchestration.

На этом этапе нужна **только модель и слои** вокруг неё.

---

## Архитектурные правила

### 1. Следовать текущему стилю проекта

Использовать принятый в проекте подход:

* Java / Spring Boot;
* DDD;
* отдельные domain / persistence / mapping / data service слои;
* Lombok;
* MapStruct;
* сущности с комментариями над полями;
* сервисы в стиле проекта.

### 2. Не использовать множественные объявления переменных

Плохо:

```java
int a = 1, b = 2;
```

Хорошо:

```java
int a = 1;
int b = 2;
```

### 3. Всегда ставить фигурные скобки

Для `if`, `else`, циклов и других управляющих конструкций.

### 4. Не переписывать лишний код

Нужно вернуть только нужные новые классы и минимально необходимые фрагменты.

---

## Что нужно реализовать

### 1. Domain model: `AnomalyReport`

Создать доменную модель `AnomalyReport`.

Обязательные поля:

* `id`
* `exchangeId`
* `instrumentId`
* `status`
* `severity`
* `code`
* `message`
* `internalBefore`
* `externalBefore`
* `internalAfter`
* `externalAfter`

Если в проекте есть общий `Auditable`, использовать его.

Обязательно добавить комментарии над каждым полем.

---

### 2. Enum: `AnomalyReport.Status`

Добавить enum со значениями:

* `CREATED`
* `IN_PROGRESS`
* `KILL_SWITCH_EXECUTED`
* `COMPLETED`
* `ERROR`

У каждого значения нужен комментарий.

---

### 3. Enum: `AnomalyReport.Severity`

Добавить enum со значениями:

* `CRITICAL`
* `NON_CRITICAL`

У каждого значения нужен комментарий.

---

### 4. Persistence entity: `AnomalyReportEntity`

Создать entity для таблицы `anomaly_reports`.

Требования:

* таблица `anomaly_reports`;
* `exchange_id` — not null;
* `instrument_id` — nullable;
* `status` — not null;
* `severity` — not null;
* `code` — not null;
* `message` — nullable;
* `internal_before` — jsonb;
* `external_before` — jsonb;
* `internal_after` — jsonb nullable;
* `external_after` — jsonb nullable;
* auditing поля по стандарту проекта.

Добавить комментарии над полями.

Если в проекте уже используется общий базовый auditable entity — следовать этому стилю.

---

### 5. Repository

Создать repository для `AnomalyReportEntity`.

Нужны методы:

* `findById(...)`
* `save(...)`
* поиск по статусам обработки;
* поиск по `exchangeId`;
* поиск по `instrumentId`

Достаточно минимального набора, без сложной фильтрации.

---

### 6. Data service

Создать `AnomalyReportDataService` по примеру остальных data service в проекте.

Ожидаемые методы:

* `save(AnomalyReport report)`
* `getRequiredById(Long id)`
* `findByStatuses(...)`
* `findByExchangeId(...)`
* `findByInstrumentId(...)`

На этом этапе логика простая: repository + mapper.

---

### 7. Mapper

Создать `AnomalyReportMapper` на MapStruct.

Нужны методы:

* `toDomain(AnomalyReportEntity entity)`
* `toEntity(AnomalyReport domain)`
* update-метод для mutable-полей

В update-методе обновлять только:

* `status`
* `message`
* `internalAfter`
* `externalAfter`

Если проект использует специальные common mapper util/helper — следовать текущему стилю.

---

### 8. Базовый service-слой

Создать `AnomalyService` без сложной логики.

На этом этапе достаточно подготовить каркас методов:

* `create(...)`
* `markInProgress(...)`
* `markKillSwitchExecuted(...)`
* `complete(...)`
* `markError(...)`

Сейчас можно оставить простую реализацию через `AnomalyReportDataService`.

Не нужно реализовывать интеграции с другими сервисами.

---

## Требования к json-полям

Пока хранить `internalBefore`, `externalBefore`, `internalAfter`, `externalAfter` как строки JSON.

Не нужно сейчас проектировать отдельные snapshot-классы.

---

## Что должно получиться в итоге

После выполнения задания в проекте должны появиться:

* доменная модель `AnomalyReport`;
* enum'ы `Status` и `Severity`;
* entity `AnomalyReportEntity`;
* repository;
* data service;
* mapper;
* каркас `AnomalyService`.

Без бизнес-логики обработки аномалий.

---

## Что вернуть

Вернуть только нужные новые классы и минимальные изменения.

Не переписывать лишние существующие классы.
