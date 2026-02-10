# Codex — Tech radar

Список технологий проекта (что используем сейчас и что допускается позже).

---

## 1) Core

* **Java 21**
* **Spring Boot 3.x**
* **Maven**

---

## 2) Data

* **PostgreSQL**
* **Hibernate (JPA)**

*(Миграции и управление схемой — определим отдельным этапом в `codex/stage/`.)*

---

## 3) Integrations

* **OKX REST API v5**

    * подпись запросов (HMAC‑SHA256 Base64)
    * таймфреймы строго как в OKX (case-sensitive)

---

## 4) Libraries

* **Lombok**
* **MapStruct**
* (опционально позже) Spring Retry / resilience4j

---

## 5) Observability

* Spring Boot Actuator
* Логирование (SLF4J/Logback)

---

## 6) Runtime & Ops (минимум)

* Docker Compose для локального Postgres

---

## 7) Project rules (важные ограничения)

* OKX: работаем со SWAP (например, `ETH-USDT-SWAP`), режим **isolated**, плечо ≤ x10 (как рамка риск‑параметров).
* Время: **UTC**.
* Таймфреймы: только `OkxTimeframes`.
* Вместо RestTemplate надо использовать RestClient

---

## 8) Документация по API и моделям

* Сводная таблица методов OKX: `docs/api/okx/okx_api_for_trading_bot_v5.md`
* Сценарии (playbooks): `docs/api/okx/OKX — Сценарии применения операций (Playbooks v1).md`
* Подробные эндпоинты: `docs/api/okx/`
* Доменные модели: `docs/models/domain/`
