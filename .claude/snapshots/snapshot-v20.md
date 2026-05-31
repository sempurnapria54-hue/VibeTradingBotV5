# Snapshot v20

**Дата:** 2026-05-31.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после **чистого `DOCS_CHECK_4`**
шага 1 Фазы 1 и **подготовки тулинга/методологии под под-шаг `CODE`**:
наполнение code-стабов, настройка ревью-фокусов, шаг «Безопасность» в
роадмапе, новые правила доков, ренейм тира `rest → api`). Подготовлен
под переезд в новый чат.

## Состояние

Фаза 1 — `IN_PROGRESS`; шаг 1 (поток рыночных данных) — прошёл
`TOOLING` → `DOCS_CHECK_1` → `GAPS_CLOSE_1` → `DOCS_CHECK_2` →
`GAPS_CLOSE_2` → `DOCS_CHECK_3` → `GAPS_CLOSE_3` → **`DOCS_CHECK_4`
(чисто)**. Исполнение по
`.claude/processes/roadmap-step-execution.md`. `DOCS_CHECK_4` —
полный сквозной прогон `concept-review` под код: 0 находок, 0
эскалаций; рекомендация — `GAPS_CLOSE_4` не нужен, концепция шага 1
**готова к `CODE`**. Следующее действие — решение пользователя:
переход к **`CODE`**.

## Что изменилось относительно v19

### Шаг 1: DOCS_CHECK_4 — чисто

- Статус шага 1: `GAPS_CLOSE_3` → `DOCS_CHECK_4` (`phase-1.md`).
  Ролляп фазы 1 — `IN_PROGRESS` без изменений.
- Прогон `phase-1-step-1-docs-check-4.md`: несогласованности 0,
  name-level 0, неотвеченные 0 новых, эскалаций 0; все четыре фокуса
  `GAPS_CLOSE_3` (состав `InstrumentExternalSnapshot`; единый шаг-1
  дом `state`/`lever` = `Instrument`; различение `leverage` ↔
  `externalLeverage`; онбординг-lifecycle на `SYNC`) — чисты; прошёл
  все стадии. Итог: сквозной прогон концепции под шаг 1 завершён без
  находок; шаг 1 готов к `CODE`.

### Подготовка тулинга под CODE (наполнение стабов)

- **`tech-radar`** наполнен: стэк **Java 25 / Spring Boot 4 /
  PostgreSQL** + Hibernate (JPA); записи `adopt` (Spring Security,
  Apache Commons `BooleanUtils`/`CollectionUtils`, Lombok, MapStruct,
  springdoc); политика «**мягкая база**» — `code-writer` опирается,
  но может предлагать новые либы (автономный выбор — на будущее).
- **`codestyle`** наполнен: принципы (DDD / Clean Code / rich-домен /
  Lombok); нейминг по слоям (**api**: `Instrument` /
  `InstrumentExternalSnapshot` / `Instrument{Exchange}Response` /
  `InstrumentEntity` / `InstrumentApiResponse`); зоны
  ответственности слоёв (Controller / Service / ClientService /
  DataService / `OkxRestClient` / Repository); маппинг только
  MapStruct (цепочка api→domain даже 1:1); Lombok-правила (`@Data`
  не использовать); форматирование; строгие правила (null —
  только `Objects.isNull/nonNull`; запрет отрицаний → `BooleanUtils`;
  `CollectionUtils`; фигурные скобки всегда; `Objects.equals`);
  контроллеры/API (Swagger + валидация, `@ParameterObject` при >2
  параметрах); логирование (секреты не логируем). **Error-handling —
  раздел `TBD`** (коды; `@ControllerAdvice` vs per-endpoint;
  `@ApiResponses`), не зафиксирован.
- **`CLAUDE.md`** выровнен: Java 21 / Spring Boot 3 → **Java 25 /
  Spring Boot 4** (под `tech-radar`).

### Ревью-фокусы (под-шаг CODE)

- `performance-review` + дедлоки и конкурентный доступ;
  `disaster-review` + оптимистичные блокировки, ретраи, транзакции.
- **`security-review` деактивирован** до шага «Безопасность» (скилл +
  реестр `reviewer.md` + процесс + ростер-нота). **Активные
  `CODE`-фокусы: `conventions` / `performance` / `disaster`.**

### Роадмап: новый шаг «Безопасность»

- `phase-1.md`: вставлен шаг **9 «Безопасность»** (Spring Security,
  `@PreAuthorize`, `SecurityFilterChain`; конфигурация секретов через
  Vault; реактивирует фокус `security-review`) **перед** «Тесты».
  Ренумбер: Тесты 9→**10**, Фронт 10→**11**. Содержание —
  docs-first на самом шаге.
