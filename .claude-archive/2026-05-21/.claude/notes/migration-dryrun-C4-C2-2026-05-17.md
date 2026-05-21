# Dry-run миграции C4 и C2 от 2026-05-17

## Контекст

Прогон в read-only режиме: я не правлю репозиторий и фиксирую только в этом файле, что бы сделал в реальной миграции. Цель — проверить, насколько инфраструктура (ADR-0006..0009, скиллы spec-cluster-migration / spec-document-migration / spec-models-registry / open-questions-workflow / spec-document-workflow, шаблоны, агенты) даёт однозначные правила для миграции кластеров C4 (Balance) и C2 (Order/AlgoOrder/Position).

Состояние репо на момент прогона:

- ADR-0006..0009 — Proposed (ещё не Accepted). Это сигнал: формально миграция стартовать «не должна», но я по условию прогона веду себя так, будто пакет принят.
- `docs/spec/MODELS.md` создан, содержит 13 core + 3 runtime моделей со стартовыми заглушками. **В MODELS.md есть только `Balance`** — `BalanceContainer` как отдельной модели в реестре нет (см. ниже, это первый видимый рассинхрон с legacy).
- `.claude/planning/migration-tracker.md` создан, все 8 кластеров фазы 1 в статусе `planned`.
- `.claude/questions/open-questions.md` создан, содержит только Q-EXAMPLE.
- `docs/conventions/terminology.md` создан с 4 соглашениями (доменная модель, хранимая модель, core vs runtime, domain-only / external-only).
- В `docs/spec/` уже лежит мигрированный ранее `models/AnomalyReport.md` и `lifecycle/AnomalyReport.md` (для core/ ещё нет подкаталога — `AnomalyReport.md` в `models/` напрямую; это известный долг).
- В фазе 1 по очерёдности ADR-0009 §2 первым идёт **C-NEW (Exchange + Instrument)**, затем C4, затем C2. Условие прогона — пропустить C-NEW и сразу взять C4 и C2. Это сразу делает обе миграции «частично-условными» по hard gate 1 (зависимости в spec нет).
- Legacy-документы кластеров читал в этой сессии: `Balance.md`, `Order.md`, `AlgoOrder.md`, `Position.md`, `Статусы торговых сущностей.md` (целиком), `Сервисные команды.md §12`.

Источник состава кластеров — ADR-0009 §2 и `migration-tracker.md`. Состав совпадает (это хорошо — нет рассинхрона между ADR и трекером).

---

## C4: Balance

### Этап 1. Подготовка

#### Действие 1: Прочитать ADR-0009 §2 для C4

- **Что делал:** перечитал блок «Кластер C4: Balance» в ADR-0009 §2 и соответствующий блок в migration-tracker.
- **Что бы сделал в реальной миграции:** ничего не правлю — это reading-only шаг скилла. Зафиксировал бы в чате: legacy-источники = `Balance.md` + фрагменты `Статусы торговых сущностей.md` (§8.6); целевой documents (планируемые) = `docs/spec/models/core/Balance.md`; зависимости = только Exchange (через FK).
- **На какие правила опирался:** ADR-0009 §2 «Кластер C4», скилл `spec-cluster-migration` Этап 1 п.1, migration-tracker запись «Кластер C4».
- **Уровень уверенности:** однозначно.

#### Действие 2: Проверить готовность зависимостей C4

- **Что делал:** проверил статус кластера C-NEW (Exchange) в трекере. Статус — `planned`. Это значит, что в `docs/spec/models/core/Exchange.md` документа НЕТ.
- **Что бы сделал в реальной миграции:** в штатной миграции по ADR-0009 §4 Gate 1 — стоп, кластер не стартует. Прогон, однако, явно требует пройти C4 как dry-run, поэтому я фиксирую: зависимость Exchange отсутствует, единственная корректная стратегия по ADR-0009 §5 — упоминать Exchange прозаически («баланс относится к exchange account»), без markdown-link и без inline-code, как забегающую ссылку. Knowledge Curator при ревью идентифицирует её по MODELS.md.
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.2 («если зависимости не готовы — старт откладывается, либо явно решается, как обрабатывать забегающие ссылки»), ADR-0009 §4 Gate 1 и §5.
- **Уровень уверенности:** однозначно по правилу, но **сигнал** для отчёта: правило позволяет «явно решить, как обрабатывать забегающие ссылки», но не описывает, как именно это решение оформляется. Я бы оформил его как пометку в чате/в этом отчёте — но скилл не говорит, нужно ли заводить Q-N. Считаю по умолчанию: не Q-N, а просто рабочее замечание (в реальной миграции — в шапке чата).

#### Действие 3: Поставить статус кластера C4 = `in-progress` в трекере

- **Что делал:** в реальной миграции открыл бы `.claude/planning/migration-tracker.md`, заменил «Статус кластера: `planned`» → `in-progress` для C4. Здесь — фиксирую.
- **Что бы сделал в реальной миграции:** правка одной строки. Дата не требуется по формату трекера.
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.3.
- **Уровень уверенности:** однозначно.

#### Действие 4: Определить порядок документов внутри кластера C4

- **Что делал:** в C4 два legacy-источника: основной `Balance.md` + фрагмент `Статусы торговых сущностей.md §8.6 (BalanceContainer)`. `Статусы торговых сущностей.md` — shared-файл, мигрируется по фрагментам в нескольких кластерах (C-NEW, C4, C2, C8). Порядок: сначала основной `Balance.md`, фрагмент из «Статусы…» уже разнесён по §8.6 и переезжает «довеском» в тот же spec-документ Balance.md.
- **Что бы сделал в реальной миграции:** зафиксировал в чате порядок. Это рабочий артефакт сессии, не идёт в файл.
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.4; ADR-0009 §3 («один spec-документ может собирать материал из нескольких legacy-файлов»).
- **Уровень уверенности:** однозначно.

#### Действие 5: Открыть журнал open-questions

- **Что делал:** прочитал шапку `.claude/questions/open-questions.md` (только Q-EXAMPLE).
- **Что бы сделал в реальной миграции:** журнал открыт, готов к появлению Q-N. Следующий свободный id — `Q-001` (Q-EXAMPLE не считается).
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.5, скилл `open-questions-workflow` (генерация следующего id).
- **Уровень уверенности:** однозначно.

#### Действие 6: Подготовка списка ожидаемых влияний на следующие кластеры (Gate 5)

- **Что делал:** ADR-0009 §4 Gate 5 требует составить «список ожидаемых влияний». Для C4 ожидаемые влияния:
  1. C2 (Order/AlgoOrder/Position) — будет ссылаться на Balance? Скорее нет, прямой FK от Order на Balance нет; косвенно — через RiskValidator. **Неблокирующее**.
  2. C7 (Calculator/Risk/Command) — Risk-валидатор использует BalanceContainer; freshness-policy будет упомянута. **Неблокирующее** — концептуальная связь, не структурная.
  3. C1 (Deal) — DealContext содержит BalanceContainer (из Balance.md §11). **Неблокирующее**, забегающая ссылка из C4 на DealContext.
- **Что бы сделал в реальной миграции:** в чате зафиксировал список и пометил все три как неблокирующие.
- **На какие правила опирался:** ADR-0009 §4 Gate 5, скилл `spec-cluster-migration` «Hard gates подробнее».
- **Уровень уверенности:** однозначно для C2 и C1; для C7 — склонялся. Альтернатива: завести concept-Q-N «freshness как кросс-модельный инвариант, отдельный документ или нет». Не завожу, потому что freshness — сквозная концепция, которая по ADR-0009 §9 получает собственный invariant/process-документ при миграции соответствующего кластера (C7/C8); здесь её можно упомянуть прозаически, не блокируя C4.

### Этап 2. Миграция документов

#### Документ A: `docs/domain/models/Balance.md`

##### Действие 7: Анализ исходника `Balance.md` (шаг 1 spec-document-migration)

- **Что делал:** прочитал Balance.md целиком (694 строки). Декомпозировал по смысловым блокам.
- **Что бы сделал в реальной миграции:** запросил у Claude Code отчёт по 6 разделам (промпт-шаблон в скилле `spec-document-migration` шаг 1). Здесь моделирую инвентарь содержания:

| # | Блок | Содержание | Жанр | Куда уезжает | Риск потери |
|---|---|---|---|---|---|
| 1 | §1 Назначение | Что такое BalanceContainer / Balance | metadata | header model-документа | low |
| 2 | §2 Главные инварианты | Список из ~20 правил | structure + lifecycle + cross-model | model-документ (раздел «Инварианты структуры»); freshness и REFRESH_BALANCE — кросс-модельные | medium |
| 3 | §3 Семантика BalanceContainer | Что используется и не используется | metadata | вмерживается в header / «Инварианты структуры» | low |
| 4 | §4 Семантика Balance | Currency-level snapshot | structure | раздел про Balance как вложенную | low |
| 5 | §5 Freshness-policy | Вычисление fresh / stale + Java-интерфейс | cross-model invariant | **Q-N или upstream-зависимость C7/C8?** см. Действие 9 | high |
| 6 | §6 Доменная модель BalanceContainer | Java-сниппет с полями | structure | таблица полей в model-документе | high (легко выпадет деталь) |
| 7 | §7 Доменная модель Balance | Java-сниппет с полями | structure | таблица полей (раздел «Balance как currency-level snapshot») | high |
| 8 | §8 Normalized external snapshots | Java-классы BalanceContainerExternalSnapshot, BalanceExternalSnapshot | structure runtime | **развилка** — см. Действие 9 | high |
| 9 | §9 REFRESH_BALANCE | Описание ServiceCommand: flow, ClientService boundary, executor responsibility, nullable contract | process / cross-model | C7 (ServiceCommand + executor); из Balance.md уходит прозаическим упоминанием | medium |
| 10 | §10 Участие в FSM | Когда handler требует fresh balance | cross-model | C1 (lifecycle Deal) или C7 (process); из Balance уходит прозой | medium |
| 11 | §11 Участие в DealContext | DealContext содержит BalanceContainer | cross-model | C1 (DealContext.md runtime); из Balance уходит прозой | medium |
| 12 | §12 Участие в CalculationContext | Использование как input | cross-model | C7 (calculator process); из Balance уходит прозой | low |
| 13 | §13 Участие в RiskValidator | BalanceContainer как вход для risk | cross-model | C7 (risk-validator process); из Balance уходит прозой | low |
| 14 | §14 Что не храним | Список negative | structure | раздел «Что не хранится в Balance» (опционально, как конструктивный сигнал) | low |
| 15 | §15 Open questions | «Открытых вопросов нет» | metadata | не переезжает | low |

