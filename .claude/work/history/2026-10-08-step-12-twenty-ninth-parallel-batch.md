# Двадцать девятая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала двадцать девятая пачка параллельных секций узла 1e (заход 260) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию после доковой двадцать восьмой. Одна волна:
один субагент на одну торгово-значимую секцию, плюс правки ведущей. Продовая
дельта — три модуля: `services/common/model/domain`, `services/connector-okx`,
`services/trading-core`. Запись захода — хроника шага, §«Сто девяносто второй
заход (260) — двадцать девятая пачка параллельных секций». Решения —
Д2783-Д2792 `.claude/work/decision-digest.md`.

- **Снята целиком** секция «Сверки эха отдельной условной заявки не
  построены, а доки о `reduceOnly` расходятся» (кодовая половина; доки
  сведены двадцать восьмой пачкой):
  - коннектор — поля `side`, `reduceOnly` у `AlgoOrderOkxResponse` и
    `AlgoOrderExternalSnapshot`; эхо в домен на прочитанной копии
    (`okxAlgoSideToDomain`, `OkxParse.flag`, база через
    `okxTriggerPriceType`);
  - домен — `TriggerPrice.externalType` типом `AlgoOrder.TriggerPriceType`;
    предикаты `AlgoOrder.matchesEcho` (три оси) и
    `AttachedAlgoOrder.matchesEcho` (база); юнит `EchoMatchTest`;
  - ядро — сверка первым ходом в `RefreshAlgoOrderExecutor`, сверка базы
    встроенной защиты на трёх тропах `RefreshOrderExecutor`;
    `AlgoOrderMapper.updateFromFetched` не переносит сторону и признак,
    уровень трейлинга — только непустым и только на ветку трейлинга;
    миграция `V18__attached_algo_order_trigger_price_type_not_null.sql`;
  - javadoc коннектора (`ExternalInvariantViolationException`,
    `PositionOkxResponse`, `ClosePositionOkxRequest`) и ядра
    (`ExternalInvariantViolationException`); строка `allowed` и её
    комментарий у записи `autoCxl` в `tools/retired-check.py`;
  - доки, отставшие от кода: таблица персистентности встроенной защиты
    (`docs/models/domain/core/Order.md`), абзац сверки базы
    (`docs/components/RefreshOrderExecutor.md`).
- **Заведена** секция тестеру «Хвосты двадцать девятой пачки 2026-10-08»:
  клетки `U10.14`, `U9.8` `okx-mapping.md` и клетки сверки эха.
- **Среда, найденное прогоном:** рабочий `~/.m2/settings.xml` держателя
  ломает офлайновую сборку — проект зовёт Maven своим
  `tools/maven-settings.xml`; сон мака посреди прогона сдвигает часы VM
  Docker — `tools/reactor-test.sh` держит `caffeinate`. Факты —
  `.claude/skills/environment-commands.md`, строки «Настройки Maven» и «Сон
  посреди прогона».
- **Прогон** — модули трёх: первый не стартовал (настройки Maven), второй
  красен компиляцией теста (двусмысленная перегрузка `snapshotToDomain(null)`),
  третий — `U32.15` (`IllegalArgumentException` в слое маппинга), четвёртый —
  19 красных ящика ядра после сна хоста и `B13.4` (перечень миграций без
  `18`); финальный — 3269 тестов, дефектов 0. Гарантия — зелёное для
  тронутого; полный реактор — при валидации шага.
