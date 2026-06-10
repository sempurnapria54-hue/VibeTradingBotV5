# MarketPhase — stateless, вычисляется на лету, не персистится

## На какой вопрос отвечает этот файл

Почему `MarketPhase` не хранится в БД, а вычисляется по запросу из текущих
значений индикаторов/структур, и какие поля/компоненты это снимает.

## Контекст

Редизайн условной фазы
(`docs/decisions/market-phase-conditional-classification.md`) уже сделал
**классификатор** фазы stateless: `MarketPhaseClassifier` — чистый
first-match поверх `StrategyConditionEvaluator`, по свечам ничего не
считает, истории прошлых фаз не читает. Но сам **результат** фазы при этом
ещё персистировался: `MarketPhaseJob` тикал по CRON и писал `MarketPhase`
(ключ по контейнеру `StrategyMarketPhaseSetting`), со своим
`expirationDuration`, `confirmedAt` и контейнерным `timeframe` как «часами
as-of».

Ревизия когерентности market-data-настроек (трек D, 2026-06-10) показала:
раз классификатор уже чист, а вход (готовые `IndicatorValue` /
`MarketStructure`) и так персистится, отдельный персист-слой фазы — лишнее
состояние и лишние «часы». Фаза — чисто производное от своих входов.

## Принятое решение

`MarketPhase` **не персистируется**. Она вычисляется **по запросу** из
текущих (последних доступных) `IndicatorValue` / `MarketStructure` через
`MarketPhaseClassifier` и отдаётся как runtime-значение
(`docs/components/MarketPhaseService.md`).

**Снимается:**

- **Персист-слой фазы целиком** — таблица/строки `MarketPhase`,
  `UNIQUE(...)`, история, retention фазы. И **`MarketPhaseJob`** (его роль
  — писать `MarketPhase` — исчезает; фаза считается на чтение).
- **`confirmedAt`** у `MarketPhase` и из контракта классификатора. Гейт
  «без look-ahead» наследуется от входов: классификатор считает по
  готовым результатам, которые сами посчитаны только по закрытым свечам и
  несут свой `confirmedAt` (структура) / `candleTimestamp` (индикатор).
- **`StrategyMarketPhaseSetting.expirationDuration`** и
  **контейнерный `timeframe`**. Своего срока свежести и своих часов у
  фазы нет — свежесть наследуется от входов (ниже). (Промежуточные формы,
  обсуждавшиеся в snapshot v42 — `defaultExpirationDuration` на
  контейнере, `expirationDuration` на правиле, `anchorTimeframe` — **не
  вводятся**: они нужны были только под персист фазы, которого больше нет.)

**Свежесть наследуется от входов.** Если вход (индикатор/структура),
нужный сработавшей клаузе, устарел или отсутствует — операнд недоступен,
и фаза консервативно резолвится в `UNKNOWN` (тот же консервативный дефолт
first-match). Отдельной проверки свежести «самой фазы» нет — есть
проверка свежести её входов (`MarketDataExpirationChecker.checkForEntry`,
`docs/rules/market-data-freshness.md`).

### Очерёдность правил = позиция в списке (убрать `level`)

`StrategyMarketPhaseRule` теряет поле `level`. Очерёдность first-match —
**позиция клаузы в `List<StrategyMarketPhaseRule>`**, а не сортировка по
`level`. Классификатор итерирует список по позиции; api/валидатор/пример
правятся соответственно. Независимая чистка (омоним с `level` действий и
`level` правил условия), въезжает этой же ревизией.

## Почему

- **Фаза — производное.** Это функция от текущих индикаторов/структур и
  авторских `phaseRules`; собственного состояния, истории и часов ей не
  нужно. Хранить производное — дублировать то, что и так выводимо из
  входов.
- **Когерентность.** Снимаются омонимы уровень×поле
  (`timeframe`/`expirationDuration` значили разное на контейнере и листе)
  и лишний персист-слой. Вместе с owner-ключеванием
  (`docs/decisions/market-data-result-identity-keying.md`) остаётся **одна**
  модель: персистентны входы (индикатор/структура, ключ по владельцу),
  фаза — их деривация на чтение.