- **На какие правила опирался:** скилл `spec-document-migration` шаг 1 (6 разделов), ADR-0002 §1 (жанры), ADR-0006 §1 (декомпозиция core/вложенные), ADR-0006 §2 (локальные vs кросс-модельные), ADR-0006 §3 (resolver, тут не критично).
- **Уровень уверенности:** однозначно — для блоков 1, 3, 4, 6, 7, 9-15. Склонялся — для блоков 2, 5, 8 (см. ниже).

##### Действие 8: Развилка по декомпозиции BalanceContainer vs Balance

- **Что делал:** legacy чётко различает `BalanceContainer` (account-level aggregate) и `Balance` (currency-level snapshot внутри). В MODELS.md есть только `Balance`. По ADR-0006 §1 критерий бизнес-объекта (имеет identity, persistent, переживает рестарт) — выполняется для **обоих**: и BalanceContainer, и Balance имеют id, оба persistent. Но Balance существует только внутри BalanceContainer (по семантике legacy: «currency-level snapshot внутри BalanceContainer»). По операционному критерию ADR-0006 §1 «можно ли работать с моделью независимо» — Balance вне контейнера бессмыслен (нет смысла грузить отдельной репозиторной операцией без знания exchange account).
- **Что бы сделал в реальной миграции:** принял решение: BalanceContainer — бизнес-объект (один model-документ); Balance — вложенная модель (раздел внутри). Целевой spec-документ один: `docs/spec/models/core/Balance.md` (имя по бизнес-объекту-aggregate; но **тут уже сидит несостыковка** — MODELS.md фиксирует имя `Balance`, а агрегат — `BalanceContainer`). Альтернатива: переименовать model-документ в `BalanceContainer.md` и в MODELS.md переименовать запись на BalanceContainer. Это структурное решение по принципу 1 ADR-0006: имя — за бизнес-объектом, не за частью.
- **На какие правила опирался:** ADR-0006 §1 (бизнес-объект и операционный признак), скилл `spec-models-registry` «Переименование модели» (требует ADR), MODELS.md.
- **Уровень уверенности:** склонялся. Альтернативы:
  - **(a) bizclass = Balance, containing Balances**: оставить имя model-документа `Balance.md`, описать его как «реестр балансов аккаунта», BalanceContainer считать неудачным наследием. Минус: противоречит фактической семантике legacy и Java-классов.
  - **(b) bizclass = BalanceContainer, embedded Balance**: model-документ `BalanceContainer.md`, MODELS.md обновляется. Это требует ADR на переименование (по скиллу `spec-models-registry`).
  - **(c) две модели**: и BalanceContainer и Balance — оба бизнес-объекты, два model-документа. Минус: Balance не имеет независимого жизненного цикла, операционный признак ADR-0006 §1 за это не голосует.
- **Выбор:** (b), потому что критерий бизнес-объекта по ADR-0006 §1 однозначно за BalanceContainer (account-level identity, экземпляр привязан к exchange account, переживает рестарт). Это **требует ADR на переименование `Balance` → `BalanceContainer` в MODELS.md**. Это блокирует Действие 10 (обновление MODELS.md) — нужно либо принять ADR, либо завести Q-N. Завожу Q-001 (см. ниже).

##### Действие 9: Развилка по freshness-policy и REFRESH_BALANCE

- **Что делал:** §5 Freshness, §9 REFRESH_BALANCE, §13 RiskValidator — все говорят про cross-cutting механику. По ADR-0006 §2 тест «можно ли проверить инвариант, глядя только на одну модель»: «BalanceContainer должен быть fresh перед risk-sensitive flow» — субъект инварианта **внешний** (FSM/handler/RiskValidator), значит **кросс-модельный**. По ADR-0009 §9 freshness — сквозная концепция, целевой spec-документ — invariant/process при миграции соответствующего кластера. Какого? В трекере 6 сквозных концепций упоминаются; freshness среди них (явно перечислена в ADR-0009 §9). Но **в каком кластере** мигрирует freshness — в ADR-0009 не сказано.
- **Что бы сделал в реальной миграции:** Q-N: «В каком кластере создаётся spec-документ для freshness invariant?». Здесь, в C4, я могу упомянуть freshness только прозаически (без link). А REFRESH_BALANCE — это ServiceCommand, его описание уходит в C7 (Сервисные команды). Из Balance.md в spec уходит только: ссылка на freshness как требование (роль), и упоминание REFRESH_BALANCE как единственного flow обновления (прозаически).
- **На какие правила опирался:** ADR-0006 §2 (cross-model тест), ADR-0009 §9 (сквозные концепции), скилл `spec-cluster-migration` Этап 2 п.2 (забегающие ссылки), скилл `spec-document-migration` шаг 2 «Развилки».
- **Уровень уверенности:** ступор по «где живёт freshness invariant?». Завожу Q-002 (см. ниже).

##### Действие 10: Развилка по ExternalSnapshot (BalanceContainerExternalSnapshot / BalanceExternalSnapshot)

- **Что делал:** в Balance.md §8 описаны два validated normalized snapshot — `BalanceContainerExternalSnapshot`, `BalanceExternalSnapshot`. По ADR-0006 §1: snapshot — не persistent, без identity, не переживает рестарт → runtime. По ADR-0007 §1 — `docs/spec/models/runtime/`. Но: ни одного из них в MODELS.md не зарегистрировано (там 3 runtime: DealActionState, DealContext, ServiceCommand). По правилам skill `spec-models-registry` — «вложенные модели (только в составе)» в MODELS.md не идут. Snapshots — обёртки между client-layer и runtime, не aggregate roots; ближе к value-object, чем к runtime aggregate. По ADR-0007 §2 — external DTO бирж описываются в `integrations/<exchange>/models/`, но **normalized snapshot** — это не raw DTO биржи, это уже нормализованный домен. Это сидит **между** жанрами.
- **Что бы сделал в реальной миграции:** Q-N «куда едет normalized external snapshot». Альтернативы:
  - (a) Раздел внутри `models/core/BalanceContainer.md` (как вложенная вспомогательная структура к refresh-flow). Просто и читаемо.
  - (b) Отдельные runtime-документы `models/runtime/BalanceContainerExternalSnapshot.md` + регистрация в MODELS.md. Размывает реестр (runtime становится «всё transient»).
  - (c) Описать только в `integrations/okx/mapping/balance-mapping.md` в фазе 2; в фазе 1 не писать вовсе.
- **Выбор:** склоняюсь к (a) — раздел внутри model-документа BalanceContainer.md. Аргумент: ExternalSnapshot ничего не «делает» сам, это структура-проводник; ADR-0007 §2 (integrations) описывает связь биржи с доменом, а normalized snapshot живёт **внутри** домена (мы уже после ClientService). Завожу Q-003 (decision-needed) для подтверждения. Не блокирует выписку, блокирует hard gate 4 (нерешённый структурный вопрос). Записываю в Q-N.

##### Действие 11: Что не переезжает в spec (по ADR-0006 §4, ADR-0002)

- **Что делал:** §15 Balance.md — «открытых вопросов нет» — не переезжает (по ADR-0002 §3 spec не содержит «Открытых вопросов»). Java-сниппеты §6, §7, §8 — не переезжают (по ADR-0002 §6 запрещены), превращаются в таблицы полей. Сниппет интерфейса BalanceFreshnessChecker (§5) — не переезжает (это реализация, не контракт). Markdown-блоки кода с диаграммами в виде ASCII (например, дерево BalanceContainer → Balance(USDT)) — допустимы, оставляю как mermaid или ASCII-блок.
- **Что бы сделал в реальной миграции:** ничего — это автоматическое следствие правил, не требует действия.
- **На какие правила опирался:** ADR-0002 §3 (нет открытых вопросов), §6 (нет Java-сниппетов).
- **Уровень уверенности:** однозначно.

##### Действие 12: Терминологические замены при миграции

- **Что делал:** свёрка с `docs/conventions/terminology.md` и таблицей замен в скилле `spec-document-migration`. В Balance.md встречается: «entity» («trading runtime entity», «runtime-сущность», «persisted domain entity») → «доменная модель» / «runtime-модель»; «persisted» → «хранимое»; «orphan» — не встречается; «aggregate» — встречается («account-state snapshot aggregate») — соглашения по «aggregate root» в terminology.md пока нет, оставляю как есть.
- **Что бы сделал в реальной миграции:** автоматические замены применил Claude Code в шаге 3 исполнения по таблице.
- **На какие правила опирался:** скилл `spec-document-migration` «Терминологические замены».
- **Уровень уверенности:** однозначно для базовых замен; склонялся для «aggregate» — оставляю до появления соглашения.

##### Действие 13: Решение развилок в чате (шаг 2 spec-document-migration)

- **Что делал:** для dry-run «решением» считаю запись развилок как Q-N и фиксацию выбранных trade-offs прозой в этом отчёте.
- **Что бы сделал в реальной миграции:** в чате обсудил бы каждый Q-N (Q-001, Q-002, Q-003), пользователь принял бы решение, и оно пошло бы в исполнение. Без этого шага исполнение **не запускается**.
- **На какие правила опирался:** скилл `spec-document-migration` шаг 2.
- **Уровень уверенности:** ступор — для финализации C4 в реальной миграции по hard gate 4 этих 3 Q-N достаточно, чтобы кластер не закрыть, **если они помечены как блокирующие**. Q-001 (имя aggregate) — критичный для gate 4 (влияет на поля документа: PascalCase имя файла, имя в реестре). Q-002 (freshness invariant — где живёт) — не влияет на поля Balance.md, только на упоминание; не блокирующий. Q-003 (ExternalSnapshot) — влияет на структуру model-документа (есть/нет раздел); критичный.

##### Действие 14: Исполнение (шаг 3 spec-document-migration) — список целевых документов

