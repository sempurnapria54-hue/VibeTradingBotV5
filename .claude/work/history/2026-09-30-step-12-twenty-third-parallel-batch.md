# Двадцать третья пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала двадцать третья пачка параллельных секций узла 1e (заход 254) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию держателя 2026-09-30 после доковой двадцать
второй. Три кодовых субагента на непересекающихся путях плюс правки ведущей;
продовая дельта — пять модулей: `services/common/model/domain`,
`services/common/model/message`, `services/trading-core`, `services/bff`,
`services/audit`; тестовая — `services/market-data` и сквозной набор
`tests`. Запись захода — хроника шага, §«Сто восемьдесят шестой заход (254) —
двадцать третья пачка параллельных секций». Решения — Д2679-Д2689
`.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Класс события `HoldReleased` и его ручная тропа» и «Класс события
    `AlgoOrderDecided` и предшественник в решении о заявке» — одним ходом:
    значения `HOLD_RELEASED`, `ALGO_ORDER_DECIDED` в `CoreEventType`, формы
    `HoldReleasedMessage`, `AlgoOrderDecidedMessage`, компонент
    `replacesInternalId` у `OrderDecidedMessage`, методы `CoreEventWriter`,
    писатели `ManualHaltService.clear` и `CreateAlgoOrderExecutor`,
    `FORM_VERSION` ядра 2 → 3, ветви и формы потока у `bff`; доки по факту —
    `contracts.md`, `event-actor-presence.json`, `AuditRecord.md`,
    `OutboxRelayJob.md`, `CreateAlgoOrderExecutor.md`,
    `audit-not-runtime-source.md`, `StreamRecordApiModel.md`; клетки B6.13
    (метка `debt` снята), B9.9, B14.7 ящика ядра, B7.1 и B7.6 ящика `bff`,
    E6.9 сквозной тропы сворачивания;
  - «След пропуска отравленной записи у ядра — не построен» — миграция
    `V15__reception_skips.sql`, `ReceptionSkipEntity`, репозиторий и
    DataService следа, писатель `StrategyFactErrorHandler`, javadoc
    `StrategyDefinitionConsumer`, у `audit` — указатели
    `JournalEnvelopeReader`, `UnparseableContentBoxTest` на решение; клетки
    B8.5, B8.6, новые B8.10, B8.11. Ряд и правило на рост остаются в
    §«Экспорт метрик из сервисов — приёмник есть, отправителя нет»;
  - «Хвосты двадцать второй пачки 2026-09-30» — javadoc `Instrument.Status`
    (`HOLD`, `ERROR`, `CLOSED` без писателя), комментарий
    `SnapshotPassBoxTest`.
- **Сужены:** «Хвосты двадцать первой пачки» — исполнены `@JsonIgnore` на
  предикатах `Order`, `AlgoOrder` (и `AttachedAlgoOrder`) и `updatable =
  false` на колонках планового риска `OrderEntity`; «Клетки ящика
  `trading-core` за чужими находками» — снят оживитель и довод
  `HoldReleased`.
- **Сверх секций:** заголовок конверта без значения у
  `StrategyDefinitionConsumer` читается как отсутствующий (Д2689) — прежде
  NPE откладывал запись без конца.
- **Заведена:** «Хвосты двадцать третьей пачки 2026-09-30» (тестеру).
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/common/model/domain,services/common/model/message,services/trading-core,services/bff,services/audit`:
  четыре модуля зелены, у `trading-core` красна B14.7 (`AbsentOutputsBoxTest`
  ждала отсутствия `HOLD_RELEASED`); после правки клетки —
  `AbsentOutputsBoxTest`, `ManualHaltBoxTest` зелены, после правки заголовка
  — модуль `trading-core` целиком: 1872 теста, дефектов 0.
  `SnapshotPassBoxTest` у `market-data` — зелен; `tests` — `test-compile`
  зелен; `TeardownManualPathTest` — красны E6.6 (половина «запросов к
  площадке нет», запись в журнале валидации) и E6.10 (известный, секция
  тестеру), E6.9 зелена.
