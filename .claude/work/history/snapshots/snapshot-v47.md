# Snapshot v47

**Дата:** 2026-06-12.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — инфра-сессия: dev/test-сплит
БД, Vault-привязка секретов (datasource + OKX) и закрытие пробелов
Boot-4 split-autoconfig; рантайм-хвост шага 4 закрыт — завершена.**
Первый реальный рантайм-старт оба профиля поднял зелёными; торговый
write-путь (OKX-прокси) подтверждён end-to-end. Плановое завершение
темы — следующий чат стартует с PK-префлайта. **Тема следующего чата
(по запросу пользователя): концепция тестирования.**

## Состояние

Фаза 1 — `IN_PROGRESS`; **шаги 1-4 — `DONE`** (рантайм-хвост шага 4
**закрыт** этой сессией), шаги 5-11 — `HOLD`. Ветка — `claude-audit`.
Код шага 4 закоммичен ранее пользователем (`aad5849` «ROADMAP 1-4-7
DONE», `f2558be` «ROADMAP 1-4-4 CODE»); работа этой сессии (compose,
yaml-сплит, pom Boot-4 модули, `OkxProperties`, парк-знание, этот
снапшот) — **staged, не коммичено** (CC не коммитит).

## Путь к точке (от v46)

1. **Dev/test-сплит БД (compose).** Удалён `db`; заведены `postgres`
   (5440 / `pgdata_prod` / `.env.prod.local`) и `postgres-test` (5441 /
   `pgdata_test` / `.env.test.local`); `vault` без изменений.
   `.env.local`→`.env.prod.local`, создан `.env.test.local`, оба
   gitignored.
2. **`application.yaml` разнесён** на базовый + `application-prod.yaml` +
   `application-test.yaml`. Vault-подключение (uri/token/kv) — общее;
   профиле-зависимое (Vault-пути, datasource, OKX-креды, `simulated`,
   джобы, уровень логов) — в профильных файлах. Запуск требует активного
   профиля (prod|test).
3. **Vault-привязка datasource** (раньше — env-дефолты, Vault отложен на
   шаг 9; теперь вытянут на инфра-шаг): prod→`secret/tradingbot/postgres`,
   test→`secret/tradingbot/postgres-test`; `${DATASOURCE_*}`.
4. **Vault-привязка OKX-кредов:** prod→`secret/tradingbot/okx`,
   test→`secret/tradingbot/okx-test`; `spring.config.import` стал списком
   из двух путей на профиль; мэппинг `OKX_API_KEY/OKX_SECRET_KEY/
   OKX_PASSPHRASE` → `okx.api-key/secret/passphrase` в корневом yaml
   (одинаков для профилей); `simulated` профиле-зависим (prod real / test
   demo). Раньше креды были null → подпись NPE-ила.
5. **Boot-4 split-autoconfig — 3 пробела закрыты** (всплывали по одному
   на каждом boot, компиляция не ловит): `RestClient.Builder`
   (`spring-boot-starter-restclient`), Jackson 2 `ObjectMapper`
   (`spring-boot-jackson2` — Boot 4 дефолтит Jackson 3), Flyway
   (`spring-boot-starter-flyway`; standalone `flyway-core` убран,
   `flyway-database-postgresql` оставлен). + `spring-cloud-vault` (BOM
   `spring-cloud-dependencies:2025.1.2` под SB4). Комментарии
   Vault-deferral переписаны инлайн.
6. **Закрытие сессии:** парк-знание (реконсиляция Vault→шаг 9; Jackson
   2/3; Boot-4 долг + durable-проверка; робастность интерсептора;
   оп-заметка OKX-ключ), этот снапшот.

## Реконсиляция «Vault → шаг 9»

Vault-привязка секретов (datasource **и** OKX-креды) введена на этом
инфра-шаге, **раньше планового шага 9**. Шаг 9 «Безопасность»
**рескоупится** на остаточный хардненинг (политики/approle вместо
root/dev-token, ротация, unseal/инициализация не в dev, вынос
Vault-токена из run-config, Spring Security), **не на введение Vault**.
Тезис выровнен везде, где был записан: `pom.xml`-шапка + комментарии
`OkxProperties`/yaml (инлайн); `tech-radar.md` (запись
spring-cloud-vault); `backlog.md` §S1 (рескоуплен — базовая привязка
✅ закрыта); `phase-1.md` (строка шага 9 + примечание).

## Boot 4 split-autoconfig (долг + durable-проверка)

