# Snapshot v48

**Дата:** 2026-06-12.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли. **Тема — контур тестов API источника
(`source-api-testing`): создан, отлажен на пилоте OKX; RUN на паузе.**
Заход — **продолжение** (RUN не закрыт), снапшот несёт контекст ветки
для подъёма без пересказа. Новый чат продолжает RUN с Фазы 3.

## Состояние

Фаза 1 роадмапа — `IN_PROGRESS`; **шаги 1-4 — `DONE`**, шаги 5-11 —
`HOLD` (без изменений против v47). Ветка — `claude-audit`. Вся работа
этого захода (контур + пилот + run-log + правки PK-файлов + этот
снапшот) — **staged, не коммичено** (CC не коммитит).

Контур `source-api-testing` создан и отлажен на пилоте OKX. Артефакты:
роль `tester`, скиллы `test-design`/`test-run`, фокус `test-review`
(материализован в `reviewer.md`), процесс `source-api-testing`, доковый
шаблон `test-plan`, **новый тип знания
`.claude/tests/{testType}/{source}/`** (decision `test-knowledge-type.md`;
строка в `structure.md`), категория делегирования **«Тестирование»**
(`tester`, ступень Предложение).

## Путь к точке (от v47)

1. **Контур создан** (блок А): роль/скиллы/фокус/процесс/шаблон + тип
   знания + делегирование (категория «Тестирование» в
   `question-delegation.md`, строка в `delegation-ledger.md`). Заодно
   закрыт рассинхрон леджера — добавлена строка «Интеграция»
   (`integrator`).
2. **Переименование + переезд типа** (по аппруву): контур назван
   «тесты API источника» (процесс `integration-testing` →
   `source-api-testing`); тип знания переехал
   `docs/integrations/{name}/tests/` → `.claude/tests/{testType}/{source}/`
   (артефакт проверочной деятельности, не продуктовое знание;
   `{testType}` на вырост, текущий — `source-api`).
3. **Пилот OKX:** DESIGN + REVIEW (петля `test-review`, 6 находок) +
   **APPROVE** (аппрув без существенной правки → гейт `tester` 1/3).
   План — `.claude/tests/source-api/okx/pilot-plan.md` (нога amend
   убрана вариантом (a); A1 trailing — рантайм-резолюция).
4. **RUN — гибрид** (решение по эскалации): app поднимает пользователь
   (IDEA run-config), вызовы через `OkxProxyController` на localhost
   делает CC, фазами под подтверждение профиля.
5. **Прогон (на паузе):** Фаза 1 demo частично + Фаза 2 I3
   (confirmed-by-code). Три находки (ниже). RUN встал на паузу для
   переезда.

## Пилот / RUN — контекст ветки (для подъёма)

Faithful-прогон идёт **через клиентский слой** (`OkxProxyController` →
`IntegrationService`/`OkxRestClient`), объект — API OKX, не домен.
Среда demo: профиль `test`, demo-креды, `:8080`. Факты — run-log
`.claude/work/progress/source-api-pilot-run-log.md`; статус задачи —
`.claude/work/progress/source-api-testing-pilot.md`.

**Сделано:**
- Фаза 1 demo: негативы N1 (cancel несущ.) / N2 (getOrder фейк) + place
  C1 ×2 (до и после выравнивания demo-аккаунта). Teardown **чист** —
  оба place ничего не создали (orphan-check: оба `clOrdId` not-found,
  позиции нет).
- Фаза 2 I3: **confirmed-by-code** (live deferred).

**Не сделано (новый чат):** Фаза 3 prod read-only (P1–P4) → отчёт →
ревью отчёта → маршрутизация находок → `history`.

**Отложено в demo (следующий прогон):** цепочка C2–C4 и И-2 (A1–A4) —
блокер F3-слой-2.

## Три находки (заземлены кодом, все в нашем клиентском слое)

Системная тема — **write-путь слеп к реджектам**. Пока **не размещены**
— маршрутизация в разборе отчёта (новый чат), ступень Предложение.

- **F1 — `ExchangeAck` теряет код/`msg` реджекта.** Write-путь
  (`placeOrder:147`, `cancelOrder:155`, `placeAlgoOrder:163`,
  `cancelAlgoOrder:175`, `closePosition:188`) минует `verifyCode`;
  `toOrderAck:212` логирует/кидает `writeFailure` **только на пустом
  `data[]`**; непустой `data[0]` с null `sCode` проходит молча →
  `success:false, code:null`, без лога. DTO `OrderAckOkxResponse`
  именован верно → null `sCode` приходит выше по стеку; raw-тело не
  логируется → корень недиагностируем извне.