- **YAGNI на персисте фазы.** Потребителя истории фаз нет
  (`docs/rules/market-data-retention.md`); расчёт фазы дёшев (first-match
  поверх готовых значений). Если когда-нибудь понадобится хранить ряд фаз
  (бэктест/аналитика) — вернуть отдельным решением.

## Открытый торговый вопрос — «липкость» режима

Stateless-резолв на лету **не даёт гистерезиса/подтверждаемости фазы**
(«липкость» режима — фаза не должна перескакивать на каждом тике у границы
режимов). Сейчас анти-whipsaw — только операнд-уровневый (сглаживающие
периоды индикаторов, структурный `breakoutConfirmationBars`). Нужна ли
фазе отдельная подтверждаемость/гистерезис поверх этого — **открытый
торговый вопрос** под `trading-review` со специалистом
(`.claude/work/questions/open-questions.md` §PHASE-Q1). Приемлемость
остаточного перескока как численный риск-аппетит автора уже принята
пользователем (`docs/decisions/market-phase-conditional-classification.md`
§Анти-whipsaw), но stateless-переход вопрос обостряет — поэтому вынесен.

## Следствия

- `docs/models/domain/other/MarketPhase.md` — модель переописана как
  вычисляемое на лету значение (не persisted): убраны `id`,
  `candleTimestamp` как хранимое, `confirmedAt`, §Правила хранения /
  history / retention; помечена развилка реклассификации в RVO
  (`docs/components/models/`) как форвард-заметка.
- `MarketPhaseJob` — **удалён** (компонент-доку снят; фаза не персистится).
- `docs/components/MarketPhaseService.md` — вычисляет фазу на чтение через
  `MarketPhaseClassifier` из последних `IndicatorValue` / `MarketStructure`;
  устаревший/отсутствующий вход → `UNKNOWN`.
- `docs/components/MarketPhaseClassifier.md` — контракт возвращает `Type`
  (без `confirmedAt`); вызывается `MarketPhaseService`, не job.
- `docs/models/domain/aggregate/Strategy.md` — `StrategyMarketPhaseSetting`
  теряет `timeframe` и `expirationDuration`; `StrategyMarketPhaseRule`
  теряет `level` (порядок = позиция); §Персистентность (нет колонок
  `timeframe`/`expiration_duration` контейнера, нет `level` в клаузе
  `phase_rules`).
- `docs/decisions/market-phase-conditional-classification.md` — `level`
  ASC → позиция; `confirmedAt`-деривация снята (фаза не персистится).
- `docs/rules/market-data-freshness.md` — фаза убрана из источников
  `expirationDuration` и из `referencePoint`; свежесть фазы = свежесть
  входов.
- `docs/processes/market-data-calculation.md` — `MarketPhaseJob` убран из
  цепочки jobs; фаза — деривация на чтение, не предрассчитанный результат.
- `docs/lifecycles/Strategy.md`, `docs/processes/deal-management.md` —
  `MarketPhaseJob` убран из перечней jobs рыночных данных.
- Реализация в коде (удаление job/entity/миграции, перевод сервиса на
  on-read расчёт) — **отдельным заходом**.

## Связи

- Условная фаза (сделала классификатор stateless) —
  `docs/decisions/market-phase-conditional-classification.md`.
- Owner-ключевание результатов (единая модель, фаза — деривация входов) —
  `docs/decisions/market-data-result-identity-keying.md`.
- Свежесть рыночных данных (наследование от входов) —
  `docs/rules/market-data-freshness.md`.
- Retention (нет потребителя истории) —
  `docs/rules/market-data-retention.md`.
- Модель — `docs/models/domain/other/MarketPhase.md`; компоненты —
  `docs/components/MarketPhaseService.md`,
  `docs/components/MarketPhaseClassifier.md`.
- Открытый вопрос липкости — `.claude/work/questions/open-questions.md`
  §PHASE-Q1.
