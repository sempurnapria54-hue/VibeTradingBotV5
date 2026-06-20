# Контур тестов API источника (source-api) — закрыт

**Дата завершения:** 2026-06-20.

## На какой вопрос отвечает этот файл

Что мы сделали в задаче «контур тестов API источника OKX» и чем она
закрыта.

## Суть

Построен и доведён до стабильного состояния контур тестов API источника
OKX: проверка **контракта биржи** (не нашего кода) по всему in-perimeter
периметру манифеста через единственный generic-эндпоинт
`POST /api/proxy/okx/raw` (`A2 raw-passthrough` + `/raw`-only,
`source-api-target-rebase.md` §D). Ассерты — по сырым полям JSON
(`OkxApiResponse<JsonNode>`).

## Что сделано

- **DESIGN** — план (`.claude/tests/source-api/okx/plan.md`) + Postman-коллекция,
  два адверсариальных ревью, аппрув. Развилки A/B/C ре-базы закрыты
  (A2 / B1 / C3 — см. архив `questions-source-api-rebase.md`).
- **CODE** — production-side (`OkxRestClient.dispatch` + generic
  `OkxProxyController` `/raw` + `OkxRawApiRequest`); **60 эндпоинт-классов
  код-тестов** + offline-probe I-cred; инфра (throttle/poll/sweep-halt/
  детерминированный порядок) закалена.
- **RUN** (demo/non-prod, `full-05.log`): **200 тестов, 198 pass / 2 fail**,
  затем оба фейла разобраны и закрыты. Манифест покрытия → `🟢 в коде` по
  всем in-perimeter; колонка «Факт + наблюдения (RUN)» плана заполнена
  (312 строк).
- **Хвосты (все закрыты):**
  - **A2** (no-op `defaultStatusHandler` на обоих RestClient'ах) — оценён
    на продуктовой поверхности: корректен по контракту OKX и
    downstream-консистентен, правок не потребовал.
  - **AG5** (`bills-history-archive`) — rate-limit/квота 12/сутки принята
    как валидный исход (AG5.1), skip-on-rate-limit для негативов
    quarter (AG5.3/4); success-контракт помечен **prod-only** (манифест,
    план, `account-bills.md`).
  - **§I3** — `OkxSigningInterceptor` fail-fast на пустых кредах
    («OKX credentials not configured»), тест переведён на новое поведение;
    `backlog` §I3 закрыт.
  - **Мис-филл плана** — I-cred унаследовал чужой OBSERVE при bulk-fill;
    исправлен, аудит подтвердил единственность; AG8.1 выровнен под
    step-метки кода (+`observe` на snapshot/verify).
  - **TG4.1** — изначальный фейл (poll-таймаут 25s мал для amend-reflection
    на demo) — тест-тюнинг: выровнен на 60s (как одиночный amend TG3.1),
    TG4 3/3 green. Не дефект контракта.
- **C3 apidoc-sync** — рантайм-находка **И-2**: `cancel-advance-algos`
  **жив на demo** вопреки офдоку (delisted 2025-04-24) — зафиксирована в
  `algo-order.md` (провенанс `рантайм`) + пометка в шапке «Внешний источник
  правды» (`external-source-sync`).
- **Скиллы контура** выровнены на `/raw`-only (`test-design`,
  `test-collection`, `test-review`; `test-code` — ранее): снят субсет-модель
  («манифест ∩ клиент», `client-coverage-gap`, «вариант-gap»), ассерты — на
  сырых полях JSON.

## Итог

Контур стабилен, остаточных фейлов нет. prod-проверка success deep-архива
AG5 — ад-хок пользователем вне контура. Детальные артефакты — в одноимённой
подпапке `2026-06-20-source-api-contour/` (pilot-run-log, rebase-концепция,
testing-pilot, развилки A/B/C).

## Связи

- Снапшот закрытия — `.claude/snapshots/snapshot-v55.md`.
- Решение ре-базы — `.claude/decisions/source-api-target-rebase.md`.
- Процесс — `.claude/processes/source-api-testing.md`; план —
  `.claude/tests/source-api/okx/plan.md`; манифест —
  `docs/integrations/okx/coverage-manifest.md`.
