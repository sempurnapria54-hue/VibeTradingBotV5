# services/

## На какой вопрос отвечает этот файл

Что кладётся в этот каталог.

Единицы развёртывания целевой конструкции — по одному каталогу на
единицу из инвентаря `docs/architecture/services.md`: `auth`,
`market-data`, `trading-core`, `strategies`, `connector-okx`,
`connector-bybit`, `bff`, `audit-statistics`.

Каталог наполняется шагами 3-10 фазы 2 (`.claude/work/roadmap/phase-2.md`),
каждый — портом из `donor/`. **Состав каталога равен инвентарю
`services.md`**: единицы, которой в инвентаре нет, здесь не заводится —
на этом равенстве держится проверяемость раскладки
(`.claude/decisions/monorepo-restructuring-in-place.md`).

Знание сервиса живёт не здесь, а в корневом `docs/`; какие доки чьи —
`.claude/rules/knowledge-ownership-by-service.md`.
