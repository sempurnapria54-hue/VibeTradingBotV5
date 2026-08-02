# Дом валют инструмента — InstrumentExternalRules

## На какой вопрос отвечает этот файл

Почему расчётная, базовая и котировочная валюты инструмента персистятся
в `InstrumentExternalRules`, а не в конфиге и не запрашиваются у
источника по требованию.

## Контекст

Расчётная валюта инструмента (settle-ccy) — операнд трёх потребителей
шага 7: ветки cross-ccy на записи `DealCashFlow`
(`docs/components/RefreshBillsExecutor.md` — сравнение `ccy` движения),
писателя `Deal.plannedRiskCurrency` (`CreateOrderExecutor`) и
финализации (`resultProfitCurrency`). До этого решения base/quote/settle
приходили **транзиентно** в `InstrumentExternalSnapshot` (шаг 1) и
персистентного дома не имели; `mapping/Instrument.md` указывал домом
`InstrumentExternalRules`, а сама модель заявляла, что валют не держит —
ссылки уводили CODE-писателя в разные стороны (H12 `DOCS_CHECK_9`,
переоткрытие узла 5).

## Решение

**Расчётная, базовая и котировочная валюты персистятся в
`InstrumentExternalRules`** (JSONB-навес инструмента): источник —
`/public/instruments` (`settleCcy`/`baseCcy`/`quoteCcy`), тот же
эндпоинт и тот же синк, что у остальной спецификации навеса; свежесть
меряется тем же измерителем (`externalModifiedAt` строки-владельца).
Ссылка `mapping/Instrument.md` («дом — `InstrumentExternalRules`»)
становится верной; клейм модели «валют не держит» снят
(`docs/models/domain/other/InstrumentExternalRules.md` §«Валюты
инструмента»).

Решение пользователя (сверка `DOCS_CHECK_9`, 2026-08-02; совпало с
креном владельца).

## Альтернативы (отвергнуты)

- **Фаза-1-константа из конфига** — подставленное число выглядит фактом,
  не будучи им (та же болезнь, по которой отвергнут fallback ставки
  комиссии); смена контура/инструмента молча рассинхронизирует константу
  с биржей.
- **Запрос у источника по требованию** — операнд нужен на записи каждой
  bill-строки и при постановке входа; exchange-вызов в этих точках —
  лишняя зависимость от доступности источника при значении, которое
  меняется не чаще спецификации инструмента.

## Открытые хвосты (CCY-Q2, владелец — `solution-designer`)

- **Именование поля расчётной валюты:** `externalCurrency` против
  `externalSettlementCurrency` — второе сохраняет дискриминатор при трёх
  валютах на инструменте (крен). До закрытия имена полей в носителях —
  предварительные.
- **Область модели:** модель заявляла «валют не держит» и названа
  «правилами», тогда как валюты — атрибуты. Развилка: расширение области
  модели (с возможным переименованием) против отдельного носителя валют.

## Связи

- Модель — `docs/models/domain/other/InstrumentExternalRules.md`.
- Mapping — `docs/models/mapping/InstrumentExternalRules.md`,
  `docs/models/mapping/Instrument.md`.
- Потребители — `docs/components/RefreshBillsExecutor.md`,
  `docs/components/CreateOrderExecutor.md`,
  `docs/models/mapping/DealCashFlow.md` §«Операнд сравнения»,
  `docs/models/domain/aggregate/Deal.md` §«Плановый риск».
- Инвариант валюты комиссии — `docs/rules/trading-constraints.md`.
