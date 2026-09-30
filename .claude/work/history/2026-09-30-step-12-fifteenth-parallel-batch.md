# Пятнадцатая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала пятнадцатая пачка параллельных секций узла 1e (заход 246) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию держателя 2026-09-30 после доковой
четырнадцатой. Пять кодовых субагентов на непересекающихся путях и один
доковый субагент документов кейсов; продовая дельта — пять модулей:
`services/trading-core`, `services/bff`, `services/strategies`,
`services/auth`, `services/connector-okx`. Запись захода — хроника шага,
§«Сто семьдесят восьмой заход (246) — пятнадцатая пачка параллельных секций».
Решения — Д2536-Д2548 `.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Неполный конверт у потребителя определений ядра идёт мимо тропы
    отравленной записи» — `StrategyDefinitionConsumer` бросает
    `PoisonStrategyFactException`, строка лога несёт тему, партицию, смещение и
    первопричину; клетка B8.5 ящика `trading-core`;
  - «Периметр подставляет момент раздачи вместо пустого момента
    происшествия» — `StreamEventConsumer` отдаёт пустой момент;
    `StreamRecordApiModel.occurredAt` описан; клетка B6.5 ящика `bff`;
  - «Срок свежести объявления рыночных данных необязателен на создании» — код
    `STRATEGY_MARKET_DATA_EXPIRATION_NOT_DECLARED`
    (`docs/rules/strategy-validation.md`, `docs/models/domain/aggregate/Strategy.md`);
  - «Шаги под статусом без отбора и пустой `EXIT` транша принимаются молча» —
    код `STRATEGY_STEP_STATUS_WITHOUT_SELECTION`, пустой пакет — только у
    `EXIT` уровня сделки; группа U37, примеры `docs/spec/strategy-reference.json`.
- **Сужены:**
  - «Пояснение отказа у шести поверхностей несёт текст платформенного
    исключения» — пять поверхностей приведены (Д2537, Д2538), клетки B2.6
    `auth` и B8.8 `connector-okx`; осталась `market-data` и контейнерная ветвь
    `handleExceptionInternal` у всех шести (Д2539);
  - «Хвосты четырнадцатой пачки 2026-09-30» — сняты javadoc `DealTransition`,
    `TrancheExitPendingHandler`, `DealContext.balanceFresh`, `StreamReplayTest`;
  - «Хвосты тринадцатой пачки 2026-09-30» — сняты строки
    `docs/components/models/DealContext.md`, операнд `actNotional` (код
    приведён к дому: нотинал акта по его плановой цене, Д2540, клетки U15.11 и
    U31.18) и проверка строки расчётной валюты у границы баланса (B5.10);
  - «Хвосты четвёртой доковой пачки 2026-09-30» — снят
    `OkxCredentialsRejectionResolver#isOwnRequestDefect` с U30.13 и U32.14;
  - «Клетки, получившие ожидание четырнадцатой пачкой 2026-09-30» — B2.6 и
    B6.5 исполнены; остались E6.2 и E4.2 сквозного набора.
- **Заведены:** «Хвосты пятнадцатой пачки 2026-09-30», «Дома, разошедшиеся с
  построенным пятнадцатой пачкой», «Клетки и пробы после пятнадцатой пачки
  2026-09-30».
- **Ведущая** — U31.18 приведена к двум членам (Д2541) вместе со строкой кейса
  и `docs/components/RiskValidator.md`; величина пачки `stepActionsCount`
  копировала форму `stepActionCountInArtifact` спеки прохода (класс C.1
  `tools/spec-scope-check.py`) — заменена формой наличия
  `stepDeclaresActions`; строка срока свежести в
  `docs/models/domain/aggregate/Strategy.md`; указатели сквозных кейсов на
  снятые секции.
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/trading-core,services/bff,services/strategies,services/auth,services/connector-okx`:
  красный U31.18 (`AccountFundsTest`) на первом прогоне, прочие четыре модуля
  зелены; после правки — `--modules services/trading-core`, зелено. Итог —
  3267 тестов, дефектов 0 (`auth` 69, `bff` 251, `strategies` 576,
  `connector-okx` 546, `trading-core` 1825). Компиляция сквозного набора
  (`mvn -o -q -am -pl tests test-compile`) — зелено; снятого символа
  `isOwnRequestDefect` у потребителей нет.