- **Что делал:** перечислил, что бы создал.
- **Что бы создал в реальной миграции:**
  1. `docs/spec/models/core/BalanceContainer.md` (имя — по выбору в Действии 8; альтернативно `Balance.md`, если Q-001 решён в пользу варианта (a)).
     - frontmatter: `status: draft`, `last_review: 2026-05-17`, `related_adrs: [ADR-0001, ADR-0006, ADR-0009]`, `related_models: [Exchange]` (через FK — но Exchange ещё нет, поэтому в related_models не пишу или ставлю как pending), `related_processes: []`.
     - Структура по шаблону `model.md`: header — короткое описание; «Поля» — таблица BalanceContainer.id, exchangeId, externalUpdatedAt, externalTotalEquity, externalAdjustedEquity, externalAvailableEquity, balances (List<Balance>); «Balance» как вложенная модель — отдельный раздел с таблицей её полей; «Инварианты структуры» — поля not-null, обязательная settle currency для текущего проекта (как контракт), invariants про отсутствие Status/lifecycle; «Связи» — Exchange (прозаически), DealContext (прозаически); «Персистентность» — нетривиально (collection of Balance внутри aggregate, ссылка на Flyway-миграции); «Связанные документы» — таблица (lifecycle: нет, см. ниже; маппинг: integration OKX/balance — фаза 2; ключевые процессы: RefreshBalance — C7).
     - Опц. раздел «Что не хранится в BalanceContainer» — как конструктивный сигнал (по ADR-0006 §4 «развилка» что включается в spec). Склонялся к **включению**, потому что список полезен (отсекает попытки добавить borrow/collateral/copy-trading). Альтернатива: не включать (правило «spec = текущее состояние, не negative»). Беру **включить** как короткий раздел.
     - Опц. раздел «Freshness» — упомянуть, что fresh-check выполняется **до** risk-sensitive flow, ссылка прозой на cross-model invariant (целевой документ — Q-002, inline-ссылка «см. Q-002»).
     - Раздел «REFRESH_BALANCE» — короткое упоминание, что обновление BalanceContainer возможно только через эту команду; полное описание — в C7 (прозаическая ссылка на «процесс обновления баланса»).
     - Раздел «BalanceContainerExternalSnapshot / BalanceExternalSnapshot» — таблицы полей normalized snapshot (по выбору в Q-003: вариант (a) — внутрь model-документа).
  2. **Lifecycle-документа НЕ создаю.** По legacy §2: «у BalanceContainer нет CREATED/PENDING/ACTIVE/CLOSED, нет Status, нет active/closed semantics». По ADR-0006 §3 lifecycle-документ нужен для модели с явной FSM. BalanceContainer её не имеет.
  3. **Invariant-документы НЕ создаю.** Freshness — Q-002 (отложено до C7/C8). Локальные правила (обязательная settle currency, replace-semantics для balances) — внутри model-документа в «Инварианты структуры».
  4. **Integration-документ НЕ создаю** (фаза 2).

- **На какие правила опирался:** ADR-0002 §1-5, ADR-0006 §1-4, ADR-0007 §1, ADR-0009 §1 («integration-документация биржи» — нейтральная формулировка в фазе 1), шаблоны `model.md`, скилл `spec-document-workflow` (шаги 3a, 6).
- **Уровень уверенности:** однозначно для отсутствия lifecycle/invariant; склонялся для «Что не хранится» (включаю); склонялся для «BalanceContainerExternalSnapshot inside model.md» (зависит от Q-003).

##### Действие 15: Связные обновления при исполнении

- **Что делал:** проверил, что обновляется при создании Balance.md.
- **Что бы сделал в реальной миграции:**
  - **MODELS.md:** если Q-001 решён в пользу `BalanceContainer` — переименовать запись `**Balance** — баланс активов…` → `**BalanceContainer** — …`, переместить по алфавиту, добавить ссылку `См. models/core/BalanceContainer.md.`. Если в пользу `Balance` — оставить имя, добавить ссылку `См. models/core/Balance.md.`.
  - **migration-tracker.md:** обновить статус документа `Balance.md` → `done`, перечислить `docs/spec/models/core/BalanceContainer.md` (или `Balance.md`); статус кластера C4 пока остаётся `in-progress` (есть второй документ — фрагмент Статусов).
  - **journal:** добавить Q-001, Q-002, Q-003 в раздел Open.
  - **backlog:** в текущем backlog есть пункт «привести `docs/spec/models/AnomalyReport.md` к стандарту ADR-0002» — не закрывается этим действием. Других пунктов про Balance нет. Добавил бы пункт «миграция кластера C4: Balance» если его ещё нет (он есть как формулировка backlog-пункта по ADR-0009 §10).
  - **MIGRATED-маркер на Balance.md:** только при условии, что все три Q-N **закрыты** или сознательно оставлены неблокирующими и все блоки разнесены. По скиллу `spec-document-migration` «MIGRATED-маркер при частичной миграции» — пока Q-001 и Q-003 открыты и они блокирующие — маркер **НЕ ставится**, документ остаётся `in-progress`.
- **На какие правила опирался:** скилл `spec-document-migration` чек-лист исполнения; скилл `spec-models-registry`; скилл `open-questions-workflow`.
- **Уровень уверенности:** однозначно по механике; решение «ставить ли MIGRATED» зависит от ответа на Q-001/Q-003.

#### Документ B: фрагменты `Статусы торговых сущностей.md §8.6 (BalanceContainer)` для C4

##### Действие 16: Анализ фрагмента §8.6

- **Что делал:** прочитал §8.6 «BalanceContainer» (~50 строк). Содержание: подтверждение что Balance не trading entity, нет Status/lifecycle, важны freshness/settleCurrency/usage в sizing-risk, REFRESH_BALANCE upsert/replace, normal null contract не используется.
- **Что бы сделал в реальной миграции:** инвентарь:
  | # | Блок | Дубликат с Balance.md? | Куда уезжает |
  |---|---|---|---|
  | 1 | «нет Status / active-closed semantics» | да (§2 Balance.md) | пропустить |
  | 2 | «freshness» — формула + что значит stale | да (§5 Balance.md) | пропустить |
  | 3 | «REFRESH_BALANCE upsert / replace» | да (§6, §9) | пропустить |
  | 4 | «normal null contract не используется» | да (§9.4) | пропустить |

  Всё в §8.6 — дубль Balance.md. Самостоятельного контента нет.

- **Что бы сделал в реальной миграции:** ничего из §8.6 в Balance.md в spec не добавляется (оно уже там по Balance.md legacy). Фрагмент помечается как «полностью разнесён».
- **На какие правила опирался:** ADR-0002 §2 (один факт — одно место); скилл `spec-document-migration` шаг 1 «дубли».
- **Уровень уверенности:** однозначно.

##### Действие 17: Обновление трекера по shared-файлу

- **Что делал:** Статусы торговых сущностей.md — shared, фигурирует в C-NEW, C4, C2, C8. Часть C4 разобрана. MIGRATED-маркер на этом файле **не ставлю** — по правилу скилла `spec-document-migration` маркер для shared-файла ставится **только когда все** фрагменты разнесены.
- **Что бы сделал в реальной миграции:** в трекере у `Статусы торговых сущностей.md (фрагменты по Balance)` — статус `done`, но у общего файла маркера ещё нет; статус общего файла в трекере не ведётся (трекер ведёт фрагменты, не общий файл — это допущение по записям в migration-tracker, там для shared-файлов указаны «фрагменты по X»).
- **На какие правила опирался:** скилл `spec-document-migration` «Маркер для shared-файлов»; migration-tracker формат.
- **Уровень уверенности:** склонялся. Альтернатива: ставить статус «фрагменты по Balance — done», ждать остальных фрагментов в других кластерах. Беру **этот** вариант (он соответствует уже существующему формату трекера, где фрагменты идут отдельными строками).

### Этап 3. Проверка hard gates

#### Gate 1: все зависимости в spec

- **Что бы получил:** Exchange отсутствует в spec (C-NEW не пройден). Gate **НЕ пройден** в штатном смысле.
- **По условию прогона:** считаю «закрыт явным решением, как обрабатывать забегающие ссылки» (Действие 2 — упоминать прозаически). По ADR-0009 §4 это допустимо как gate-pass только если есть **явное решение**; решение зафиксировано в чате прогона. В реальной миграции это сомнительно и должно стать поводом не стартовать C4 до C-NEW.
- **Отметка для отчёта:** инфраструктура не запрещает прогон C4 раньше C-NEW, но и не делает это легально без отдельной фиксации.

#### Gate 2: карта legacy-источников зафиксирована

- **Что бы получил:** карта зафиксирована в Действиях 1 и 4. Конкретные разделы Balance.md и §8.6 «Статусов» перечислены в инвентаре (Действие 7, 16). **Пройден.**

#### Gate 3: карта целевых spec-документов зафиксирована

- **Что бы получил:** один документ — `docs/spec/models/core/BalanceContainer.md` (или `Balance.md` в зависимости от Q-001). Жанр зафиксирован — model/core. **Пройден** (имя зависит от Q-001).

#### Gate 4: критические концептуальные вопросы закрыты

- **Что бы получил:**
  - Q-001 «имя aggregate (BalanceContainer vs Balance)» — влияет на имя файла и на запись MODELS.md. **Критичный.** В реальной миграции — должен быть Resolved до старта исполнения шага 3.
  - Q-002 «где живёт freshness invariant» — не влияет на поля Balance.md; влияет на формулировку упоминания. **Не критичный.**
  - Q-003 «куда едет ExternalSnapshot» — влияет на структуру model-документа (отдельные разделы внутри или нет). **Критичный.**
- **Без закрытия Q-001 и Q-003 gate НЕ пройден.** Это блокирует финализацию C4.

#### Gate 5: влияния на следующие кластеры зафиксированы

- **Что бы получил:** список из Действия 6 (C2 — неблокирующее, C7 — неблокирующее, C1 — неблокирующее). **Пройден.**

### Этап 4. Финализация

- **Прошла / не прошла:** В реальной миграции — **не проходит** по gate 4 (Q-001 + Q-003). В рамках dry-run я фиксирую: «инфраструктура говорит — кластер не закрыть до решения двух concept-Q-N». Это правильный отказ.
- **Чтобы условный прогон отчёта показал, что инфраструктура работает:** если Q-001 и Q-003 решены в чате (это нормальная активность Этапа 2 шага 2 спецификации), кластер закрывается за 1 короткую дополнительную итерацию: исполнение записи в Balance.md → правка MODELS.md → правка трекера → MIGRATED-маркер на Balance.md → закрытие backlog-пункта C4 → Knowledge Curator общая проверка (Сценарий 8 playbook). Все шаги штатные.
- **Сравнение с ожиданием в задании:** ожидалось, что у C4 финализация **проходит** (upstream-зависимостей в фазе 1 нет). У меня формально **не проходит** — но не из-за upstream-зависимости C-NEW, а из-за двух собственных concept-вопросов внутри C4 (имя aggregate, размещение ExternalSnapshot). Это **сигнал для отчёта**: тестовый «простой» кластер на проверку процедуры миграции **не оказался простым**. Это либо ожидаемое поведение (любой кластер открывает Q-N — это норма), либо ошибка ожидания (C4 был оценён как «простой» по ADR-0009 §2 «маленький и почти самодостаточный», но содержит две неочевидные структурные развилки).

### Q-N, которые я бы завёл (по C4)

```
### Q-001 — Каноническое имя для aggregate (BalanceContainer vs Balance)

| | |
|---|---|
| Status | Added |
| Classification | concept |
| Source | dry-run миграции C4, 2026-05-17; Balance.md legacy + MODELS.md рассинхрон |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

Какое каноническое имя у aggregate root в кластере C4 — `BalanceContainer` (как в Java-классах и legacy Balance.md) или `Balance` (как в текущем MODELS.md)? От ответа зависит: имя model-документа (PascalCase = имя класса), запись в MODELS.md, имена в `related_models` других spec-документов, маршрут переименования (если меняем — нужен ADR на переименование по скиллу spec-models-registry).

**Context.**

Legacy чётко различает: `BalanceContainer` — account-level aggregate (имеет id, exchangeId, externalUpdatedAt, externalTotalEquity и т.д.), `Balance` — currency-level snapshot внутри BalanceContainer (id, balanceContainerId, externalCurrency и т.д.). По тесту ADR-0006 §1 операционно `Balance` без `BalanceContainer` бессмыслен — это вложенная модель. Имя бизнес-объекта (по ADR-0002 §5: имя файла = имя класса) должно быть `BalanceContainer`. Но MODELS.md уже зафиксировал запись `**Balance** — баланс активов на бирже…`.

**Notes.**

— (пусто на момент заведения)

**Resolution.**

— (заполняется при переходе в Resolved)
```