- `backlog`: раздел «Шаг «Безопасность» — форвард-материал» (S1 —
  Vault-конфигурация секретов: `vault://` только в
  `spring.config.import`, env-плейсхолдеры, реальные секреты не
  коммитим; S2 — auth-инфраструктура + реактивация `security-review`).
- Сопутствующий ренумбер ссылок на «Тесты» (`reviewer.md`,
  ростер-нота).

### Новые правила доков (3)

- `docs/rules/time-utc.md` — время **везде UTC** (код и БД).
- `docs/rules/idempotency-via-unique.md` — уникальность/идемпотентность
  через **UNIQUE-индексы + безопасный upsert**; ключевые
  UNIQUE-инварианты фиксируются в `docs/models/domain`.
- `docs/integrations/okx/rules/timeframe-constants.md` — OKX-таймфреймы
  **case-sensitive**, только константы (`OkxTimeframes`), запрет
  lower-case-нормализации.

### Ренейм тира rest → api

- `git mv docs/models/rest → docs/models/api`; обновлены
  `structure.md`, `model-layer-ontology.md`, `classify-type.md`,
  README. История / снапшоты (immutable) не правились — там остались
  исторические ссылки `docs/models/rest`.

### Open-questions

- **REF-Q1** (новый, 2026-05-31): тип, место и характер
  референс-доков для `code-writer` (опора `find-code-examples`).
  По решению — первый референс-док **не создан**, маркер P1 в
  backlog **оставлен**; кандидат-шаблон контроллера (api-нейминг,
  `<Model>`-плейсхолдеры, `@PreAuthorize` TODO, `@ApiResponses`
  закомментирован) припаркован в вопросе. Концепция референс-доков
  прорабатывается отдельно в чате.
- Всего открыто **16** (было 15 + REF-Q1).

## Активные задачи

Шаг 1 Фазы 1 (поток рыночных данных): `DOCS_CHECK_4` пройден чисто;
активна — решение по переходу к `CODE`. Прочих активных задач нет.
Прогресс-файлы: `phase-1-step-1-docs-check-1.md` … `-docs-check-4.md`,
`-gaps-close-1.md` … `-gaps-close-3.md`.

## Текущий фронтир / следующее действие

- **Переход к `CODE`.** Концепция шага 1 готова (`DOCS_CHECK_4`
  чисто, `GAPS_CLOSE_4` не нужен). Тулинг кода наполнен:
  `tech-radar` / `codestyle` содержательны; ревью-фокусы настроены
  (активны `conventions` / `performance` / `disaster`).
- **Открытый хвост тулинга под `CODE` — REF-Q1.** Референс-доки для
  `code-writer` (example-скилл `find-code-examples`) ещё не имеют
  типа/места. До решения `CODE` либо идёт без референс-доков, либо
  REF-Q1 разбирается первым. Маркер P1 в backlog активен.
- Тулинг `concept-review` / `reviewer` / `code-writer` — в обкатке
  (форма дорабатывается, снимается с первого реального `CODE`).

## Открытые общие вопросы

`open-questions.md`: DEAL-Q1, DEAL-Q2, PROC-Q1, RISK-Q1, TIME-Q1
(для кода шага 1 закрыт, хвост — шаг 2), INSTR-Q1, INSTR-Q2, ORCH-Q1,
ENUM-Q1, CMD-Q1, OKX-Q1, OKX-Q2, OKX-Q3, OKX-Q4 (разблокирован для
шага 1), DEAL-Q3, **REF-Q1** (новый) — все **16** открыты. Шаг 1 не
блокирует ни один (INSTR-Q1 / INSTR-Q2 / ORCH-Q1 — отложенные детали,
не гейты).

## Что в работе

- Шаг 1 Фазы 1: `DOCS_CHECK_4` пройден чисто; следующее — `CODE`
  (решение пользователя), с открытым хвостом REF-Q1 (референс-доки).
- **Project Knowledge требует обновления:** последний снапшот теперь
  **`snapshot-v20`** (заменяет v19 в префлайте). Также в PK правились
  **`CLAUDE.md`** и **`.claude/rules/structure.md`** — обновить их
  копии в PK после коммита.
- Затронуто: code-тулинг (`tech-radar`, `codestyle`,
  `{performance,disaster,security}-review`, `reviewer.md`,
  `roadmap-step-execution.md`, ростер-нота), роадмап (`phase-1.md` —
  +шаг «Безопасность», ренумбер), `backlog.md` (форвард-материал),
  `docs/` (3 новых правила; ренейм тира `rest → api`),
  `open-questions.md` (+REF-Q1).
