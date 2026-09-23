# Снапшот v417

**Дата:** 2026-09-23.

## На какой вопрос отвечает этот файл

Где мы сейчас.

## Состояние

Сменяет v416. Фаза 1 — `FOLDED`. **Фаза 2 — `IN_PROGRESS`**: шаги 1-11
`DONE`, **шаг 12 — `DOCS`**, шаг 13 `HOLD`. Прод-рубеж — `HOLD`.

**Машина шага** — `CONCEPT → CONCEPT_REVIEW → CODE → DOCS → REVIEW → DONE`
(`.claude/processes/roadmap-step-execution.md`).

**Дельта — в рабочем дереве, не закоммичена.** Ветка `claude-audit`.

**Режим — автономия до прод-рубежа.** Решения — Д250-Д2066
(`.claude/work/decision-digest.md`).

## Что сделано после v416

- **Доковый заход `DOCS` по артефакту `trading-core` закрыт** — заход 135
  (`.claude/work/progress/phase-2-step-12-chronicle.md` §«Шестьдесят седьмой
  заход (135) — доковый заход `DOCS`: дельта `trading-core`», Д2066).
- **Приведено к коду:** тропа чтения состава durable-потребителей в
  `docs/architecture/data-ownership.md` (клиенты брокера вместо
  `spring.kafka`), строка `common/strategy-engine` в
  `docs/architecture/services.md`, ненастроенная тропа событий в
  `docs/architecture/services/trading-core.md`, указатели на `V4` → `V1`.
- **Новая ловушка** — CS-029 (прокси абстрактного предка у маппера),
  `.claude/rules/codestyle.md` §«Ловушки».

## Вход следующей сессии

**Единица — доковый заход `DOCS` шага 12: сверка доков с продуктовой дельтой
шага по артефакту `strategies`.** Перечень артефактов и команда — хроника
шага, заход 134; порядок дальше — `statistics`, `bff`,
`common/model/domain`, `connector-okx`, `auth`; после последнего — `REVIEW`.
Замечание для `REVIEW` (фокус `divergence`) записано в заходе 135.

## Что держателю решать

1. **Находка `F6` первой тропы стоит на пути всей торговли** — ни одна
   активация не доезжает до копии у ядра. Секция
   `.claude/work/backlog.md` §«Снимок определения несёт числовые ключи базы
   владельца, и копия у ядра не заводится»; за ней — половина сквозного
   набора.
2. **Источник цены `MARK_PRICE` / `INDEX_PRICE`** —
   `.claude/work/backlog.md` §«Источник цены `MARK_PRICE` / `INDEX_PRICE`
   не отвергается создáнием».
3. **Читатель `exitOutcome`** — там же §«Журнальные отчёты исходов
   округления выхода — читателя `exitOutcome` в ядре нет».
4. **Ключи demo-окружения OKX и распечатанный Vault** — без них не
   прогоняется ни один кейс мишени `-D` и весь `smoke-live`.

Прочие ожидания держателя печатает `py tools/backlog-check.py` строкой
`держатель:`.

## Что висит

Висящее живёт секциями `.claude/work/backlog.md` и
`.claude/work/prod-checks.md` и печатается `py tools/backlog-check.py`; в
снапшоте не дублируется. Этим проходом секций не заведено и не закрыто.

**Пользователю:** обновить Project Knowledge (этот снапшот).

## Ловушки

Ловушки живут в домах по своему вопросу; снапшот их не держит. В разделе
ловушек дома — оглавление: споткнулся — найди строку по словам области и
симптома, тело читай по номеру в `.claude/traps/<дом>-traps.md`.

- среда, оболочка, правка корпуса скриптом —
  `.claude/skills/environment-commands.md` §«Ловушки и обходы»;
- код тестов — `.claude/skills/test-code.md` §«Ловушки и обходы»;
- письмо кейсов и их ревью — `.claude/skills/test-design.md`,
  `.claude/skills/test-review.md`;
- продуктовый код — `.claude/rules/codestyle.md` §«Ловушки»;
- команды корпуса и свипы — `.claude/rules/measurement-commands.md`;
- условие маркера бэклога — `.claude/rules/backlog-section-form.md`
  §«Ловушки»;
- гейт и статус — `.claude/skills/update-roadmap-progress.md`;
- цикл сессий — `.claude/skills/session-chain.md` §«Ловушки и обходы».

## Проверки на момент снапшота

Дерево кода этим заходом не тронуто — реактор не гонялся; последние числа —
заход 133 в хронике шага. Гейт инструментов корпуса — целиком, одним прогоном
на финальном состоянии (`.claude/skills/update-roadmap-progress.md` §«Гейт
инструментов корпуса»); исход — отчёт прохода.

## Среда

Дом фактов и ловушек среды — `.claude/skills/environment-commands.md`;
стенда — `.claude/skills/local-stand.md`. Изменений среды нет.