```
### Q-002 — Где живёт freshness invariant как cross-model документ

| | |
|---|---|
| Status | Added |
| Classification | concept |
| Source | dry-run миграции C4, 2026-05-17; Balance.md §5, §13; ADR-0009 §9 |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

В каком кластере и в каком документе фиксируется freshness invariant как сквозная концепция? Balance.md §5 описывает freshness-policy для `BalanceContainer`, но по ADR-0006 §2 это кросс-модельный инвариант (субъект — внешний RiskValidator / FSM-handler). По ADR-0009 §9 freshness — сквозная концепция, получает собственный spec-документ при миграции соответствующего кластера. Какой кластер «соответствующий»? C7 (Calculator + Risk + Command) или C8 (Audit)? Или новый invariant создаётся в C4 как часть Balance-кластера?

**Context.**

Freshness применяется и к Balance (см. RiskValidator), и к MarketData (см. C6). C4 не может создать собственный invariant freshness, потому что это потребует знания всех потребителей. C7 — естественный кандидат (RiskValidator там). До закрытия Q-002 я в Balance.md упоминаю freshness прозаически («fresh-check выполняется до risk-sensitive flow») без markdown-link.

**Notes.**

— 

**Resolution.**

—
```

```
### Q-003 — Куда едет normalized ExternalSnapshot (Balance/AlgoOrder/Order/Position и далее)

| | |
|---|---|
| Status | Added |
| Classification | structural |
| Source | dry-run миграции C4, 2026-05-17; Balance.md §8 |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

Куда едет `BalanceContainerExternalSnapshot` / `BalanceExternalSnapshot` и аналогичные normalized snapshot-классы (`OrderExternalSnapshot`, `AlgoOrderExternalSnapshot`, `PositionExternalSnapshot`)? Варианты: (a) разделы внутри model-документа aggregate; (b) отдельные runtime-документы в `models/runtime/` с регистрацией в MODELS.md; (c) `integrations/<exchange>/mapping/` в фазе 2 без записи в фазе 1. Решение системное — применяется ко всем кластерам C4, C2, C6, C5, C1, и должно быть зафиксировано один раз.

**Context.**

Snapshot создаётся в ClientService / adapter-layer после валидации raw response, передаётся в RefreshExecutor для применения к domain-сущности. Это не persistent (по тесту ADR-0006 §1 — runtime), но и не aggregate root (вспомогательная структура). ADR-0007 §2 описывает `integrations/<exchange>/models/` для **внешних DTO**, что не совпадает по природе с normalized snapshot. Решение влияет на структуру всех 5 «торговых» model-документов фазы 1.

**Notes.**

—

**Resolution.**

—
```

---

## C2: Order, AlgoOrder, Position

### Этап 1. Подготовка

#### Действие 18: Прочитать ADR-0009 §2 для C2

- **Что делал:** перечитал блок «Кластер C2». Legacy = Order.md, AlgoOrder.md, Position.md, фрагменты Статусов торговых сущностей.md, Сервисные команды.md §12 (дубль). Зависимости = Exchange, Instrument (C-NEW); через FK на Deal — допустимая забегающая ссылка по §5.
- **Что бы сделал в реальной миграции:** Зафиксировал в чате — кластер большой, 3 модели + 2 фрагмента shared-файлов.
- **На какие правила опирался:** ADR-0009 §2, скилл `spec-cluster-migration` Этап 1 п.1, migration-tracker.
- **Уровень уверенности:** однозначно.

#### Действие 19: Проверить готовность зависимостей C2

- **Что делал:** C-NEW (Exchange + Instrument) и C4 (Balance) — оба `planned` в трекере (или `in-progress` C4 после этого dry-run). C2 формально не должен стартовать. По условию прогона — стартую с явной фиксацией: Exchange, Instrument, Balance отсутствуют в spec; ссылки на них — прозаические забегающие.
- **Что бы сделал в реальной миграции:** старт **отказать**, открыть задачу «сначала C-NEW и C4».
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.2, ADR-0009 §4 Gate 1 и §5.
- **Уровень уверенности:** однозначно «отказать» по правилу; продолжаю по условию прогона.

#### Действие 20: Поставить статус кластера C2 = in-progress в трекере

- **Что делал:** в реальной миграции — правка `migration-tracker.md`.
- **Уровень уверенности:** однозначно.

#### Действие 21: Определить порядок документов внутри кластера C2

- **Что делал:** документы: Order.md, AlgoOrder.md, Position.md, фрагменты «Статусы…», фрагмент «Сервисные команды §12». Порядок по зависимостям:
  - `Position.md` — самостоятельная, единственная зависимость — DealContext (forward) и Exchange/Instrument (forward).
  - `Order.md` — содержит AttachedAlgoOrder как вложенную, упоминает AlgoOrder в §17.
  - `AlgoOrder.md` — ссылается на Order (отличие от attached), ссылается на DealActionState.
  - Я бы взял **Order → AlgoOrder → Position** (Order ⇒ задаёт AttachedAlgoOrder, AlgoOrder уже может ссылаться на Order как контраст; Position читается отдельно).
  - Альтернатива: **Position → Order → AlgoOrder**. Position наиболее автономна. Я склоняюсь к этому варианту: первая миграция отрабатывает простой кейс (Position имеет минимальный enum-набор и наиболее коротка), потом Order (содержит AttachedAlgoOrder как вложенную — тестирует декомпозицию), потом AlgoOrder (большая модель с Condition/Trigger/Trailing).
- **Что бы сделал в реальной миграции:** в чате обсудил с пользователем порядок, зафиксировал.
- **На какие правила опирался:** скилл `spec-cluster-migration` Этап 1 п.4; ADR-0009 §2 «батчинг однотипных» (намёк на параллельность, но скилл явно запрещает параллельную миграцию в Подводных камнях п.2).
- **Уровень уверенности:** склонялся. Альтернативы перечислены. Беру **Position → Order → AlgoOrder**.

#### Действие 22: Открыть журнал — следующий id

- **Что делал:** после C4 максимум `Q-003`. Следующий — `Q-004`.
- **Уровень уверенности:** однозначно.

#### Действие 23: Список ожидаемых влияний на следующие кластеры (Gate 5)

- **Что делал:** ожидаемые влияния C2:
  1. C5 (Strategy) — Strategy.md ссылается на Order/AlgoOrder/Position как target runtime-сущности. **Неблокирующее**, забегающая ссылка из C5 на C2 (а не наоборот) — C5 будет писаться после, ссылки штатные.
  2. C1 (Deal) — Deal runtime graph включает Order/AlgoOrder/Position. **Неблокирующее**.
  3. C7 (Calculator/Risk/Command) — ServiceCommand SUBMIT/CANCEL/CLOSE_POSITION/REFRESH_POSITION/REFRESH_ORDER/REFRESH_ALGO_ORDER работают с Order/AlgoOrder/Position; status resolver pattern; exchange exception runtime-реакция (Deal → ERROR, Exchange → HOLD). **Блокирующее в обратную сторону** — Order.md / AlgoOrder.md / Position.md описывают конкретные runtime-реакции на исключения, что относится к C7. Это **нужно вынести** или **оставить как «контракт сущности»**.
  4. C8 (Audit) — status-resolver pattern, anomaly-classification, status-resolution invariant. **Блокирующее в обратную сторону** — C2 не может описать конкретные таблицы маппинга OKX (это фаза 2), но common contract resolver-pattern по ADR-0006 §3 — кросс-модельный invariant из C8 (`docs/spec/invariants/status-resolution.md`).
- **Что бы сделал в реальной миграции:** Q-N: «как описывать runtime-реакцию на exception и status-resolver pattern в C2 без C7/C8?». Завожу Q-004 (см. ниже).
- **На какие правила опирался:** ADR-0009 §4 Gate 5, ADR-0006 §3 (status-resolution invariant), скилл `spec-cluster-migration` «Hard gates подробнее».
- **Уровень уверенности:** ступор. Завожу Q-004.

### Этап 2. Миграция документов

#### Документ A: `docs/domain/models/Position.md`

##### Действие 24: Анализ Position.md

- **Что делал:** прочитал Position.md (~780 строк). Декомпозировал:
  | # | Блок | Жанр | Куда |
  |---|---|---|---|
  | 1 | §1 Назначение | metadata | header |
  | 2 | §2 Главные инварианты | structure + lifecycle + cross-model | «Инварианты структуры»; cross-model — Q-N или forward |
  | 3 | §3 Доменная модель (Java) | structure | таблица полей |
  | 4 | §4 Position.Status | structure | раздел «Статусы» в model-документе (по ADR-0006 §3) |
  | 5 | §5 Position.CloseReason | structure | раздел «Причины закрытия» — enum-список + семантика |
  | 6 | §6 Position.Direction | structure | enum в таблице полей |
  | 7 | §7 Active/Closed/Live risk | structure + cross-model | внутрь model (live risk — derived) |
  | 8 | §8 PositionExternalSnapshot | structure runtime | Q-003 — раздел внутри model или отдельный документ |
  | 9 | §9 PositionStatusResolver | cross-model invariant | C8 (`invariants/status-resolution.md`) — прозаически здесь |
  | 10 | §10 REFRESH_POSITION policy | process | C7 |
  | 11 | §11 Легитимное окно появления позиции | lifecycle / cross-model | спорно — см. ниже |
  | 12 | §12 CLOSE_POSITION semantics | process | C7 |
  | 13 | §13 Position и fills/PnL | metadata | header или «Что не делает Position» |
  | 14 | §14 Position и DealContext | cross-model | C1 |
  | 15 | §15 Что принципиально хранит | metadata | header |
  | 16 | §16 Recovery-сценарий | process / cross-model | C7 (process), здесь — прозаически |
  | 17 | §17 Связанные документы | metadata | frontmatter `related_*` или раздел навигации в конце |

