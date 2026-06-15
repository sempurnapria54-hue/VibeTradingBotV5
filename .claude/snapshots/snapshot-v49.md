# Snapshot v49

**Дата:** 2026-06-13.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — контур тестов API источника
(`source-api-testing`): пилот OKX, RUN.** Заход — **continuation**
(RUN не закрыт). Снапшот несёт состояние и контекст разбора, чтобы
новый чат после PK-префлайта **поднял маршрутизацию находок RUN** без
пересказа. Делается **в дополнение к v48** (последний в PK). Детали
прогона — `.claude/work/progress/source-api-pilot-run-log.md` (не
дублируются, ссылка).

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; **шаги 1-4 — `DONE`**, шаги 5-11 —
`HOLD` (без изменений против v48). Ветка — `claude-audit`. Вся работа
этого захода (фиксы F1/F3a/F4 + диагностика F3b + завершение demo-
прогона + этот снапшот) — **staged, не коммичено** (CC не коммитит).

Контур `source-api-testing` создан и отлажен (см. v48). Этот заход —
**живой RUN пилота OKX**: demo-контур пройден целиком, по ходу сняты
несколько блокеров и закрыты три фикса.

## Путь к точке (от v48)

v48 оставил RUN на паузе: Фаза 1 demo частично (негативы N1/N2 + place
C1 непрозрачно реджектится — F1/F2/F3), Фаза 2 I3 confirmed-by-code,
Фаза 3 prod — в новом чате. С тех пор:

1. **F1 — фикс видимости write-реджекта** (только видимость): новый
   `OkxWriteLoggingInterceptor` логирует сырое тело OKX на write-ops
   (POST) + `BufferedClientHttpResponse` для повторного чтения
   downstream; top-level `code`/`msg` fallback в `OrderMapper`/
   `AlgoOrderMapper.integrationToAck`. `mvn compile` зелёный.
2. **Probe** живого write-реджекта → захвачен raw OKX: per-element
   `sCode=51010` присутствует в теле, но в ack доезжал top-level `code=1`
   → корневой узел: `sCode` десериализуется в null.
3. **F3a — фикс**: `sCode`/`sMsg` не биндились (**Jackson 3 × Lombok
   beanspec** мангли́нг, подтверждён юнит-срезом обоими Jackson). Явный
   `@JsonProperty` на `OrderAckOkxResponse`/`AlgoOrderAckOkxResponse`.
4. **F4 — interim-фикс**: тот же корень бил по 5 read-DTO
   (`cTime`/`uTime`). `@JsonProperty` + round-trip тесты
   (`OkxReadDtoDeserializationTest`). Системный класс заведён в
   `backlog.md` §Инфра-долг **I4**.
5. **F3b — диагностика** (read `account/config`): фактический блокер —
   **уровень аккаунта** (`acctLv=1` Spot-mode → SWAP недоступен →
   `51010`), не `posMode` и не права. Добавлен диагностический
   `getAccountConfig()` (не продуктизация).
6. **demo-цепочка — пройдена целиком (попытка 4)**: пользователь
   последовательно снял на стороне demo-аккаунта `51010` (acctLv→2) →
   `50033` (instrument restriction, web-UI) → place проходит. **C1→C4**
   (place→live→cancel→canceled) и **И-2 A1→A4** (trailing через
   `cancel-advance-algos`) — все зелёные, teardown чист, орфанов нет.
7. **Фаза 3 prod read-only — ОТЛОЖЕНА** (решение пользователя; см. ниже).

## RUN — итог прогона (контекст ветки)

Faithful-прогон через клиентский слой (`OkxProxyController` →
`IntegrationService`/`OkxRestClient`), объект — API OKX. Среда demo:
профиль `test`, `:8080`, app поднимает CC через `tools/boot-test.sh`
(локальный gitignored Vault-token файл) под захваты, останавливает
после. Факты — run-log; статус задачи —
`.claude/work/progress/source-api-testing-pilot.md`.

- **Фаза 1 demo — завершена целиком:** негативы N1/N2 + C-цепочка
  C1→C4 + И-2 A1→A4, все зелёные, teardown чист.
- **Фаза 2 I3 — done** (confirmed-by-code; NPE на пустом секрете,
  `backlog.md` §I3).
- **Фаза 3 prod read-only — ОТЛОЖЕНА.** Prod-write должен оставаться
  структурно невозможным; для read-only фазы нужен **read-only
  prod-ключ** либо ручной бут пользователем. Пользователь пока ключи
  не заводит — припарковано. *Уточнение safety-правила:* автономное
  чтение prod допустимо, только если prod-ключ read-only (структурный
  no-write); пока не настроено.
- **Среда demo закрыта:** `tools/boot-test.sh` + локальный gitignored
  Vault-token файл + диагностический `getAccountConfig()`.

## Находки на маршрутизацию (ступень Предложение — НЕ финализировать)

Это и есть материал, который новый чат маршрутизирует по владельцам
(преим. `integrator`). Заземление — run-log.

- **F1** — write-реджект был непрозрачен → **fixed** (видимость:
  `OkxWriteLoggingInterceptor` + top-level fallback в мапперах).
