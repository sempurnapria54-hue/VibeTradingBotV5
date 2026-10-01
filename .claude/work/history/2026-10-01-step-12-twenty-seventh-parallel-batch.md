# Двадцать седьмая пачка параллельных секций узла 1e шага 12 фазы 2

## На какой вопрос отвечает этот файл

Что сделала двадцать седьмая пачка параллельных секций узла 1e (заход 258) шага 12 фазы 2.

## Итог

Пачка кодовая — по чередованию после доковой двадцать шестой. Три волны:
первая — три субагента (домен и коннектор с `market-data`, ядро, `auth`);
вторая — два (ядро; сквозной набор и документы кейсов); третья — три
(предикаты на моделях, долг адресов кода ядра, долг адресов кода прочих
модулей пачки) плюс правки ведущей. Продовая дельта — пять модулей:
`services/auth`, `services/common/model/domain`, `services/connector-okx`,
`services/market-data`, `services/trading-core`; тестовая — ещё модуль
`tests`. Запись захода — хроника шага, §«Сто девяностый заход (258) —
двадцать седьмая пачка параллельных секций». Решения — Д2747-Д2759
`.claude/work/decision-digest.md`.

- **Сняты целиком** (секции `.claude/work/backlog.md`):
  - «Переоценка инварианта «ликвидация за стопом» — код A13 не построен» —
    предикат `Deal.heldStopBeforeLiquidation`, детектор в
    `DealInvariantDetectors`, константа
    `Constants.Hold.INSTRUMENT_LIQUIDATION_BEFORE_STOP`; юниты `U22`
    (`domain-model-predicates.md`), `U20` (`trading-core-safety.md`);
  - «Проверки средств преконтроля неполны: обязательства и стадии
    сопровождения» — пятый исход `ActionPlan.awaitingBalance`, отсрочка в
    `CreateOrderActionExecutor.planCreation`, перевод в `balanceFetch` у
    `TrancheActionDisposition`; клетки `U32` (`trading-core-risk.md`),
    `U22.22`, `U22.23` (`trading-core-fsm.md`);
  - «Режим счёта в снимке средств и отказ контура» — перечни `AccountMode`,
    `PositionMode`, поля `BalanceContainer`, чтение `GET
    /api/v5/account/config` у коннектора, миграция
    `V17__balance_container_account_mode.sql`, приземление в
    `RefreshBalanceExecutor`, код `ACCOUNT_MODE_OUT_OF_CONTOUR`, член
    популяции `liveRiskBlockReaction`; клетки `U31.19`-`U31.23`, `U25.13`,
    B4.15, B5.14-S, B5.15-S, `U6.27`-`U6.33`, `U17.10`, `U17.11`;
  - «Оценка ликвидации до входа — тиры площадки и проверка преконтроля» —
    запись `PositionTier`, поле `InstrumentExternalRules.positionTiers`,
    чтение `GET /api/v5/public/position-tiers`, тиры в ответе правил
    `market-data`, цепочка `entryStopBeforeLiquidation` в `RiskValidator`;
    клетки `U33`, B4.16, B6.11-S, B6.12-S, B2.12 (`market-data.md`),
    `U15.15`-`U15.18`;
  - «Смена ключей биржевого счёта у `auth` не построена» — `PUT
    /api/v1/auth/exchange-accounts/{accountInternalId}/keys`; группа B8 и
    пересобранная B7.4 (`auth.md`);
  - «Хвосты двадцать пятой пачки 2026-09-30» — javadoc
    `ExternalInvariantViolationException` у коннектора.
- **Сужены:** «Хвосты двадцать шестой пачки 2026-09-30» (сняты javadoc
  `AlgoOrder.mayBeLive` и `AnomalyReportService.journalState`); «Неразрешимые
  адреса пассажей в деревьях кода» (погашены строки пяти модулей пачки, кроме
  четырёх строк `V1` ядра — применённая миграция неизменяема).
- **Заведены:** «Хвосты двадцать седьмой пачки 2026-10-01» (тестер: кейс ящика
  `A13`, `U13.3` навеса без тиров в `test-support`); «Гигиена после двадцать
  седьмой пачки 2026-10-01» (абзац инвентаря `InstrumentOkxResponse.md`,
  javadoc `ManualHaltBoxTest`).
- **Предикаты на моделях** — `BalanceContainer.isAccountModeOutOfContour`,
  `PositionTier.covers`; клетки `U23` (`domain-model-predicates.md`).
- **Сквозной набор** — стаб конфигурации счёта и тиры в теле правил стаба
  `market-data` (`Trail`), перечень E8.2.
- **Прогон** — `bash tools/reactor-test.sh --modules
  services/auth,services/common/model/domain,services/connector-okx,services/market-data,services/trading-core`:
  первый красен на B13.4 (перечень миграций без `17`), прочие модули зелены;
  после правки — модуль ядра 1927 тестов, дефектов 0; класс
  `TrailIntegrityPathTest` модуля `tests` — 4 теста, дефектов 0; финальное
  состояние (после третьей волны) — пять модулей, 3701 тест, дефектов 0.