- **Уровень уверенности:** склонялся для §11 «Легитимное окно появления позиции». Это про переход системы из состояния «нет Position» в «есть Position» — структурный момент жизненного цикла Position. По ADR-0006 §3: lifecycle-документ нужен для модели с явной FSM. У Position 3 статуса (ACTIVE/CLOSED/ERROR) и нет полноценной FSM с переходами (нет CREATED/PENDING), переход ACTIVE → CLOSED делается одним refresh-снимком, не цепочкой. Поэтому **lifecycle-документ для Position не создаю**. Параграф §11 уходит в model-документ в разделе «Статусы» как примечание о появлении Position.
- **Альтернатива:** создать `docs/spec/lifecycle/Position.md` с описанием состояний и переходов. Не выбираю, потому что нет FSM-handlers и нет нетривиальных переходов.

##### Действие 25: Развилка — где описывать ExternalStatusException/ExternalNotFoundException/ExternalInvariantViolationException для Position

- **Что делал:** в Position.md прямо не упоминаются типы exception (только в Order.md, AlgoOrder.md, «Статусы…» §6). Но для Position runtime-реакция (Position → ERROR, Deal → ERROR, Exchange → HOLD) аналогична. По ADR-0006 §3: общий контракт резолюции — кросс-модельный invariant. Detail для Position попадёт в C8.
- **Что бы сделал в реальной миграции:** в model-документе Position.md упомянуть прозой «ошибки status-resolver обрабатываются по общему контракту резолюции», без markdown-link до C8.
- **Уровень уверенности:** однозначно по принципу; ссылка прозаическая.

##### Действие 26: Развилка по §11 «Легитимное окно появления позиции»

- **Что делал:** этот блок описывает, что Position может сначала появиться на бирже, потом локально через REFRESH_POSITION. Это **процесс**, не структура. В model-документе — короткое примечание; полное описание — в process-документе C7 (`refresh-executor.md` или похожий) или в C1 (Deal lifecycle).
- **Что бы сделал в реальной миграции:** в Position.md spec — короткое примечание «Position может появиться локально позже, чем на бирже; см. процесс обновления позиции» (прозаически). Полный flow — в C7.
- **Уровень уверенности:** однозначно.

##### Действие 27: Развилка по §16 «Recovery-сценарий»

- **Что делал:** §16 описывает конкретный recovery flow: восстановление после падения приложения. Это процесс, не структура. По ADR-0002 §1 жанр model не описывает процессы.
- **Что бы сделал в реальной миграции:** не переношу в Position.md spec. Запись в Q-N как кандидат для C1 (Deal lifecycle) или C7 (recovery process). Завожу Q-005 (см. ниже).
- **Уровень уверенности:** однозначно для решения «не переносить»; ступор для «где живёт описание recovery flow».

##### Действие 28: Исполнение для Position — целевой документ

- **Что бы создал:** `docs/spec/models/core/Position.md`.
  - frontmatter: `status: draft`, `last_review: 2026-05-17`, `related_adrs: [ADR-0001, ADR-0006, ADR-0009]`, `related_models: []` (Deal, Exchange, Instrument — забегающие, не пишу пока), `related_processes: []`.
  - Структура: header, «Поля» (таблица 11 полей), «Статусы» (enum + семантика + правило live risk), «Причины закрытия» (CloseReason enum + 6 значений + семантика), «Direction» (LONG/SHORT enum в таблице полей), «Инварианты структуры» (одна Position на Deal, не хранит instrumentId/exchangeId/internalId/strategyActionId, обновляется только через REFRESH_POSITION, ACK не truth и пр.), «PositionExternalSnapshot» (по Q-003) либо ссылка прозой на runtime-документ, «Связи» (Deal — прозой), «Связанные документы» (lifecycle: нет; маппинг: integration OKX — фаза 2; ключевые процессы: refresh/close-position — C7; resolver-pattern — C8).
- **Уровень уверенности:** однозначно по структуре; зависит от Q-003 для блока про ExternalSnapshot.

##### Действие 29: Связные обновления для Position

- **MODELS.md:** заменить заглушку `**Position** — открытая торговая позиция…` → добавить ссылку `См. models/core/Position.md.`. Имя совпадает с записью в реестре — без переименования.
- **migration-tracker.md:** `Position.md` → `done`, перечислить `docs/spec/models/core/Position.md`.
- **journal:** Q-004, Q-005 (а если Q-003 ещё не закрыт — он висит из C4, его проверить).
- **MIGRATED-маркер на Position.md:** ставлю **если** все блоки разнесены и Q-N по Position не блокируют. Q-005 (recovery) — не блокирует Position.md как model-документ, потому что блок §16 не относится к контракту Position. Можно ставить.

#### Документ B: `docs/domain/models/Order.md`

##### Действие 30: Анализ Order.md

- **Что делал:** прочитал Order.md (~1260 строк). Это **большой документ**, превышает мягкий cap 1000 строк, заданный ADR-0002 §6. После переработки в таблицы (вместо Java) станет короче — ориентировочно 600-800 строк. Декомпозиция:
  | # | Блок | Жанр | Куда |
  |---|---|---|---|
  | 1 | §1 Назначение | metadata | header |
  | 2 | §2 Главные инварианты | structure + cross-model | «Инварианты структуры» + cross-model прозой |
  | 3 | §2.1 Exchange invariant validation | cross-model | C7 (или C8 status-resolution.md) |
  | 4 | §3 Доменная модель Order (Java) | structure | таблица полей |
  | 5 | §4 Доменная модель AttachedAlgoOrder (Java) | structure | раздел «AttachedAlgoOrder» как вложенная модель внутри Order.md (см. развилку ниже) |
  | 6 | §5 OrderExternalSnapshot + AttachedAlgoOrderExternalSnapshot | structure runtime | по Q-003 |
  | 7 | §6 Status semantics | structure | разделы «Статусы Order» и «Статусы AttachedAlgoOrder» |
  | 8 | §7 OrderExternalStatusResolver | cross-model | C8 invariant, OKX detail — фаза 2 |
  | 9 | §8 Attached protection resolving | cross-model | C8 + здесь упоминание |
  | 10 | §9 Missing attached protection policy | lifecycle / process | спорно — см. ниже |
  | 11 | §10 REFRESH_ORDER / REFRESH_PENDING_ORDERS / REFRESH_ORDER_HISTORY / REFRESH_FILLS | process | C7 |
  | 12 | §11 Связь с DealActionState | cross-model | C7 (cross-ref) + здесь прозой |
  | 13 | §12 Что не хранится | metadata | раздел «Что не хранится» |
  | 14 | §13 Связанные документы | metadata | frontmatter / навигация |

- **Уровень уверенности:** склонялся для §9 «Missing attached protection policy» — это структурный набор правил поведения, который зависит от статуса parent Order. По ADR-0006 §2 — это **локальный инвариант** AttachedAlgoOrder (субъект — сама модель + parent Order, но parent тот же документ). Уходит в раздел «Инварианты структуры» или отдельный раздел «Поведение attached protection» внутри Order.md model. **Не в lifecycle.**

##### Действие 31: Развилка — AttachedAlgoOrder вложенная или отдельная

- **Что делал:** AttachedAlgoOrder имеет id, Status, CloseReason, attachedAlgoOrderId, externalAttachedId, externalId, full FSM с canTransitionTo матрицей. Имеет identity (Long id). Persistent. Но **существует только в составе Order** (по legacy §2: «embedded-часть parent Order»). По операционному критерию ADR-0006 §1 — нельзя работать независимо (нет независимого репозитория, всегда связана с parent Order через orderId). Беру **вложенную** — раздел внутри `models/core/Order.md`. **НЕ создаю** `models/core/AttachedAlgoOrder.md`.
- **Уровень уверенности:** склонялся. Альтернативы:
  - (a) Вложенная — раздел внутри Order.md. ADR-0006 §1 операционный признак за это.
  - (b) Отдельная — `models/core/AttachedAlgoOrder.md`. ADR-0006 §1 формальный критерий (identity + persistence) за это; в MODELS.md записи нет, надо добавлять.
  - (c) Отдельный документ в `models/runtime/` — не подходит, AttachedAlgoOrder persistent.
- **Беру (a)** по операционному признаку.

##### Действие 32: Развилка — Order.Status и lifecycle/Order.md

- **Что делал:** Order имеет 7 статусов и переходы между ними реализованы через transition methods (toCancel/toComplete/toError, без явной матрицы — у AttachedAlgoOrder матрица есть). По ADR-0006 §3: «Enum-декларация и переходы статусов (внутренний автомат сущности) — в model-документе сущности». Lifecycle-документ для Order **не создаю** — таксономия внутри model. AttachedAlgoOrder — тоже внутри Order.md model.
- **Альтернатива:** создать `docs/spec/lifecycle/Order.md` с диаграммой переходов. По ADR-0002 §1 жанр lifecycle — «динамика модели, переходы, кто меняет». Если в Order.md переходы триггерятся внешними процессами (refresh / handlers), это уже cross-model. Тогда `lifecycle/Order.md` уместен.
- **Беру:** model-документ для Order.md содержит **enum-декларацию + семантику статусов**. Если переходы триггерятся внешними процессами, это в `lifecycle/Order.md` — отдельный документ с таблицей переходов и колонкой «кто триггерит». Это даёт два документа: `models/core/Order.md` и `lifecycle/Order.md`.
- **Уровень уверенности:** склонялся. Альтернатива (только model, без lifecycle) проще, но переходы Order действительно триггерятся снаружи (refresh-executor, handlers). По ADR-0006 §3 «кто двигает (executor, resolver, finalize-handler) описывается отдельно» — это аргумент за lifecycle. Беру **с lifecycle/Order.md** для Order, AlgoOrder (у обоих есть transition methods и внешние триггеры), но **не для Position** (3 статуса, переходы тривиальные).
- **Альтернатива b:** не выделять lifecycle, держать всё в model. **Беру с выделением lifecycle** для Order/AlgoOrder.

##### Действие 33: Исполнение для Order — целевые документы

- **Что бы создал:**
  1. `docs/spec/models/core/Order.md` — поля Order (таблица 14 полей), enum-декларации Status/Type/CloseReason с семантикой, «AttachedAlgoOrder» как раздел-вложенная-модель (поля, Status, CloseReason, Type, «Поведение attached protection» с правилами по статусу parent — §9 legacy), «Инварианты структуры», «OrderExternalSnapshot / AttachedAlgoOrderExternalSnapshot» (по Q-003), «Связи», «Связанные документы» (навигация на lifecycle/Order.md, integration OKX — фаза 2, processes — C7, status-resolution invariant — C8).
  2. `docs/spec/lifecycle/Order.md` — таблица переходов Status (CREATED → PENDING → ACTIVE → PARTIALLY_COMPLETED → COMPLETED/CANCELED/ERROR), кто триггерит (Submit-executor, Refresh-executor, Cancel-executor), правила обработки сбоев (Unknown external status → ERROR + Deal ERROR + Exchange HOLD прозой). Внутри: отдельный подраздел про AttachedAlgoOrder lifecycle (4 ACTIVE-like + 3 terminal). Размер — около 200 строк.
- **Уровень уверенности:** склонялся (см. Действие 32).