Переезд Boot 3→4 / Spring 7 / Hibernate 7 / JDK 25 раньше не гонялся в
рантайме. Шаблон пробела: **библиотека на classpath есть, её
`spring-boot-*` автоконфиг-модуль не подтянут → бин/фича молча не
активируется** (особенно коварны «тихие» стартовые автоконфиги без
инжекта бина — Flyway: без модуля не падает, просто ничего не делает).
**Durable-проверка на будущее:** при добавлении/обновлении зависимости —
«библиотека на classpath → её `spring-boot-*` автоконфиг-модуль
подтянут?». Зафиксировано — `backlog.md` §Инфра-долг I1. Чистый
end-state Jackson (миграция кода на Jackson 3, снятие `jackson2`) — I2;
робастность `OkxSigningInterceptor` на пустых кредах — I3.

## Рантайм-подтверждение (закрывает рантайм-хвост шага 4 из v46)

Первый реальный boot обоих профилей — зелёный. Vault-токен — через IDEA
run config env (`VAULT_TOKEN`), две run-конфиги `[prod]`/`[test]`.

- **Flyway применил `V1`-`V7`** на обеих БД (`tradingbot` /
  `tradingbot_test`), схема создана — **снят v46-хвост Flyway
  `V6`/`V7` + валидация схемы**.
- **OKX-прокси (prod) вернул реальный balance** — подпись + Vault-креды
  + Jackson-сериализация ответа работают **end-to-end** (закрыт хвост
  прокси-тестирования командного слоя).
- **И-2** (demo trailing `cancel-advance-algos`) — **разблокирован** (у
  `test` теперь demo-креды); сама функциональная demo-проверка остаётся
  обычным пунктом `backlog.md` §Хвост шага 4, не блокирующим хвостом.

Остаётся за пользователем: коммит staged-хвоста + обновление PK
(snapshot v46 → v47).

## Backlog-добавления (эта сессия)

- **§Инфра-долг** (новый раздел): I1 Boot-4 split-autoconfig
  durable-проверка; I2 миграция кода на Jackson 3 (снять `jackson2`);
  I3 `OkxSigningInterceptor` fail-fast на пустых кредах.
- **§S1** рескоуплен (Vault базовая привязка ✅ закрыта; остаточный
  хардненинг — шаг 9).
- Оп-заметка: `.claude/notes/2026-06-12-okx-api-key-14d-expiry.md` (OKX
  удаляет trade-ключи без IP-привязки через 14 дней неактивности —
  причина протухания старого ключа).

## Открытые вопросы

Без изменений против v46 (новых парк-вопросов сессия не породила —
работа инфраструктурная): CMD-Q5/Q6 (парк на 6-7), PHASE-Q1/Q2,
STRUCT-Q1, IND-Q1, RISK-Q1/Q2, INSTR-Q1/Q2, ORCH-Q1, DEAL-Q1/Q2/Q3,
OKX-Q1/Q2/Q3/Q4, STRAT-Q4, CMD-Q2/Q4. Форвард-долг ревью шага 4
(D-B3/D-M1 как гейты 6/7) и ретро-ревью 1-3 — `backlog.md`.

## Гейты делегирования

Без изменений против v46 (`solution-designer` — 1 из 3 чистых валидаций;
прочие роли — без изменений). Сессия — исполнительная инфра-работа, не
прогон пайплайна.

## Режим работы

Содержательное закрытие (не отладка пайплайна). Инфра-тема (dev/test +
Vault + Boot-4 + рантайм-хвост шага 4) — завершена. **Следующая тема (по
запросу пользователя): концепция тестирования** (ср. отложенные
артефакты шага 10 — `testing-strategy`/`test-writer`/`test-review`,
`.claude/notes/2026-05-29-ростер-тулинга-роадмап.md`). Альтернатива —
старт **шага 5 (риск-преконтроль)** штатным процессом.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v47`**
  (заменяет v46 в префлайте — обновить PK после коммита). Затронут
  PK-файл `tech-radar.md` (Vault-реконсиляция + Jackson) —
  синхронизировать, если в PK.
- **Staged, не коммичено:** `docker-compose.yml`, `.gitignore`,
  `application.yaml` + `application-{prod,test}.yaml`, `pom.xml`,
  `OkxProperties.java`; парк-знание (`tech-radar.md`, `backlog.md`,
  `phase-1.md`, оп-заметка); этот снапшот. (`.env.*.local` —
  gitignored, у пользователя.)
- Напоминание `external-source-sync`: интеграционные доки OKX —
  источник правды офдок (последняя сверка 2026-06-11).
