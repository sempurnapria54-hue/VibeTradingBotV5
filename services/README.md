# services/

## На какой вопрос отвечает этот файл

Что кладётся в этот каталог.

Единицы развёртывания целевой конструкции — по одному каталогу на
единицу из инвентаря `docs/architecture/services.md`: `auth`,
`market-data`, `trading-core`, `strategies`, `connector-okx`,
`connector-bybit`, `bff`, `audit-statistics`.

Каталог наполняется шагами 3-10 фазы 2 (`.claude/work/roadmap/phase-2.md`),
каждый — портом из `donor/`. **В каталоге нет единицы, которой нет в
инвентаре `services.md`** — на этом вложении держится проверяемость
раскладки, и мерит его `python3 tools/deploy-layout-check.py` (ось 1)
(`.claude/decisions/monorepo-restructuring-in-place.md`). **Обратное вложение
замером не покрыто:** пока построена не последняя единица инвентаря,
инвентарь шире каталога; равенством клейм станет по построении последней.

Знание сервиса живёт не здесь, а в корневом `docs/`; какие доки чьи —
`.claude/rules/knowledge-ownership-by-service.md`.
