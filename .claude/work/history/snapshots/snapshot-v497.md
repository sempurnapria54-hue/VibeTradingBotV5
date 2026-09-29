# Снапшот v497

**Дата:** 2026-09-29.

## На какой вопрос отвечает этот файл

Где мы сейчас.

## Состояние

Сменяет v496. Фаза 1 — `FOLDED`. **Фаза 2 — `IN_PROGRESS`**: шаги 1-11
`DONE`, **шаг 12 — `REVIEW`**, шаг 13 `HOLD`. Прод-рубеж — `HOLD`.

**Машина шага** — `CONCEPT → CONCEPT_REVIEW → CODE → DOCS → REVIEW → DONE`
(`.claude/processes/roadmap-step-execution.md`).

**Дельта — в рабочем дереве, не закоммичена** (поверх `50e04ba5`). Ветка
`claude-audit`.

**Режим — автономия до прод-рубежа.** Решения — Д250-Д2301
(`.claude/work/decision-digest.md`).

## Что сделано после v496

- **Узел 1e плана остатка, заход 220** — связка пяти секций точки входа
  отказа доступа общего артефакта `platform`: строка лога на каждом отказе у
  всякого сервиса, сбой писателя следа поглощается точкой входа,
  неудостоверённый контекст на тропе авторизации отвечает исходом отказа
  аутентификации, javadoc порта сведён с кодом; у периметра — ряд частоты
  отказов (`AccessDenialMeter`) и поимённый съём метрик с `ServiceMonitor`; у
  `bff` и `strategies` отказ по правам пробрасывается контуру. Дома —
  `docs/rules/api-access-policy.md`, `docs/models/domain/other/AccessDenial.md`.
  Итог — `.claude/work/history/2026-09-29-step-12-access-denial-entry-point.md`;
  запись — хроника шага `.claude/work/progress/phase-2-step-12-chronicle.md`,
  §«Сто пятьдесят второй заход (220) — точка входа отказа доступа: охрана, лог и ряд периметра».

## Вход следующей сессии

Незакрытого закрытия нет. Единица — **узел 1e плана остатка**: следующая
секция `сейчас` с вердиктом `R` отчёта триажа
`.claude/work/progress/phase-2-step-12-parking-triage.md`, ещё стоящая в
бэклоге, либо связка секций одного дома или компонента. Ближайшая связка
того же дома — писатель строки отказа у сервисов с базой
(`.claude/work/backlog.md` §«Таблица отказов доступа у сервисов со своей
базой» и §«Отказ доступа у ядра не оставляет следа ни строкой, ни записью
журнала»); её развилка — копии писателя по сервисам либо общий артефакт
(`.claude/rules/carrier-levels.md`). Порядок владельцев в 1e — `code-writer` и
сервисы, затем `integrator`, `solution-designer`, `knowledge-curator`;
действующий перечень — `py tools/backlog-check.py`. Полного реактора до
узла 4 (валидация) нет.

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

**Стенд:** отставание стенда от дерева (миграции ядра до `V11`) — своя
секция бэклога; базы пересоздаются перед подъёмом. **Периметр на стенде:**
оси `PerimeterProperties` валидируются при подъёме; манифест периметра
теперь несёт `ServiceMonitor` и метку на службе — наблюдатель снимает ряд
отказов доступа путём `/actuator/prometheus`, которого ингресс не ведёт.

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
Заход 220 (2026-09-29): модули `services/common/platform`, `services/bff`,
`services/strategies` — `bash tools/reactor-test.sh --modules
services/common/platform,services/bff,services/strategies`, 802 теста,
дефектов 0; класс `PerimeterRefusalPathTest` модуля `tests` —
`bash tools/reactor-test.sh --modules tests --classes PerimeterRefusalPathTest`,
1 тест, дефектов 0; после правки пути пробы — класс `SurfaceErrorContractTest`
модуля `services/bff`, 3 теста, дефектов 0; все на финальном состоянии дерева
кода. Заход 219 —
модуль `services/bff` (245, дефектов 0) и класс `TenantContextPathTest`;
заход 218 — модуль `services/bff` (238); заход 217 — модуль
`services/trading-core` (1720); заход 216 — модули `services/market-data`,
`services/trading-core` (2032) и класс `PerimeterIntegrityPathTest`; заход
215 — модули `services/common/test-support`, `services/market-data`,
`services/strategies`, `services/trading-core` (2572); заход 214 — модуль
`services/market-data` (308); заход 212 — модули `services/audit`,
`services/bff`, `services/common/test-support`, `services/statistics` (1129)
и классы `SideOutagePathTest`, `StreamRecordPathTest`; заход 211 — модули
`services/common/model/domain` и `services/strategies` (1023); все —
дефектов 0. Прочие модули — с полного реактора захода 209 (5244 теста,
дефектов 0). Потребители общего артефакта `platform` (`audit`,
`statistics`, `auth`, `connector-okx`, `market-data`, `trading-core`) после
правки точки входа не прогонялись — изменение мерит валидация; прочие классы
сквозного набора `tests` тоже. Гейт инструментов корпуса — прогнан на
финальном состоянии, исход — отчёт сессии.

## Среда

Дом фактов и ловушек среды — `.claude/skills/environment-commands.md`;
стенда — `.claude/skills/local-stand.md`. Изменений среды нет.
