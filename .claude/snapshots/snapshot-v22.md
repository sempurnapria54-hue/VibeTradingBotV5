# Snapshot v22

**Дата:** 2026-05-31.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после завершения **шага 1
Фазы 1**: код написан, первый реальный `CODE`-ревью прошёл, доки
синхронизированы под код, шаг переведён в `DONE`).

## Состояние

Фаза 1 — `IN_PROGRESS`; **шаг 1 (поток рыночных данных) — `DONE`**;
шаги 2-11 — `HOLD`. Шаг 1 пройден полностью по docs-first процессу:
`DOCS_CHECK`/`GAPS_CLOSE` (×4) → `CODE` (написание + ревью + аппрув) →
`SYNC_DOCS_FROM_CODE` → `DONE`. Следующее действие — решение
пользователя по переходу к **шагу 2 (Стратегия)**.

## Что изменилось относительно v21

### `CODE` шага 1 написан и апрувнут

Свежий `src/` по концепции (Java 25 / SB4): domain (`Exchange`,
`Instrument`, `CandleGroup`, `Candle`, `TimeFrame`, `*ExternalSnapshot`,
`Auditable`), integration (`IntegrationService`/`OkxIntegrationService`,
`OkxRestClient`, OKX DTO), persistence (entity/репо/data-services/Flyway
`V1`), mapping (MapStruct), domain-сервисы онбординга, `CandleLoader`,
`CandleJob` (`domain.jobs`) + `CandleJobFacade`/`JobController`, API,
config. **Сборка не верифицирована локально** (нет JDK 25 / SB4 / mvn)
— за пользователем в IDEA; аппрув дан пользователем.

### Первый реальный `CODE`-ревью → конвенции зафиксированы

Прошёл первый реальный адверсариальный ревью кода (фокус
`conventions`). По нему:

- **`.claude/rules/codestyle.md`** существенно расширен: поля private +
  Lombok; обёртки в типах контрактной поверхности (на call-site —
  автобоксинг); enum только в домене (иначе String); identity наружу —
  `internalId` (не `id` из БД); слой **Integration** (не Client); джобы
  в `domain.jobs` + внерасписанный async-триггер через фасад; Auditable
  по слоям; имена методов мапперов по слоям без избыточных `@Mapping`;
  `util.Constants`; статик-импорты предикатов; `Collectors.toList`; без
  `New` в именах; `getRequiredBy*` в DataService; без внутренних классов
  в сервисах; rich-модели + низкая вложенность; проекции вместо
  вытягивания сущности ради поля; без неиспользуемых методов.
- **`tech-radar.md`** — добавлен Spring `@Async`/`@EnableAsync`.
- **`.claude/skills/conventions-review.md`** — наполнен по этому ревью
  (процедура + чеклист признаков), снят статус «стаб»; в
  `.claude/agents/reviewer.md` фокус «конвенции» отмечен наполненным.

### `SYNC_DOCS_FROM_CODE` шага 1 выполнен (docs←code)

- **Глобальный ренейм** `Client → Integration` по всем `docs/` (файл
  `components/ClientService.md` → `IntegrationService.md` + идентификаторы);
  `domainToOkxClient → domainToOkx`.
- Контент: `CandleGroup.md` (+`internalId`/колонка, enum→String в
  persistence), `Instrument.md` (identity наружу, enum→String),
  `TimeFrame.md` (одностороннее `domainToOkx`), `Auditable.md` (по
  слоям), `api/README.md` (API введён + конвенции слоя), `CandleJob.md`
  / `candle-loading.md` (`domain.jobs` + async-триггер + провизорная
  координация готовности). Отложенные (`MarketPriceData`/ticker/…)
  оставлены как форвард-концепт.
- Стале-токенов кода в доках не осталось (проверено).

### Прогресс шага 1 → history

8 прогресс-файлов шага 1 (`CODE`, `SYNC_DOCS_FROM_CODE`, 4×`DOCS_CHECK`,
3×`GAPS_CLOSE`) заархивированы в
`.claude/work/history/2026-05-31-phase-1-step-1-market-data-flow/` +
короткое summary рядом. `progress/` пуст.

## Активные задачи

Активных задач нет — шаг 1 закрыт (`DONE`). Следующее — старт **шага 2
(Стратегия)** по тому же процессу (решение пользователя).

## Текущий фронтир / следующее действие

- **Переход к шагу 2 (Стратегия).** По процессу
  `.claude/processes/roadmap-step-execution.md`: `TOOLING` →
  `DOCS_CHECK_N`/`GAPS_CLOSE_N` → `CODE` → `SYNC_DOCS_FROM_CODE` →
  `DONE`. Концепция шага 2 в `docs/` уже частично есть (Strategy,
  индикаторы) — на старте проверяется на целостность.
- **Коммит.** Все правки сессии — **staged, не закоммичены** (CC не
  коммитит); коммит и сборка в IDEA — за пользователем.

## Открытые общие вопросы

`open-questions.md`: **15** открыто (без изменений в этой сессии):
DEAL-Q1/Q2/Q3, PROC-Q1, RISK-Q1, TIME-Q1 (для кода шага 1 закрыт, хвост
— шаг 2), INSTR-Q1, INSTR-Q2, ORCH-Q1 (в коде провизорный seam
`CandleJob.refreshInstrumentReadiness`), ENUM-Q1, CMD-Q1, OKX-Q1..Q4.
Ни один не блокирует старт шага 2 на уровне концепт-проверки.

## Что в работе / PK

- Шаг 1 закрыт; следующее — шаг 2 (решение пользователя).
- **Project Knowledge:** последний снапшот теперь **`snapshot-v22`**
  (заменяет v21 в префлайте). `structure.md`/`naming.md` в этой сессии
  **не менялись** — их копии в PK обновлять не нужно; обновить только
  указатель на снапшот.
- Затронуто (всё staged): `src/` (шаг 1, конвенции применены),
  `.claude/rules/codestyle.md` + `tech-radar.md`,
  `.claude/skills/conventions-review.md`, `.claude/agents/reviewer.md`,
  `docs/` (SYNC), `.claude/work/roadmap/phase-1.md` (шаг 1 `DONE`),
  history (архив прогресса шага 1).