##### Действие 34: Связные обновления для Order

- **MODELS.md:** заменить заглушку Order на ссылку `См. models/core/Order.md.`.
- **migration-tracker.md:** `Order.md` → `done`, перечислить `models/core/Order.md` и `lifecycle/Order.md`.
- **journal:** Q-N по ходу (Q-006 — см. ниже).
- **MIGRATED-маркер:** ставлю при разнесении.

##### Действие 35: Развилка — OKX-mapping таблицы из Order.md §7.2 в фазу 2

- **Что делал:** legacy Order.md §7.2 содержит конкретную OKX mapping таблицу (live → ACTIVE, partially_filled → PARTIALLY_COMPLETED и т.д.). По ADR-0006 §3 — это уезжает в `integrations/okx/mapping/order-mapping.md` в фазе 2. Но фаза 2 ещё не наступила; legacy `docs/domain/models/mapping/okx/OKX_Order_mapping.md` уже существует и описывает маппинг.
- **Что бы сделал в реальной миграции:** в Order.md spec **не переношу** таблицу OKX. Прозой упоминаю «конкретные таблицы соответствия описаны в integration-документации биржи» (нейтрально, по ADR-0007 §2). Таблица остаётся в legacy `docs/domain/models/mapping/okx/OKX_Order_mapping.md` до фазы 2.
- **Альтернатива:** в Order.md дать таблицу как пример. По ADR-0006 §3 — запрещено. Не делаю.
- **Уровень уверенности:** однозначно по правилу. **Но** это создаёт «пробел» в spec до фазы 2: пользователь, читающий Order.md, не увидит ни таблицы маппинга, ни ссылки на конкретный документ (только на «integration-документацию биржи»). Это естественное следствие фазового подхода — отмечаю как «не идеально, но штатно».

#### Документ C: `docs/domain/models/AlgoOrder.md`

##### Действие 36: Анализ AlgoOrder.md

- **Что делал:** прочитал AlgoOrder.md (~1370 строк). Содержание похоже на Order.md, плюс ConditionType/Condition/Trigger/Trailing — встроенные модели. Декомпозиция:
  | # | Блок | Жанр | Куда |
  |---|---|---|---|
  | 1 | §1-2 Назначение, инварианты | metadata + structure | header + «Инварианты структуры» |
  | 2 | §3 AlgoOrder (Java) | structure | таблица полей |
  | 3 | §4 Status semantics | structure | «Статусы» в model + lifecycle/AlgoOrder.md |
  | 4 | §5 Condition/Trigger/Trailing (Java) | structure | разделы «Condition», «Trigger», «Trailing» как вложенные модели внутри AlgoOrder.md |
  | 5 | §6 ConditionType | structure | enum в разделе «Condition» |
  | 6 | §7 AlgoOrderExternalSnapshot | structure runtime | по Q-003 |
  | 7 | §8 ConditionExternalSnapshot (+ Trigger/Trailing snapshots) | structure runtime | по Q-003 |
  | 8 | §9 Связь с DealActionState | cross-model | прозой |
  | 9 | §10 Exchange exceptions (3 типа) | cross-model | C8 (status-resolution invariant) + прозой здесь |
  | 10 | §11 Status resolver + OKX-mapping | cross-model + integration | C8 + integration OKX (фаза 2); таблица OKX state не переносится |
  | 11 | §12 Client/adapter validation | cross-model | C7/C8 + прозой |
  | 12 | §13 Refresh/recovery + algo evidence-cycle | process | C7 |
  | 13 | §14 Cancel semantics | process / lifecycle | lifecycle/AlgoOrder.md или C7 |
  | 14 | §15 Amend semantics | process | C7 |
  | 15 | §16 Связь с ordinary Order (linkedOrderExternalIds) | cross-model | прозой |
  | 16 | §17 Отличие от attached protection | metadata | header или раздел «Соотношение с Order» |
  | 17 | §18 Impact на текущий код | metadata | **НЕ переезжает** — это implementation checklist, не контракт |

##### Действие 37: Развилка — Condition / Trigger / Trailing — вложенные или отдельные

- **Что делал:** Condition имеет type + trigger + trailing (XOR). Trigger — stopLoss/takeProfit (TriggerPrice). Trailing — trailingPercents/trailingStepValue/activationPrice/externalPrice. TriggerPrice — type/value/externalType/externalValue. **Все** существуют только внутри AlgoOrder. По ADR-0006 §1 — вложенные.
- **Беру:** разделы внутри `models/core/AlgoOrder.md`. Структура: «Condition», «Trigger», «TriggerPrice», «Trailing», «ConditionType (enum)». Это **5 разделов** внутри одного model-документа.
- **Уровень уверенности:** однозначно.

##### Действие 38: Исполнение для AlgoOrder — целевые документы

- **Что бы создал:**
  1. `docs/spec/models/core/AlgoOrder.md` — поля (таблица 18 полей), Status enum, CloseReason enum (10 значений), Direction (BUY/SELL), вложенные «Condition»/«Trigger»/«TriggerPrice»/«Trailing»/«ConditionType», «AlgoOrderExternalSnapshot / ConditionExternalSnapshot / TriggerExternalSnapshot / TriggerPriceExternalSnapshot / TrailingExternalSnapshot» (по Q-003), «Инварианты структуры», «Связи», «Связанные документы».
  2. `docs/spec/lifecycle/AlgoOrder.md` — переходы Status (CREATED → PENDING → ACTIVE/ERROR → PARTIALLY_COMPLETED/COMPLETED/CANCELED), кто триггерит (Submit/Refresh/Cancel/Amend executors). Cancel semantics (§14 legacy) — здесь же или в C7. Беру: **в lifecycle/AlgoOrder.md** — описание правила «CANCELED ставится только после refresh» как обработка сбоев. ACK-not-truth — общая cross-model invariant, в C7/C8.
- Размер AlgoOrder.md spec оцениваю в 700-900 строк (с подробной таблицей Condition/Trigger/Trailing), под cap 1000.
- **Уровень уверенности:** однозначно.

##### Действие 39: Связные обновления для AlgoOrder

- MODELS.md: `**AlgoOrder**` → ссылка `См. models/core/AlgoOrder.md.`.
- migration-tracker: `AlgoOrder.md` → `done`, перечислить два целевых.
- journal: Q-N (Q-006 уже заведён для Order; для AlgoOrder — Q-007 если возникнет; см. ниже).
- MIGRATED-маркер.

#### Документ D: `docs/domain/processes/Deal management/Сервисные команды.md §12`

##### Действие 40: Анализ §12 (дубль таксономий)

- **Что делал:** прочитал §12.1-12.4. Это enum-копии Order.Status, AlgoOrder.Status, AttachedAlgoOrder.Status, Position.Status с короткими комментариями про strategy_action_id (должен быть убран), PENDING-not-truth, PARTIALLY_COMPLETED.
- **Что бы сделал в реальной миграции:** **ничего не переносится** — по ADR-0006 §3 «§12 в spec самостоятельной записи не порождает; таксономии мигрируют из model-документов Order/AlgoOrder/Position». Комментарии («strategyActionId должен быть убран», «PENDING не ACK-truth», «PARTIALLY_COMPLETED — recovery-status») — уже отражены в Order.md/AlgoOrder.md/Position.md legacy. Дубль чистый.
- **На какие правила опирался:** ADR-0006 §3 (explicit handling §12), скилл `spec-document-migration` шаг 1 «duplicate».
- **Уровень уверенности:** однозначно.

##### Действие 41: Трекер для §12

- **Что бы сделал:** в трекере у строки `Сервисные команды.md §12 (дубль таксономии Order/AlgoOrder/Position)` — статус `done`. Сам файл `Сервисные команды.md` имеет ещё много других разделов (1-11, 13), которые мигрируют в C7. MIGRATED-маркер на Сервисные команды.md — **не ставлю** (shared, мигрирует с C7).

#### Документ E: `docs/domain/processes/Deal management/Статусы торговых сущностей.md` (фрагменты по Order/AlgoOrder/Position)

##### Действие 42: Анализ фрагментов §8.3, §8.4, §8.5

- **Что делал:** §8.3 Order, §8.4 AlgoOrder, §8.5 Position — описания active/closed/live risk, привязки к OKX state, OKX mapping таблицы (effective → COMPLETED и т.п.). Большинство **дубль** с Order.md/AlgoOrder.md/Position.md. Дополнительное:
  - §8.3 явно описывает что reduceOnly не означает «можно игнорировать» — это уже в Order.md §2 (positionReducingOnly).
  - §8.4 живёт частично с OKX mapping таблицами — они **уходят в фазу 2** (по ADR-0006 §3).
  - §8.5 повторяет про CloseReason, PositionStatusResolver, REFRESH_POSITION policy — всё это в Position.md.
- **Что бы сделал в реальной миграции:** дополнительного контента, не покрытого Order/AlgoOrder/Position.md, **нет**. Фрагменты разнесены полностью.
- **Уровень уверенности:** однозначно.

##### Действие 43: Дополнительно — §3-7 «Статусы…» (Общие принципы / Resolver / Exception policy / Exchange-level / Управляющие сущности / Cleanup / Anomaly)

- **Что делал:** §3 «Общий принцип», §5 «Resolver внешних статусов», §6 «External exchange exception policy» — это **не часть C2**, это материал для **C8** (status-resolution.md invariant + anomaly-classification.md). §7 «Управляющие сущности» (Exchange/Instrument/Strategy) — материал для **C-NEW** (Exchange, Instrument) + **C5** (Strategy). §9 «Cleanup rules», §10 «Anomaly rules» — для **C8**.
- **Что бы сделал в реальной миграции:** в C2 не трогаю. Фрагменты «Статусы торговых сущностей.md по Order/AlgoOrder/Position» — статус `done` в трекере; общий файл маркером **не помечается** (shared, ждёт C-NEW/C5/C8).
- **Уровень уверенности:** однозначно.

### Этап 3. Проверка hard gates

#### Gate 1: все зависимости в spec

- Exchange, Instrument, Balance — **отсутствуют** (C-NEW и C4 не пройдены). По прогону — все ссылки прозаические забегающие. Gate **НЕ пройден** в штатном смысле. По условию прогона — допущен с явной фиксацией.

#### Gate 2: карта legacy-источников зафиксирована

- Зафиксирована (Действия 18, 21). **Пройден.**

#### Gate 3: карта целевых spec-документов зафиксирована

- 5 документов: `models/core/Position.md`, `models/core/Order.md`, `lifecycle/Order.md`, `models/core/AlgoOrder.md`, `lifecycle/AlgoOrder.md`. **Пройден** (зависит от Q-003 и Q-006).

#### Gate 4: критические концептуальные вопросы закрыты

