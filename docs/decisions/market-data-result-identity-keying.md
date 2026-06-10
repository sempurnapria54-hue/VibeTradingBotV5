# Ключевание результатов расчёта рыночных данных по настройке-владельцу

## На какой вопрос отвечает этот файл

Почему персистентные результаты расчёта рыночных данных
(`IndicatorValue`, `MarketStructure`) ключуются **настройкой-владельцем**
(одна типизированная FK на строку настройки), а не по идентичности
считаемого через реестр конфигураций, и почему шаринг результатов
(compute-once) и дедуп убраны.

## Контекст

Ревизия Н3 (`GAPS_CLOSE_3`, `docs/decisions/strategy-tree-persistence.md`)
перевела листовые настройки (`StrategyIndicatorSetting`,
`StrategyMarketStructureSetting`) в JSONB внутри контейнера — отдельной
реляционной строки/`id` у них не стало. Результату расчёта стало не на что
ссылаться по `id` настройки, и направление было принято: ключевать
результаты **по идентичности считаемого** (тип + `timeframe` +
canonical-`params`) через **реестр конфигураций** (`indicator_configs` /
`market_structure_configs`, `config_id`-FK), считать раз на инструмент и
**шарить** между всеми настройками, которые запрашивают ту же
конфигурацию. `MarketPhase` был **осознанным исключением** из шаринга
(ключ по контейнеру-настройке).

Эта схема породила два дефекта когерентности:

- **Раскол двух моделей ключевания.** Индикатор/структура — по
  `config_id` (идентичность); фаза — по контейнеру. Два правила вместо
  одного; фаза постоянно требовала оговорки «осознанное исключение».
- **Краевой случай STRUCT-Q2.** Soft-входы резолвера структуры
  (`efficiencyRatioKey` / `atrKey`) живут на настройке, а не в `params`, и
  в идентичность не входят. Две настройки с одинаковыми `timeframe +
  params`, но разными ER/ATR-входами делили один `config_id` и один ряд
  результатов — молчаливая коллизия («первый писатель выигрывает»).

Пользователь выдвинул гипотезу упрощения; проверена по коду (snapshot
v42). Костяная развилка **R1 vs D** — выбор пользователя; выбран **D**.

## Принятое решение (D — перестать шарить)

Каждый персистентный результат расчёта рыночных данных ключуется **своей
настройкой-владельцем**. Реестры конфигураций (`indicator_configs`,
`market_structure_configs`), `config_id`-FK, канонизация `params` и
дедуп расчёта **убираются**. Индикаторы и структура ключуются по
владельцу — единая модель ключевания для всех видов рыночных данных
(фаза при этом вообще перестаёт персистироться — см.
`docs/decisions/market-phase-stateless.md`).

- **`IndicatorValue`** — одна типизированная FK
  `strategyIndicatorSettingId` → `strategy_indicator_settings.id`;
  ключ `UNIQUE(instrument_id, strategy_indicator_setting_id,
  candle_timestamp)`.
- **`MarketStructure`** — одна типизированная FK
  `strategyMarketStructureSettingId` → `strategy_market_structure_settings.id`;
  ключ `UNIQUE(instrument_id, strategy_market_structure_setting_id,
  window_end_at)`.

Одна настройка → один ряд результатов на инструмент. Идентичный расчёт по
двум настройкам, запрашивающим одно и то же, считается дважды и хранится
дважды — это осознанная цена (см. §Почему).

**Что это снимает в коде.** `MarketDataConfigWriter` и канонизация
`params`, config-entity/repo/data-service для обоих реестров, дедуп тика
по `instrumentId:configId` — удаляются. Job пишет результат под
`(instrument, setting, timestamp)`; свежесть по-прежнему вычисляется на
чтение, но теперь у строки **один** владелец (см.
`docs/rules/market-data-freshness.md`).

**Предпосылка — настройки получили собственные строки.** Owner-ключ
требует, чтобы у настройки был реляционный `id` как цель FK. Это даёт
ревизия настроек (`docs/decisions/strategy-tree-persistence.md`
§Ревизия — настройки в собственные строки): листовые настройки
промоутятся из JSONB в собственные таблицы (`strategy_indicator_settings`,
`strategy_market_structure_settings`), scope — стратегия,
`UNIQUE(strategy_id, key)`. FK результата — **одна** типизированная ссылка
на таблицу настройки, без полиморфной адресации владельца (см.
`docs/rules/persistence-representation.md` §Запрет полиморфных ключей).

## Почему

- **Когерентность.** Owner-ключевание растворяет раскол двух моделей:
  индикатор, структура (и логика фазы) ключуются/резолвятся **по
  владельцу**, одинаково. `MarketPhase` перестаёт быть «осознанным
  исключением» — исключения больше нет, потому что есть одна модель.
  Это убирает корень повторяющейся путаницы про «разное ключевание».
- **YAGNI на compute-once шаринге.** Выгода шаринга в фазе 1 ≈ ноль (одна
  стратегия на инструмент, перекрытия конфигураций редки). В фазе 3
  (портфель / мульти-стратегия на общем инструменте) шаринг окупался бы —
  но его дешевле вернуть **кэш-слоем поверх owner-ключа при доказанной
  потребности**, чем держать реестр + канонизацию + дедуп всё время. Не
  строим инфраструктуру шаринга превентивно.
