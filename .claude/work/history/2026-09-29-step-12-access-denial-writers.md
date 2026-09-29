# Писатель строки отказа доступа у сервисов со своей базой

## На какой вопрос отвечает этот файл

Что сделано по связке секций бэклога о строке отказа доступа у сервисов со
своей базой.

## Итог

Кодовый заход 221 шага 12 фазы 2, узел 1e плана остатка (Д2302-Д2305).
Сняты три секции бэклога — §«Таблица отказов доступа у сервисов со своей
базой», §«Отказ доступа у ядра не оставляет следа ни строкой, ни записью
журнала», §«Сегодняшняя половина кейсов следа отказа доступа не выведена ни
одним домом»; закрыты находки `F-5` ящика `auth` и `F-6` ящика
`trading-core`:

- **форма строки — общий артефакт**: модель `AccessDenial` в
  `services/common/model/domain`, сущность `AccessDenialEntity` в
  `services/common/model/persistence`; копии у `audit` и `statistics` сняты;
- **писатель у `auth`, `trading-core`, `strategies`, `market-data`** —
  миграция таблицы `access_denials`, маппер, репозиторий, транзакционная
  граница и писатель порта `AccessDenialRecorder`;
- **копии писателя объявлены семействами** реестра
  `tools/peer-copy-check.py`.

Дома — `docs/models/domain/other/AccessDenial.md` §Персистентность;
`docs/architecture/services.md`, строка `common/model/persistence`. Клетки —
`B5.7` (`.claude/tests/cases/auth.md`), `B12.10`
(`.claude/tests/cases/trading-core.md`), `B8.9`
(`.claude/tests/cases/strategies.md`), `B8.11`
(`.claude/tests/cases/market-data.md`). Прогон — модули
`services/common/model/domain`, `services/audit`, `services/statistics`,
`services/auth`, `services/trading-core`, `services/strategies`,
`services/market-data`; полный реактор — при валидации шага.