- Открытые из C4: Q-003 (ExternalSnapshot — критичен, влияет на 5 model-документов структуру) — НЕ закрыт. Q-001 (имя BalanceContainer) — не относится к C2.
- Новые из C2: Q-004 (как описывать runtime-реакцию на exception без C7/C8), Q-005 (где живёт recovery), Q-006 (lifecycle/Order.md и lifecycle/AlgoOrder.md — ввести жанр или нет; решение в Действии 32), Q-007 (как описать OKX-mapping placeholder до фазы 2).
- Q-004 — **критичный** (описание контракта runtime-реакции — это поле документа). Q-005 — не критичный. Q-006 — критичный (если решение «вводим lifecycle» — это лишний документ; если «не вводим» — переходы внутри model). Q-007 — структурный.
- **Gate НЕ пройден.** Минимум Q-003, Q-004, Q-006 нужно закрыть.

#### Gate 5: влияния на следующие кластеры зафиксированы

- C5 (Strategy ссылается на runtime entities) — неблокирующее.
- C1 (Deal runtime graph) — неблокирующее.
- C7 (ServiceCommand, executor, RiskValidator) — частично блокирующее: detail runtime-реакции на exception описано в C2 legacy, но spec-вариант требует обоюдного решения. Q-004 — закрытие.
- C8 (status-resolution invariant, anomaly-classification, resolver-pattern) — частично блокирующее. Зависит от Q-004.
- **Пройден частично.** Зависит от Q-004.

### Этап 4. Финализация

- **Прошла / не прошла:** **НЕ прошла.** Причины:
  1. Gate 1 — Exchange, Instrument, Balance не в spec (C-NEW и C4 — `planned`/`in-progress`). Забегающие ссылки прозой допустимы, но это не закрытие gate, это явное обходное решение.
  2. Gate 4 — открытые Q-003, Q-004, Q-006 — блокирующие.
- **Что не дало закрыть кластер:**
  - Незавершённый C-NEW (формально C2 не должен стартовать).
  - Незавершённый C4 (с открытыми Q-001, Q-003).
  - Концептуальный Q-004 (runtime-реакция на exception без C7/C8 в spec) — описание контракта в model-документах Order/AlgoOrder/Position зависит от того, как этот контракт оформлен в C8 invariant. Без него получаем либо дубль (Order.md, AlgoOrder.md, Position.md каждый описывает «Deal → ERROR, Exchange → HOLD»), либо прозаическое упоминание без структуры.
  - Структурный Q-006 (вводить lifecycle для Order/AlgoOrder или нет) — решает, 3 или 5 целевых документов.
- **Это совпадает с ожиданием в задании:** «Этап 4 финализации у C2 ожидаемо НЕ проходит». Согласовано: C-NEW не сделан → забегающие ссылки на Exchange/Instrument → gate 1 не закрыт штатно. Также вылезли собственные Q-N.
- **Если бы инфраструктура «не заметила» нарушения** — это был бы дефект скилла spec-cluster-migration или ADR-0009. Здесь — заметила. Это работает корректно.

### Q-N, которые я бы завёл (по C2)

```
### Q-004 — Где описывается runtime-реакция «entity → ERROR, Deal → ERROR, Exchange → HOLD»

| | |
|---|---|
| Status | Added |
| Classification | concept |
| Source | dry-run миграции C2, 2026-05-17; Order.md §7, §2.1; AlgoOrder.md §10; Position.md §13 invariants; Статусы торговых сущностей.md §6 |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

Runtime-реакция на controlled exchange exception (ExternalStatusException / ExternalInvariantViolationException / ExternalNotFoundException) описана в каждом из Order.md, AlgoOrder.md, Position.md, Статусы торговых сущностей.md как одинаковая цепочка: entity → ERROR, closeReason = <reasonCode>, Deal → ERROR, Exchange → HOLD. Это **кросс-модельное правило** (по ADR-0006 §2, субъект — три модели одновременно + общий контракт), и по ADR-0006 §3 относится к `docs/spec/invariants/status-resolution.md` (целевой документ C8). В model-документах Order/AlgoOrder/Position spec — что именно остаётся? Полное упоминание контракта (дубль) или короткая ссылка на invariant (но invariant в C8 ещё не написан)? Если короткая ссылка — это inline-ссылка на ещё-не-созданный документ (запрещено по ADR-0007 — markdown-link на несуществующий файл); если прозаически — теряется точная привязка.

**Context.**

C2 идёт по очерёдности до C8. По ADR-0009 §5 забегающая ссылка прозой допустима. Но runtime-реакция — это не «упоминание модели», это «полный контракт правила». Прозой получится «при ошибках разрешения внешнего статуса entity переходит в ERROR, что приводит к ERROR сделки и приостановке торговли на бирже» — без перечисления конкретных closeReason и без точных условий. В legacy эти правила описаны детально (например, OrderExternalSnapshot.failCode mapping). Альтернативы: (a) дубль в каждом model — нарушает ADR-0002 §2; (b) забегающий invariant-документ в C2 (создаём `invariants/status-resolution.md` сейчас, наполняем при C8) — нарушает порядок кластеров; (c) inline-ссылка «см. invariants/status-resolution.md» — нарушает ADR-0007 §1 (документ не существует). Нужно концептуальное решение.

**Notes.**

—

**Resolution.**

—
```

```
### Q-005 — Где описывается recovery-сценарий runtime-сущностей

| | |
|---|---|
| Status | Added |
| Classification | structural |
| Source | dry-run миграции C2, 2026-05-17; Position.md §16; AlgoOrder.md §13.2 (algo evidence-cycle); Order.md §10.3 (REFRESH_ORDER_HISTORY); косвенно Balance.md §10 (FSM precondition) |
| Added | 2026-05-17 |
| Related | Q-004 |

**Formulation.**

Recovery-сценарии (поведение системы после падения приложения, evidence-cycle для подтверждения terminal-фактов, восстановление locally-otsutstvuyuschey Position по фактам биржи) описаны во многих legacy-документах. В spec они уходят в process-документы (C7) или lifecycle/Deal.md (C1)? Куда конкретно?

**Context.**

В Position.md §16 recovery-сценарий описан как пример работы DealOrchestratorJob с цепочкой REFRESH_*. Это типичный процесс. В AlgoOrder.md §13 — algo evidence-cycle как алгоритм. По ADR-0002 §1 жанр process — оркестрация, jobs, executor-flow. По ADR-0006 §2 — это кросс-модельный invariant (правило «evidence-cycle не закрыт пока не все источники проверены»). Beforehand: alternatives — (a) один process-документ `processes/refresh-evidence-cycle.md` в C7, (b) invariant `invariants/evidence-cycle.md` в C8, (c) распределить по lifecycle-документам каждой сущности. Решение влияет на структуру 3 кластеров.

**Notes.**

—

**Resolution.**

—
```

```
### Q-006 — Жанр для переходов Status у Order/AlgoOrder — model или lifecycle

| | |
|---|---|
| Status | Added |
| Classification | structural |
| Source | dry-run миграции C2, 2026-05-17; Order.md §3 (transition methods toCancel/toComplete/toError); AlgoOrder.md §3 (transitTo + isTransitionForbidden матрица) |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

Для Order и AlgoOrder переходы Status триггерятся внешними процессами (Submit/Refresh/Cancel/Amend executors). По ADR-0006 §3 «Enum-декларация и переходы — в model-документе сущности; кто двигает (executor, resolver, finalize-handler) описывается отдельно». Это можно понять двояко: (a) и enum, и таблица переходов в model-документе, а «кто двигает» — отдельно (в process-документе C7 или в lifecycle); (b) полная таблица переходов с колонкой «кто триггерит» — в lifecycle-документе. Выбор влияет на: создаются ли `lifecycle/Order.md` и `lifecycle/AlgoOrder.md` (как для AnomalyReport, который уже мигрирован). Для Position (3 статуса, переходы тривиальные) lifecycle-документ заведомо не нужен. Для Order/AlgoOrder — спорно.

**Context.**

Существующий `lifecycle/AnomalyReport.md` — прецедент введения отдельного lifecycle. Это означает, что инфраструктура поддерживает оба варианта. ADR-0006 §3 формально не запрещает ни одного. Беру в dry-run «с lifecycle» (вариант b) для Order/AlgoOrder. Q-N нужен для подтверждения.

**Notes.**

—

**Resolution.**

—
```

```
### Q-007 — Как помечать «детали OKX-mapping вынесены в фазу 2» в model-документах фазы 1

| | |
|---|---|
| Status | Added |
| Classification | structural |
| Source | dry-run миграции C2, 2026-05-17; Order.md §7.2 (OKX mapping); AlgoOrder.md §11 (OKX mapping); Position.md §10 (OKX REFRESH_POSITION endpoint); Balance.md (косвенно через §9) |
| Added | 2026-05-17 |
| Related | — |

**Formulation.**

В model-документах фазы 1 (Order, AlgoOrder, Position, BalanceContainer, IndicatorValue и т.д.) есть отсылки к конкретным OKX-эндпоинтам, OKX raw state names и таблицам соответствия. По ADR-0006 §3 эти таблицы уезжают в фазу 2 (`integrations/okx/mapping/`). В фазе 1 — нейтральная формулировка «integration-документация биржи». Это создаёт «пробел» в spec до фазы 2: читатель Order.md не видит ни таблицы, ни конкретной ссылки. Допустимо ли это, или нужен унифицированный placeholder (например, навигационная таблица в конце model-документа с пустой строкой «Маппинг OKX — фаза 2, см. legacy `docs/domain/models/mapping/okx/OKX_*_mapping.md`»)?

**Context.**

Legacy `docs/domain/models/mapping/okx/OKX_*_mapping.md` существуют и описывают конкретный OKX mapping. По ADR-0009 §1 в фазе 1 эти файлы остаются как есть до фазы 2 (тогда они мигрируют в `integrations/okx/`). По шаблону `model.md` есть опц. раздел «Связанные документы» — там можно дать строку с указанием на legacy-файл. Это **отсылка на legacy после migration** — концептуально неприятно (полу-источник правды), но операционно корректно (читатель получает информацию). Альтернатива: вообще ничего не упоминать, читать только integration-документ. Q-N нужен для единого правила.

**Notes.**

—

**Resolution.**

—
```

---

## Сводка

### Однозначные решения

