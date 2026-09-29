# Снапшот v499

**Дата:** 2026-09-29.

## На какой вопрос отвечает этот файл

Где мы сейчас.

## Состояние

Сменяет v498. Фаза 1 — `FOLDED`. **Фаза 2 — `IN_PROGRESS`**: шаги 1-11
`DONE`, **шаг 12 — `REVIEW`**, шаг 13 `HOLD`. Прод-рубеж — `HOLD`.

**Машина шага** — `CONCEPT → CONCEPT_REVIEW → CODE → DOCS → REVIEW → DONE`
(`.claude/processes/roadmap-step-execution.md`).

**Дельта — в рабочем дереве, не закоммичена** (поверх `50e04ba5`). Ветка
`claude-audit`.

**Режим — автономия до прод-рубежа.** Решения — Д250-Д2309
(`.claude/work/decision-digest.md`).

## Что сделано после v498

- **Узел 1e плана остатка, заход 222** — клеймы реестра `auth` и сбой звена
  bearer-токена: описание реестрового статуса счёта, клейм «резолвера актора
  нет» в доке и javadoc, исход пустого `VAULT_URI` (регистрация отказывает до
  клиента); сбой звена цепочки — и отказ добычи ключей, и отказ ленивой
  сборки декодера — отвечает `500` единым DTO у всех восьми сервисов. Сняты
  пять секций бэклога, закрыты находки `F-1`, `F-2`, `F-8`, `F-11` ящика
  `auth`. Итог —
  `.claude/work/history/2026-09-29-step-12-auth-registry-claims.md`;
  запись — хроника шага `.claude/work/progress/phase-2-step-12-chronicle.md`,
  §«Сто пятьдесят четвёртый заход (222) — клеймы реестра `auth` и сбой звена bearer-токена».

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
пересоздаются перед подъёмом. Миграции захода 221 (`auth` `V3`,
`trading-core` `V12`, `strategies` `V2`, `market-data` `V2` — таблица
`access_denials`) на стенд не накатывались. С заходом 222 `auth` без
`VAULT_URI` поднимается, но регистрацию счёта отвергает. **Периметр на
стенде:** оси `PerimeterProperties` валидируются при подъёме; манифест
периметра несёт `ServiceMonitor` и метку на службе.

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
Заход 222 (2026-09-29): модули `services/common/platform`, `services/auth`,
`services/audit`, `services/bff`, `services/market-data`,
`services/statistics`, `services/strategies`, `services/trading-core`,
`services/connector-okx` — `bash tools/reactor-test.sh --modules
services/common/platform,services/auth,services/audit,services/bff,services/market-data,services/statistics,services/strategies,services/trading-core,services/connector-okx`,
4329 тестов, дефектов 0, на финальном состоянии дерева кода. Заход 221 —
модули `services/common/model/domain`, `services/audit`,
`services/statistics`, `services/auth`, `services/trading-core`,
`services/strategies`, `services/market-data` (4006); прочие модули — с
полного реактора захода 209 (5244 теста, дефектов 0). Сквозной набор `tests`
после правки цепочки восьми сервисов и схем четырёх не прогонялся —
изменение мерит валидация. Гейт инструментов
корпуса — прогнан на финальном состоянии, исход — отчёт сессии.

## Среда

Дом фактов и ловушек среды — `.claude/skills/environment-commands.md`;
стенда — `.claude/skills/local-stand.md`. Изменений среды нет.