- **F2** — `getOrder` на not-found → HTTP 500 (`verifyCode` бросает на
  51603) → **open**; упирается в error-политику (шаг 6). Бьёт также в
  D-B3 (recovery-by-clientId требует null-on-not-found).
- **F3a** — per-element `sCode` не биндился (Jackson 3 × Lombok) →
  **fixed** (`@JsonProperty`).
- **F3b / В-9** — **open**: адаптер хардкодит `posSide=net`/
  `tdMode=isolated`, нет bootstrap-проверки `acctLv` → глухой 51010;
  должен fail-fast с внятным сообщением. (Слои 51010 account-mode и
  50033 instrument-restriction сняты пользователем на стороне
  demo-аккаунта — не код.)
- **F4** — системный Jackson 3 × Lombok мангли́нг → **fixed interim**
  (5 read-DTO `cTime`/`uTime` + round-trip тесты, backlog §I4);
  **open:** recurrence-guard (глобальный Jackson-конфиг vs конвенция) +
  sweep будущих/иных источников.
- **A3** — `cancel-advance-algos` **жив на demo** вопреки офдоку OKX
  2025-04-24 → **open**: правка `docs/integrations/okx/contracts/algo-order.md`
  в сторону «эндпоинт живой» (факт из прогона, не выдумка).
- **reduce-only trailing без позиции** принимается на demo (A1 rests
  pending; рантайм-резолюция A1 не понадобилась) — **наблюдение**.
- **C2** — адаптер-инварианты `tdMode`/`posSide`/`reduceOnly` не
  маппятся в `OrderExternalSnapshot` (adapter-validation, не в снапшот)
  → C2-проверка инвариантов по снапшоту ненаблюдаема — **наблюдение**.

## Контекст для разбора (новый чат)

Самый диалоговый кусок — **маршрутизация находок**. Опорные точки по
содержательности:

- **В-9 / F3b** — самый содержательный: проектирование
  bootstrap-проверки адаптера (fail-fast на несоответствии `acctLv` /
  account mode вместо глухого 51010). Владелец — `integrator`/код.
- **F4 recurrence-guard** — открытый под-вопрос `integrator`
  (глобальный Jackson-конфиг vs конвенция `@JsonProperty`; sweep иных
  источников). `backlog.md` §I4.
- **A3** — doc-правка `algo-order.md` (эндпоинт живой). Владелец —
  `integrator`.
- **F2** — к error-политике (шаг 6, TBD в `codestyle.md`).

**Следующая фаза работы:** консолидация run-log → **отчёт прогона** →
**ревью отчёта** (`test-review`) → **маршрутизация находок** по
владельцам (ступень Предложение, не финализировать) → `history`. Затем
prod-фаза — по готовности read-only-ключа; на горизонте — полный план
по 26 контрактам вторым заходом.

## Гейты делегирования

- **`tester` — Предложение, 1 из 3 чистых валидаций** (аппрув
  пилотного плана, без изменений против v48).
- **`integrator`** — Предложение, 0 (строка в леджере); находки RUN
  идут к нему на маршрутизации.
- Прочие роли — без изменений против v48 (`solution-designer` 1/3).

## Открытые вопросы

Без изменений против v48 (CMD-Q5/Q6, PHASE-Q1/Q2, STRUCT-Q1, IND-Q1,
RISK-Q1/Q2, INSTR-Q1/Q2, ORCH-Q1, DEAL-Q1/Q2/Q3, OKX-Q1/Q2/Q3/Q4,
STRAT-Q4, CMD-Q2/Q4). Находки пилота (F1/F2/F3a/F3b/F4/A3 + наблюдения)
— не открытые вопросы, а материал на маршрутизацию в разборе отчёта.

## Режим работы

Содержательное исполнение (живой RUN пилота), не отладка пайплайна.
**Первая задача нового чата:** поднять **маршрутизацию находок RUN** —
консолидация run-log → отчёт → ревью (`test-review`) → разбор находок
по владельцам (преим. `integrator`), **не финализировать** (ступень
Предложение). Опорные точки разбора — В-9/F3b, F4 recurrence-guard, A3,
F2 (см. «Контекст для разбора»). Затем — отложенная prod-фаза (по
read-only-ключу) и полный план по контрактам.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v49`**
  (заменяет v48 в префлайте — обновить PK после коммита).
- **Staged, не коммичено** (этот и предыдущий заход): фиксы кода
  (`OkxWriteLoggingInterceptor`, `BufferedClientHttpResponse`,
  `@JsonProperty` на ack- и read-DTO, мапперы, `OkxIntegrationService`,
  `OkxRestClient`/`getAccountConfig`, `OkxProxyController`,
  `OkxConfig`, `Constants`), новые тесты
  (`OkxAckDeserializationTest`, `OkxReadDtoDeserializationTest`),
  `tools/boot-test.sh`, `.env.vault.test.local.example`, обновлённый
  run-log, `backlog.md` (§I4, §Средовой дефицит), этот снапшот.
- Напоминание `external-source-sync`: интеграционные доки OKX —
  источник правды офдок; A3 требует правки `algo-order.md` (эндпоинт
  `cancel-advance-algos` живой на demo вопреки офдоку 2025-04-24).