- Состав кластеров C4 и C2 — берётся из ADR-0009 §2 и migration-tracker, не дублируется в скилле.
- `Сервисные команды.md §12` в C2 — полностью дубль, в spec не порождает самостоятельной записи (правило ADR-0006 §3, скилл `spec-document-migration` шаг 1).
- `Статусы торговых сущностей.md §8.6 (BalanceContainer)` в C4 — дубль Balance.md, в spec не добавляет.
- Lifecycle для Position **не создаю** (нет полноценной FSM, переходы тривиальные).
- Lifecycle для BalanceContainer **не создаю** (нет Status и lifecycle по семантике legacy).
- AttachedAlgoOrder — вложенная модель внутри `models/core/Order.md`, не отдельный документ (по операционному критерию ADR-0006 §1).
- Condition / Trigger / TriggerPrice / Trailing / ConditionType — вложенные внутри `models/core/AlgoOrder.md`.
- Все Java-сниппеты переходят в таблицы полей; SQL DDL и `@Entity`-аннотации не переезжают (ADR-0002 §6).
- Java-интерфейсы (BalanceFreshnessChecker, *StatusResolver classes) — не переезжают (это реализация, не контракт).
- OKX mapping таблицы из Order.md §7.2 и AlgoOrder.md §11 — **не переезжают** в фазу 1; в spec-документах фазы 1 — нейтральная формулировка «integration-документация биржи».
- Frontmatter каждого нового spec-документа: `status: draft`, `last_review: 2026-05-17`, `related_adrs: [ADR-0001, ADR-0006, ADR-0009]`.
- Имена файлов — PascalCase для классов: `BalanceContainer.md` (если Q-001 → BalanceContainer) / `Balance.md` (если Q-001 → Balance) / `Order.md`, `AlgoOrder.md`, `Position.md`.
- MIGRATED-маркер на shared-файлах (Статусы торговых сущностей.md, Сервисные команды.md) — НЕ ставлю в C4/C2 (по скиллу `spec-document-migration` маркер для shared-файла — только после полной разноски).
- Терминологические замены — автоматически: «entity» → «доменная модель», «persisted» → «хранимое».
- Backlog не получает новых пунктов «концептуальный вопрос X» — они идут в журнал как Q-N (по ADR-0008 §6).

### Склонялся

- **Порядок документов в C2** — `Position → Order → AlgoOrder` против `Order → AlgoOrder → Position`. Выбрал первый (Position автономна, малочисленна, отрабатывает простой случай первой). Альтернатива оправдана (Order содержит AttachedAlgoOrder как вложенную модель — тестирует декомпозицию).
- **Жанр для переходов Status у Order/AlgoOrder** — model или lifecycle. Выбрал **с lifecycle** (есть transition methods и внешние триггеры). Альтернатива (только model) проще; Q-006 для подтверждения.
- **Имя aggregate в C4** — `BalanceContainer.md` против `Balance.md`. Выбрал `BalanceContainer` (фактический aggregate root). Альтернатива — оставить `Balance` (как уже зафиксировано в MODELS.md, без переименования). Q-001 для решения.
- **Раздел «Что не хранится» в model-документах** — включать или нет. Выбрал **включать** (полезный конструктивный сигнал, отсекает попытки добавить лишние поля). Альтернатива — не включать (spec = текущее состояние, не negative).
- **`models/runtime/BalanceContainerExternalSnapshot.md` отдельным документом** против раздела внутри model. Выбрал раздел внутри (по природе — вспомогательная структура-проводник). Q-003 для решения.

### Ступор

- **Q-001: имя aggregate в C4 (BalanceContainer vs Balance).** MODELS.md уже зафиксировал `Balance`, но legacy и Java-классы — `BalanceContainer`. Правила переименования (скилл `spec-models-registry`) требуют ADR. Это структурное решение, должно быть принято до старта исполнения C4.
- **Q-002: где живёт freshness invariant.** ADR-0009 §9 говорит «получает собственный spec-документ при миграции соответствующего кластера», но не уточняет какого. Кандидаты: C7 (RiskValidator), C8 (Audit / status-resolution), новый invariant в C4. Не блокирует C4 как model-документ, но не даёт замкнуть концепцию freshness в spec.
- **Q-003: куда едет normalized ExternalSnapshot.** Применяется ко всем «торговым» model-документам фазы 1 (Balance, Order, AlgoOrder, Position, MarketData). ADR-0007 §2 описывает только raw DTO бирж в `integrations/<exchange>/models/`, не normalized snapshot. ADR-0006 §1 даёт runtime-критерий, но snapshot — не aggregate root.
- **Q-004: где описывается runtime-реакция «entity → ERROR, Deal → ERROR, Exchange → HOLD».** Описано в legacy в 4 местах одинаково — это типичный кросс-модельный invariant для C8 (`invariants/status-resolution.md`). Но C8 идёт после C2 — в C2 model-документах эту runtime-реакцию нужно либо дублировать (нарушение ADR-0002 §2), либо сильно упростить прозой (теряется детализация). Концептуальное решение, не структурное.
- **Q-005: где описывается recovery-сценарий.** Применяется к 3+ моделям. Куда: process-документ в C7, invariant в C8, распределение по lifecycle-документам. Влияет на 3 кластера.
- **Q-006: жанр для переходов Status у Order/AlgoOrder.** ADR-0006 §3 формулировка двусмысленна (см. Q-006 формулировку). Решение «вводить lifecycle/Order.md и lifecycle/AlgoOrder.md» подкреплено только прецедентом `lifecycle/AnomalyReport.md`. Нужно правило.
- **Q-007: placeholder для OKX-mapping в model-документах фазы 1.** ADR-0009 §1 говорит «нейтральная формулировка», но не задаёт шаблон ссылки на legacy `OKX_*_mapping.md`. Без шаблона разные авторы напишут разное.

### Несостыковки инфраструктуры

1. **MODELS.md vs legacy: `Balance` vs `BalanceContainer`.** MODELS.md фиксирует `Balance`, legacy и Java — `BalanceContainer`. Это не разрешено никаким артефактом. По скиллу `spec-models-registry` «Переименование модели» требует ADR. По ADR-0006 §1 — это не «переименование» (исходное `Balance` — другая модель, currency-level snapshot). Это **ошибка регистрации** имени в MODELS.md на старте фазы 1.

2. **ADR-0007 §1 формулировка «лежат в правильной папке для своего жанра»** не учитывает, что `docs/spec/models/AnomalyReport.md` физически лежит **не** в `models/core/`, а прямо в `models/`. Это уже зафиксированный долг (см. ADR-0006 §Consequences «AnomalyReport.md»). Mожет помешать gate 2 проверки skill `spec-document-workflow` («Файл лежит в правильной папке для своего жанра») для существующего AnomalyReport — пока он не переехал в `core/`, документы кластеров C-NEW/C4/C2 видят несоответствие соседа стандарту, что может ошибочно сработать как сигнал.

3. **ADR-0009 §4 Gate 1 vs §5 Забегающая ссылка.** Gate 1 говорит «все зависимости в spec». §5 разрешает забегающую ссылку прозой. Их соотношение: gate 1 «закрывается» забегающей ссылкой, или забегающая ссылка — это исключение из gate? Скилл `spec-cluster-migration` Этап 1 п.2 формулирует: «либо явно решается, как обрабатывать забегающие ссылки» — но не описывает, как оформляется «явное решение». В отчёте это создаёт сомнение по C4 и C2 (формально gate 1 не пройден; формально dry-run прогон допущен по условию задания, но не по штатной процедуре).

4. **Отсутствие правила для placeholder на legacy `OKX_*_mapping.md`.** ADR-0009 §1 и ADR-0007 §2 описывают, что фаза 2 переносит маппинги. Но в фазе 1 model-документы Order/AlgoOrder/Position **должны** упомянуть, где живёт маппинг. Прозаически — потеря точности. Markdown-link на legacy — формально нарушение (markdown-link только на spec в иерархии). См. Q-007.

5. **Скилл `spec-cluster-migration` Этап 2 п.3 vs п.4** — про MIGRATED-маркер. Скилл говорит «MIGRATED-маркер не ставится до закрытия всех Q-N по документу», и одновременно «обновить трекер: статус документа `done` (или остаётся `in-progress`, если есть открытые Q-N)». Это согласовано. Но **скилл не описывает критерий «блокирующий Q-N»**. В моём отчёте Q-002 «не блокирует C4», Q-005 «не блокирует C2 model-документы» — это решение я принимаю **сам**. Скилл не даёт явного критерия (например: «Q-N блокирует документ, если ответ влияет на поля или структуру разделов»). Это есть в ADR-0009 §4 Gate 4 («Знак критичности: ответ влияет на поля документов, не на пояснения»), но **не дублируется** в скилле, и применяется только к gate 4 на уровне кластера, не на уровне документа.

6. **ADR-0006 §3 формулировка про `lifecycle` для Order/AlgoOrder.** «Enum-декларация и переходы статусов — в model-документе сущности» — это явно говорит «не в lifecycle». Но ниже: «кто двигает (executor, resolver, finalize-handler) описывается отдельно (в кросс-модельных инвариантах или process-документах)». Если переходы триггерятся снаружи, и описание триггера — отдельно, то либо переходы и триггеры распределены по двум документам (model = переходы; process/invariant = триггеры) — это разрыв; либо переходы в model только как enum + семантика, а полная таблица «from-to-кто триггерит» в lifecycle. ADR-0006 явно говорит **первое** для torгующих сущностей; ADR-0007 §4 и ADR-0002 §1 жанр lifecycle сохраняют — но без явного указания, когда он применяется. См. Q-006.

7. **Шаблон `model.md` имеет раздел «Связи»** (с пунктами `**<Имя модели>** — <роль>`). Эти пункты — это **markdown-link** или **прозаически имя**? Если model связана с моделью, для которой ещё нет model-документа (забегающая ссылка) — то прозаически. Если есть — markdown-link. Шаблон не явный. Скилл `spec-cluster-migration` забегающие ссылки описывает только для прозы в теле документа, не для раздела «Связи». Это может привести к разнобою.

### Q-N, которые я бы завёл (сводка по обоим кластерам)

| id | title | classification | кластер | блокирующий? |
|---|---|---|---|---|
| Q-001 | Каноническое имя для aggregate (BalanceContainer vs Balance) | concept | C4 | да (gate 4 C4) |
| Q-002 | Где живёт freshness invariant как cross-model документ | concept | C4 | нет (не влияет на поля Balance.md) |
| Q-003 | Куда едет normalized ExternalSnapshot (Balance/AlgoOrder/Order/Position и далее) | structural | C4 (применяется ко всем «торговым») | да (gate 4 C4, gate 4 C2, gate 4 C6) |
| Q-004 | Где описывается runtime-реакция «entity → ERROR, Deal → ERROR, Exchange → HOLD» | concept | C2 | да (gate 4 C2) |
| Q-005 | Где описывается recovery-сценарий runtime-сущностей | structural | C2 | нет (не влияет на поля Position.md / Order.md / AlgoOrder.md) |
| Q-006 | Жанр для переходов Status у Order/AlgoOrder — model или lifecycle | structural | C2 | да (gate 4 C2 — количество целевых документов) |
| Q-007 | Как помечать «детали OKX-mapping вынесены в фазу 2» в model-документах фазы 1 | structural | C2 (и далее) | нет (не влияет на структуру, влияет на единообразие) |

Все формулировки полностью — в разделах «Q-N по C4» и «Q-N по C2» выше.