- **Закрывает STRUCT-Q2 по построению.** Разделяемого ряда больше нет:
  каждая настройка структуры (со своими `efficiencyRatioKey` / `atrKey`)
  пишет в **свою** строку под своим `*_setting_id`. Коллизия общего
  результата при разных ER/ATR-входах невозможна — нет общего результата.

## Отвергнутые альтернативы

- **R1 — ER/ATR в идентичность структуры, шаринг сохраняется.** Resolved
  `config_id(ER)` / `config_id(ATR)` входят в `config_id` структуры;
  идентичность полна по построению, шаринг сохраняется. Блет-радиус узкий
  (только структура). Отвергнут: сохраняет раскол двух моделей ключевания
  (фаза по контейнеру vs индикатор/структура по `config_id`) и усложняет
  стационарную идентичность (read тянет sibling-`config_id`); частичный
  откат свободы fork-A (вход перестаёт быть свободной soft-ссылкой).
  Чинит STRUCT-Q2, но не лечит корневую некогерентность.
- **Прежняя схема — шаринг по идентичности через реестр конфигураций**
  (`config_id` + канонизация `params` + дедуп; `MarketPhase` —
  исключение). Это направление, принятое на `GAPS_CLOSE_3`; **реверснуто**
  здесь. Минусы, из-за которых отвергнуто: два правила ключевания,
  фаза-исключение, краевой случай STRUCT-Q2; выгода шаринга в фазе 1
  отсутствует.
- **Inline-идентичность / hash-колонка** (рассматривались внутри прежней
  схемы) — отпадают вместе с самой идеей идентичностного ключевания.

## Граница владения конфигурацией

Решение согласуется с `docs/rules/trading-configuration-ownership.md`:
универсальная **формула** расчёта — у системы, её **параметры** — у
стратегии (живут в настройке). Owner-ключевание просто хранит результат
под той настройкой, чьи параметры его породили; шаринг идентичных
конфигураций между стратегиями — отдельная оптимизация, отложенная до
явной потребности (кэш, фаза 3).

## Следствия

- `docs/models/domain/other/IndicatorValue.md` — `configId` →
  `strategyIndicatorSettingId` (FK); `UNIQUE(instrument_id,
  strategy_indicator_setting_id, candle_timestamp)`; реестр/шаринг убраны.
- `docs/models/domain/other/MarketStructure.md` — `configId` →
  `strategyMarketStructureSettingId` (FK); `UNIQUE(instrument_id,
  strategy_market_structure_setting_id, window_end_at)`; реестр/шаринг и
  краевой случай идентичности (STRUCT-Q2) убраны.
- `docs/components/IndicatorJob.md`, `docs/components/MarketStructureJob.md`
  — ключ/checkpoint по `(instrument, setting)`; дедуп по `config_id` и
  реестр убраны; STRUCT-Q2-секция снята.
- `docs/components/IndicatorService.md`,
  `docs/components/MarketStructureService.md`,
  `docs/components/MarketDataExpirationChecker.md` — lookup и свежесть по
  настройке-владельцу (один владелец на строку, нет «общей строки»).
- `docs/processes/market-data-calculation.md` — идемпотентность по
  идентичности считаемого заменена на owner-ключ.
- `docs/models/domain/aggregate/Strategy.md` — настройки индикаторов/
  структур в собственных строках, `UNIQUE(strategy_id, key)` (детали и
  обоснование — `docs/decisions/strategy-tree-persistence.md`).
- `docs/rules/market-data-freshness.md`,
  `docs/rules/market-data-retention.md`,
  `docs/rules/trading-configuration-ownership.md`,
  `docs/decisions/efficiency-ratio-as-catalog-indicator.md` — формулировки
  про шаринг по идентичности приведены к owner-ключеванию.
- Закрывает **STRUCT-Q2** (`.claude/work/questions/open-questions.md`).
- Реализация в коде (миграция: drop 2 реестра; owner-FK на
  `indicator_values` / `market_structures` + UNIQUE; удаление
  config-entity/repo/data-service; переписка job/service) — **отдельным
  заходом**.

## Связи

- Ревизия настроек в собственные строки (даёт FK цель) —
  `docs/decisions/strategy-tree-persistence.md` (§Ревизия — настройки в
  собственные строки).
- Запрет полиморфных ключей (owner-FK типизирован, не `owner_kind+owner_id`)
  — `docs/rules/persistence-representation.md`.
- Фаза перестала персистироться (бывшее исключение шаринга) —
  `docs/decisions/market-phase-stateless.md`.
- Затрагиваемые модели результатов — `docs/models/domain/other/IndicatorValue.md`,
  `docs/models/domain/other/MarketStructure.md`.
- Свежесть на чтение (referencePoint + expiredAt) —
  `docs/rules/market-data-freshness.md`.
- Retention результатов — `docs/rules/market-data-retention.md`.
- Граница владения конфигурацией — `docs/rules/trading-configuration-ownership.md`.
- Краевой случай STRUCT-Q2 (закрыт этим решением) —
  `docs/decisions/derived-market-data-code-increments.md` §Что осталось
  открытым.