- **F2 — HTTP 500 вместо чистого not-found.** `getOrder` →
  `verifyCode` (`OkxIntegrationService.java:347`) кидает
  `ExchangeIntegrationException` на 51603; `OkxProxyController.getOrder:59`
  не ловит → 500. Бьёт в **D-B3** (recovery нужен null-on-not-found) и
  в TBD error-политику (шаг 6).
- **F3 — place блокирует demo write-цепочку, два слоя.** (1) **В-9** —
  до выравнивания OKX UI показал конфиг-причину → адаптер хардкодит
  `posSide=net`/`tdMode=isolated`, SWAP-capable без bootstrap-проверки
  (пользователь выровнял аккаунт). (2) **Остаточный дефект place-пути**
  — place падает непрозрачно **и после** выравнивания → причина не
  аккаунт, а дефект сборки запроса / парсинга ответа, недиагностируемый
  снаружи из-за F1. Владелец — integrator/код.

## Backlog-добавления (этот заход)

- **§Средовой дефицит автономного RUN тестов** (новый): `mvnw`/wrapper
  в репо; проброс Vault-токена в окружение прогона; **правило
  безопасности** — `tester` автономно бутает только `test`-профиль
  (prod-write технически невозможен), `prod` — никогда автономно. Снимает
  зависимость demo-прогонов от ручного бута; prod-фаза остаётся за
  пользователем навсегда.

## Открытые вопросы

Без изменений против v47 (CMD-Q5/Q6, PHASE-Q1/Q2, STRUCT-Q1, IND-Q1,
RISK-Q1/Q2, INSTR-Q1/Q2, ORCH-Q1, DEAL-Q1/Q2/Q3, OKX-Q1/Q2/Q3/Q4,
STRAT-Q4, CMD-Q2/Q4). Новых парк-вопросов заход не породил. Три находки
пилота (F1/F2/F3) — не открытые вопросы, а находки прогона на
маршрутизацию в разборе отчёта.

## Гейты делегирования

- **`tester` — Предложение, 1 из 3 чистых валидаций** (аппрув пилотного
  плана без существенной правки; `delegation-ledger.md`).
- Строка **«Интеграция»** (`integrator`, Предложение, 0) добавлена в
  леджер — закрыт рассинхрон с картой владельцев.
- Прочие роли — без изменений против v47 (`solution-designer` 1/3).

## Режим работы

Содержательное исполнение (создание контура + прогон пилота), не отладка
пайплайна. **Первая задача нового чата:** продолжить RUN с **Фазы 3**
(prod read-only, app поднимает пользователь на `prod`-профиле, строго
P1–P4) → отчёт прогона → ревью отчёта (`test-review`) → **маршрутизация
находок** (F1/F2/F3 + средовой дефицит RUN), **не финализировать**
(Предложение) → `history`. Затем — отложенная demo-цепочка (C2–C4, И-2)
следующим прогоном; на горизонте — полный план по 26 контрактам вторым
заходом после обкатки формы.

## Синхрон / PK / staged

- **Project Knowledge:** последний снапшот теперь **`snapshot-v48`**
  (заменяет v47 в префлайте — обновить PK после коммита). Затронуты
  PK-файлы: `structure.md`, `question-delegation.md`, ростер-нота
  (`2026-05-29-ростер-тулинга-роадмап.md`), новый decision
  `test-knowledge-type.md`, `backlog.md` — синхронизировать в PK после
  коммита.
- **Staged, не коммичено** (весь заход): новые — `tester.md`,
  `test-design.md`/`test-run.md`/`test-review.md`,
  `processes/source-api-testing.md`, `templates/docs/test-plan.md`,
  `decisions/test-knowledge-type.md`, `tests/source-api/okx/pilot-plan.md`,
  прогресс `source-api-testing-pilot.md` + run-log
  `source-api-pilot-run-log.md`, этот снапшот; правки — `reviewer.md`,
  `structure.md`, `question-delegation.md`, `delegation-ledger.md`,
  ростер-нота, `backlog.md`.
- Напоминание `external-source-sync`: интеграционные доки OKX — источник
  правды офдок (последняя сверка 2026-06-11).
