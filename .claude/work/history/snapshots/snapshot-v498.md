# Снапшот v498

**Дата:** 2026-09-29.

## На какой вопрос отвечает этот файл

Где мы сейчас.

## Состояние

Сменяет v497. Фаза 1 — `FOLDED`. **Фаза 2 — `IN_PROGRESS`**: шаги 1-11
`DONE`, **шаг 12 — `REVIEW`**, шаг 13 `HOLD`. Прод-рубеж — `HOLD`.

**Машина шага** — `CONCEPT → CONCEPT_REVIEW → CODE → DOCS → REVIEW → DONE`
(`.claude/processes/roadmap-step-execution.md`).

**Дельта — в рабочем дереве, не закоммичена** (поверх `50e04ba5`). Ветка
`claude-audit`.

**Режим — автономия до прод-рубежа.** Решения — Д250-Д2305
(`.claude/work/decision-digest.md`).

## Что сделано после v497

- **Узел 1e плана остатка, заход 221** — строка отказа доступа у сервисов со
  своей базой: форма строки (модель и сущность) переехала в общие артефакты
  `common/model/domain` и `common/model/persistence`; у `auth`,
  `trading-core`, `strategies`, `market-data` заведены таблица и писатель
  порта; копии писателя объявлены семействами `tools/peer-copy-check.py`.
  Сняты три секции бэклога, закрыты находки `F-5` (`auth`) и `F-6`
  (`trading-core`). Итог —
  `.claude/work/history/2026-09-29-step-12-access-denial-writers.md`;
  запись — хроника шага `.claude/work/progress/phase-2-step-12-chronicle.md`,
  §«Сто пятьдесят третий заход (221) — писатель строки отказа доступа у сервисов со своей базой».

## Вход следующей сессии

Незакрытого закрытия нет. Единица — **узел 1e плана остатка**: следующая
секция `сейчас` с вердиктом `R` отчёта триажа
`.claude/work/progress/phase-2-step-12-parking-triage.md`, ещё стоящая в
бэклоге, либо связка секций одного дома или компонента. Порядок владельцев в
1e — `code-writer` и сервисы, затем `integrator`, `solution-designer`,
`knowledge-curator`; действующий перечень — `py tools/backlog-check.py`.
Полного реактора до узла 4 (валидация) нет.

## Что держателю решать

1. **Апрув `REVIEW` шага 12** — после узла 4 плана (зелёная валидация).
2. **Три клетки `E2.5`, `E2.6`, `E3.1` ждут снятия контурной половины гейта
   входа** — продуктового хода (потолок одновременного риска тенанта).
3. **Плечо пар стенда** — вход по паре без назначенного плеча отвергается;
   к первому прогону на стенде плечо назначается точкой
   `PUT …/pair-settings/…` (число — риск-аппетит держателя).
4. **Вход `claude` перед запуском цикла** — цикл без действующего входа
   останавливается кодом 17.

Прочие ожидания держателя печатает `py tools/backlog-check.py` строкой
`держатель:`.

## Что висит

Висящее живёт секциями `.claude/work/backlog.md` и
`.claude/work/prod-checks.md` и печатается `py tools/backlog-check.py`; в
снапшоте не дублируется.

**Стенд:** отставание стенда от дерева — своя секция бэклога; базы
пересоздаются перед подъёмом. С заходом 221 миграции прибавились у `auth`
(`V3`), `trading-core` (`V12`), `strategies` (`V2`) и `market-data` (`V2`) —
таблица `access_denials`. **Периметр на стенде:** оси `PerimeterProperties`
валидируются при подъёме; манифест периметра несёт `ServiceMonitor` и метку
на службе.

**Пользователю:** обновить Project Knowledge (этот снапшот).

## Ловушки

Ловушки живут в домах по своему вопросу; снапшот их не держит. В разделе
ловушек дома — оглавление: споткнулся — найди строку по словам области и
симптома, тело читай по номеру в `.claude/traps/<дом>-traps.md`.

- среда, оболочка, правка корпуса скриптом, площадка OKX —
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

**Гарантия — зелёное для тронутого; полный реактор — при валидации шага.**
Заход 221 (2026-09-29): модули `services/common/model/domain`,
`services/audit`, `services/statistics`, `services/auth`,
`services/trading-core`, `services/strategies`, `services/market-data` —
`bash tools/reactor-test.sh --modules
services/common/model/domain,services/audit,services/statistics,services/auth,services/trading-core,services/strategies,services/market-data`,
4006 тестов, дефектов 0, на финальном состоянии дерева кода;
`services/common/model/persistence` тестов не имеет и собран зависимыми.
Заход 220 — модули `services/common/platform`, `services/bff`,
`services/strategies` (802) и классы `PerimeterRefusalPathTest`,
`SurfaceErrorContractTest`; заход 219 — модуль `services/bff` (245) и класс
`TenantContextPathTest`; заход 218 — модуль `services/bff` (238); заход 217 —
модуль `services/trading-core` (1720); заход 216 — модули
`services/market-data`, `services/trading-core` (2032) и класс
`PerimeterIntegrityPathTest`; заход 215 — модули
`services/common/test-support`, `services/market-data`,
`services/strategies`, `services/trading-core` (2572); заход 214 — модуль
`services/market-data` (308); заход 212 — модули `services/audit`,
`services/bff`, `services/common/test-support`, `services/statistics` (1129)
и классы `SideOutagePathTest`, `StreamRecordPathTest`; заход 211 — модули
`services/common/model/domain` и `services/strategies` (1023); все —
дефектов 0. Прочие модули — с полного реактора захода 209 (5244 теста,
дефектов 0). Потребители общего артефакта `platform` после правки точки
входа захода 220: `audit`, `statistics`, `auth`, `market-data`,
`trading-core` прогнаны заходом 221; `connector-okx` не прогонялся —
изменение мерит валидация.
Сквозной набор `tests` после правки схем четырёх сервисов не прогонялся —
изменение мерит валидация. Гейт инструментов корпуса — прогнан на финальном
состоянии, исход — отчёт сессии.

## Среда

Дом фактов и ловушек среды — `.claude/skills/environment-commands.md`;
стенда — `.claude/skills/local-stand.md`. Изменений среды нет.
