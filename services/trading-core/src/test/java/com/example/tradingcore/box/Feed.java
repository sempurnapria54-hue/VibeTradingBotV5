package com.example.tradingcore.box;

import static java.util.Objects.isNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Тела ответов соседей по ярусу — вход ящика.
 *
 * <p><b>Формы собраны от КОНТРАКТА соседа</b>, а не сняты с живого
 * сервиса: предмет здесь — ядро, и ответ соседа ему вход, а не выход. Что
 * сосед эту форму и отдаёт, мерит ящик соседа.
 *
 * <p><b>Тела — строки, а не объекты.</b> Часть кейсов подаёт форму,
 * которой в моделях сервиса нет вовсе (неразбираемое тело, поле
 * неизвестного класса, отсутствующий ключ раскладки), и типизованная
 * сборка такой вход выразить не даёт.
 */
final class Feed {

    /**
     * Сырой тип инструмента у площадки: он же ключ, по которому ставка
     * комиссии ищется в паре с группой.
     */
    static final String INSTRUMENT_TYPE = "SWAP";

    /** Ключ комиссионной группы счёта: им ставка связывается с навесом. */
    static final String FEE_GROUP = "1";

    /** Половина спреда: ею стороны стакана разводятся с последней ценой. */
    private static final BigDecimal SPREAD_HALF = new BigDecimal("0.1");

    /** Пустая раскладка: ею ставится «операнда этого рода владелец не дал». */
    private static final String EMPTY_LAYOUT = "{}";

    /** Конец окна расчёта структуры и момент её подтверждения. */
    private static final String STRUCTURE_MOMENT = "2026-09-20T09:00:00Z";

    /** Начало окна расчёта структуры. */
    private static final String STRUCTURE_WINDOW_START = "2026-09-19T09:00:00Z";

    private Feed() {
    }

    /** Строка реестра биржевых счетов у владельца. */
    static String account(String internalId, String tenantInternalId, String contour, String status) {
        return """
                {
                  "internalId": "%s",
                  "tenantInternalId": "%s",
                  "exchangeCode": "%s",
                  "label": "box account %s",
                  "contour": "%s",
                  "status": "%s"
                }
                """.formatted(internalId, tenantInternalId, TradingCoreSubstrate.EXCHANGE_CODE,
                internalId, contour, status);
    }

    /** Строка каталога инструментов у владельца. */
    static String instrument(String internalId, String externalId) {
        return """
                {
                  "internalId": "%s",
                  "exchangeCode": "%s",
                  "externalId": "%s",
                  "externalType": "%s",
                  "status": "ACTIVE",
                  "externalSettlementCurrency": "USDT",
                  "externalBaseCurrency": "%s",
                  "externalQuoteCurrency": "USDT"
                }
                """.formatted(internalId, TradingCoreSubstrate.EXCHANGE_CODE, externalId, INSTRUMENT_TYPE,
                externalId.split("-")[0]);
    }

    /**
     * Справочные правила инструмента: навес у владельца каталога.
     *
     * <p><b>Имена полей — имена КОНТРАКТА, а не удобные сокращения.</b>
     * Навес приезжает ядру доменной моделью целиком
     * ({@code MarketDataReadClient#getInstrumentRules}), и ключ, который
     * она не несёт, разбор молча отбрасывает: навес с сокращёнными
     * именами материализовался бы БЕЗ размерных спецификаций, и
     * ближайший сайзинг отказал бы кодом
     * {@code MISSING_SIZE_SPECS} — то есть на входе, который кейс считал
     * поставленным.
     *
     * <p><b>Ключ комиссионной группы объявлен, и он несущий:</b> ставку
     * наливает в навес граница чтения по тройке «счёт, сырой тип, ключ
     * группы» ({@code InstrumentExternalRulesDataService}), и без ключа
     * ставка не резолвится ни при каком синке — сайзинг отказывает
     * {@code FEE_RATE_UNAVAILABLE}.
     *
     * @param externalId идентичность инструмента на площадке
     */
    static String instrumentRules(String externalId) {
        return """
                {
                  "externalInstrumentId": "%s",
                  "externalInstrumentType": "%s",
                  "externalTickSize": "0.1",
                  "externalLotSize": "1",
                  "externalMinSize": "1",
                  "externalMaxLimitSize": "1000000",
                  "externalMaxMarketSize": "1000000",
                  "externalContractValue": "0.01",
                  "externalContractValueCurrency": "%s",
                  "externalMaxLeverage": "50",
                  "externalFeeGroupId": "%s",
                  "instrumentType": "%s",
                  "status": "LIVE",
                  "externalState": "live"
                }
                """.formatted(externalId, INSTRUMENT_TYPE, externalId.split("-")[0], FEE_GROUP,
                INSTRUMENT_TYPE);
    }

    /**
     * Ставка комиссии комиссионного уровня счёта: приватное чтение у
     * коннектора.
     *
     * <p><b>Ставка едет ИЗДЕРЖКОЙ, а не значением источника, и знак здесь
     * несущий.</b> Площадка отдаёт комиссию отрицательным числом, а
     * коннектор снимает знак на своей границе
     * ({@code TradeFeeRateMapper#rateAsCost}) — наружу он отдаёт доменную
     * модель, у которой положительное число значит «комиссия», а
     * отрицательное — «ребейт». Стаб, отдающий форму ИСТОЧНИКА,
     * предъявлял бы ядру контракт, которого у соседа нет: пол дистанции
     * стопа ({@code RiskMath#stopDistanceFloor}) становился бы
     * отрицательным, и проверка, заведённая против стопа внутри
     * round-trip комиссии, не срабатывала бы НИКОГДА — то есть молча.
     */
    static String tradeFeeRate() {
        return """
                {
                  "externalInstrumentType": "%s",
                  "externalFeeGroupId": "%s",
                  "instrumentType": "%s",
                  "externalTakerFeeRate": "0.0005",
                  "externalMakerFeeRate": "0.0002",
                  "externalFeeLevel": "Lv1",
                  "externalModifiedAt": "2026-09-20T00:00:00Z"
                }
                """.formatted(INSTRUMENT_TYPE, FEE_GROUP, INSTRUMENT_TYPE);
    }

    /**
     * Биржевой момент у коннектора.
     *
     * <p><b>Форма — голая строка ISO, а не объект.</b> Сосед отдаёт
     * {@code OffsetDateTime} своей поверхностью, и читатель разбирает
     * ровно его ({@code ExchangeOperationsClient#getServerTime}); объект
     * с миллисекундами есть форма ИСТОЧНИКА, а не соседа, и стаб,
     * отдавший её, предъявлял бы ядру контракт, которого у коннектора
     * нет.
     *
     * @param moment момент в форме ISO-8601
     */
    static String serverTime(String moment) {
        return "\"%s\"".formatted(moment);
    }

    /**
     * Связка фич момента у владельца рыночных данных: раскладки пусты,
     * фаза — названная.
     *
     * <p><b>Пустые раскладки — не заглушка, а вход.</b> Отсутствующий
     * ключ и есть ответ «операнд недоступен»
     * (docs/rules/market-data-freshness.md), и кейс о непокрытом шаге
     * подаёт ровно её.
     *
     * @param phaseType тип фазы рынка
     */
    static String features(String phaseType) {
        return bundle("{\"type\": \"%s\"}".formatted(phaseType), "null", EMPTY_LAYOUT);
    }

    /** Та же связка БЕЗ фазы: классифицировать её владелец не смог. */
    static String featuresWithoutPhase() {
        return bundle("null", "null", EMPTY_LAYOUT);
    }

    /**
     * Та же связка С ЦЕНАМИ момента: вход всякого кейса, доходящего до
     * РАСЧЁТА параметров действия.
     *
     * <p><b>Цена отделена от связки без цены намеренно.</b> Владелец
     * данных отдаёт цены тогда и только тогда, когда их спросили, а
     * спрашивает их безусловно только чтение под расчёт
     * ({@code MarketFeatureService#readForCalculation}); связка без цены
     * остаётся входом кейсов отбора входа, где цену читает не всякая
     * стратегия.
     *
     * <p><b>Стороны стакана разведены с последней ценой</b>, потому что
     * источник размещения объявляется перечнем
     * (docs/components/models/MarketPriceData.md), и кейс, у которого
     * все три числа равны, не различил бы взятый источник от соседнего.
     *
     * @param phaseType тип фазы рынка
     * @param lastPrice последняя цена сделки
     */
    static String featuresWithPrice(String phaseType, String lastPrice) {
        return bundle("{\"type\": \"%s\"}".formatted(phaseType), priceData(lastPrice), EMPTY_LAYOUT);
    }

    /**
     * Та же связка СО СТРУКТУРОЙ момента: вход кейсов, чей защитный
     * уровень считается не процентом от цены входа, а от рыночной
     * структуры.
     *
     * <p><b>Раскладка структур отделена от связки с ценой намеренно.</b>
     * Пустая раскладка есть ответ «операнд недоступен», и связка с ценой
     * остаётся входом кейсов, чей стоп объявлен процентом; подмешав
     * структуру во всякую связку, кейс давал бы расчёту вход, которого
     * его определение не просило.
     *
     * <p><b>Уровень подаётся ОДИН — свинг-минимум</b>, потому что резолв
     * базы у длинной стороны читает его первым, а диапазонную границу —
     * запасной ({@code PriceCalculator#structureLevel}); две цены сразу
     * не дали бы кейсу сказать, какая из них взята.
     *
     * @param phaseType     тип фазы рынка
     * @param lastPrice     последняя цена сделки
     * @param swingLowPrice цена свинг-минимума, от которой считается стоп
     */
    static String featuresWithStructure(String phaseType, String lastPrice, String swingLowPrice) {
        return bundle("{\"type\": \"%s\"}".formatted(phaseType), priceData(lastPrice),
                structures(swingLowPrice));
    }

    /**
     * Раскладка структур с единственным свинг-минимумом под ключом, под
     * которым её просит определение.
     *
     * @param swingLowPrice цена свинг-минимума
     */
    private static String structures(String swingLowPrice) {
        return """
                {
                  "%s": {
                    "type": "UPTREND",
                    "windowStartAt": "%s",
                    "windowEndAt": "%s",
                    "confirmedAt": "%s",
                    "levels": [
                      {
                        "type": "SWING_LOW",
                        "price": "%s",
                        "detectedAt": "%s",
                        "confirmedAt": "%s"
                      }
                    ]
                  }
                }
                """.formatted(Definitions.STRUCTURE_KEY, STRUCTURE_WINDOW_START, STRUCTURE_MOMENT,
                STRUCTURE_MOMENT, swingLowPrice, STRUCTURE_WINDOW_START, STRUCTURE_MOMENT);
    }

    /** Цены момента: последняя плюс разведённые стороны стакана. */
    private static String priceData(String lastPrice) {
        return """
                {
                  "externalLastPrice": "%s",
                  "externalBidPrice": "%s",
                  "externalAskPrice": "%s",
                  "externalBidSize": "500",
                  "externalAskSize": "500",
                  "externalTimestamp": "2026-09-20T10:00:00Z"
                }
                """.formatted(lastPrice,
                new BigDecimal(lastPrice).subtract(SPREAD_HALF).toPlainString(),
                new BigDecimal(lastPrice).add(SPREAD_HALF).toPlainString());
    }

    private static String bundle(String marketPhase, String marketPriceData, String structures) {
        return """
                {
                  "latestIndicators": {},
                  "previousIndicators": {},
                  "structures": %s,
                  "marketPhase": %s,
                  "marketPriceData": %s
                }
                """.formatted(structures, marketPhase, marketPriceData);
    }

    /**
     * Принятая настройка плеча: у неё нет сущности площадки, поэтому ни
     * биржевого идентификатора, ни эха клиентского ack не несёт.
     */
    static String leverageAck() {
        return """
                {
                  "success": true,
                  "code": "0",
                  "message": "accepted"
                }
                """;
    }

    /**
     * Подтверждение приёма команды площадкой.
     *
     * <p><b>Принято не значит исполнено</b>
     * (docs/rules/ack-not-runtime-truth.md): ack несёт биржевой
     * идентификатор и эхо клиентского, а состояние ноги подтверждает
     * добыча.
     *
     * @param externalId биржевой идентификатор принятой сущности
     * @param internalId эхо клиентского идентификатора
     */
    static String ack(String externalId, String internalId) {
        return """
                {
                  "success": true,
                  "externalId": "%s",
                  "internalId": "%s",
                  "code": "0",
                  "message": "accepted",
                  "externalCreatedAt": "2026-09-20T10:00:01Z"
                }
                """.formatted(externalId, internalId);
    }

    /**
     * Добытая нога входа: форма доменной заявки, которой коннектор
     * отвечает на всякой ноге лестницы добычи.
     *
     * @param externalId биржевой идентификатор ноги
     * @param internalId её клиентский идентификатор
     * @param status     доменный статус, наблюдённый у источника
     */
    static String order(String externalId, String internalId, String status) {
        return """
                {
                  "internalId": "%s",
                  "externalId": "%s",
                  "status": "%s",
                  "type": "ENTRY_ATTACHED_STOP_LOSS",
                  "side": "BUY",
                  "externalStatus": "live",
                  "accumulatedFillSize": "0",
                  "externalCreatedAt": "2026-09-20T10:00:01Z",
                  "externalModifiedAt": "2026-09-20T10:00:02Z"
                }
                """.formatted(internalId, externalId, status);
    }

    /**
     * Добытая нога, НАЛИТАЯ целиком: терминал источника с наливом, равным
     * размеру.
     *
     * <p><b>Налив и статус — разные операнды, и подаются оба.</b> Класс
     * родителя встроенной защиты различает терминал по наливу
     * ({@code AttachedAlgoOrderStateResolver}): терминал без налива снимает
     * защиту как отменённую вместе с родителем, а с наливом — отправляет её
     * искать материализованную запись.
     *
     * @param externalId   биржевой идентификатор ноги
     * @param internalId   её клиентский идентификатор
     * @param size         налитый размер в контрактах — он же размер ноги
     * @param averagePrice средняя цена налива
     */
    static String filledOrder(String externalId, String internalId, String size, String averagePrice) {
        return """
                {
                  "internalId": "%s",
                  "externalId": "%s",
                  "status": "COMPLETED",
                  "type": "ENTRY_ATTACHED_STOP_LOSS",
                  "side": "BUY",
                  "externalStatus": "filled",
                  "size": "%s",
                  "accumulatedFillSize": "%s",
                  "averagePrice": "%s",
                  "fee": "-0.1",
                  "externalCreatedAt": "2026-09-20T10:00:01Z",
                  "externalModifiedAt": "2026-09-20T10:00:03Z"
                }
                """.formatted(internalId, externalId, size, size, averagePrice);
    }

    /**
     * Добытая нога, налитая ЧАСТЬЮ: налив меньше размера, а встроенная
     * защита стоит в её теле.
     *
     * <p><b>Защита в теле подаётся, и это несущее.</b> У живого родителя
     * цикла добычи материализованной записи нет, и живость защиты
     * резолвится по предъявлению в теле плюс наливу
     * ({@code AttachedAlgoOrderStateResolver}): тело без элемента оставило
     * бы защиту неподтверждённой, и покрытие частично налитого входа не
     * сошлось бы.
     *
     * @param externalId           биржевой идентификатор ноги
     * @param internalId           её клиентский идентификатор
     * @param status               доменный статус: живой частичный налив либо снятая нога
     * @param size                 размер ноги в контрактах
     * @param filled               налитый размер — меньше размера
     * @param protectionInternalId клиентский идентификатор встроенной защиты
     * @param stopTrigger          цена срабатывания её стопа
     */
    static String partiallyFilledOrder(String externalId, String internalId, String status, String size,
                                       String filled, String protectionInternalId, String stopTrigger) {
        return """
                {
                  "internalId": "%s",
                  "externalId": "%s",
                  "status": "%s",
                  "type": "ENTRY_ATTACHED_STOP_LOSS",
                  "side": "BUY",
                  "externalStatus": "%s",
                  "size": "%s",
                  "accumulatedFillSize": "%s",
                  "averagePrice": "100",
                  "fee": "-0.05",
                  "attachedAlgoOrders": [
                    {
                      "internalId": "%s",
                      "type": "ATTACHED_STOP_LOSS",
                      "size": "%s",
                      "stopLossTriggerPrice": "%s",
                      "triggerPriceType": "LAST"
                    }
                  ],
                  "externalCreatedAt": "2026-09-20T10:00:01Z",
                  "externalModifiedAt": "2026-09-20T10:00:03Z"
                }
                """.formatted(internalId, externalId, status,
                "CANCELED".equals(status) ? "canceled" : "partially_filled", size, filled,
                protectionInternalId, size, stopTrigger);
    }

    /**
     * Встроенная защита, развёрнутая источником в САМОСТОЯТЕЛЬНУЮ живую
     * условную заявку: форма, которой коннектор отвечает на перечень живых
     * материализованных защит инструмента.
     *
     * <p><b>Совпадение — по клиентскому идентификатору защиты</b>, и
     * подаёт его кейс: его назначило ядро при создании ноги, и иной
     * связи записи с нашей защитой у источника нет
     * ({@code RefreshOrderExecutor#matchProtection}).
     *
     * @param internalId  клиентский идентификатор защиты
     * @param externalId  биржевой идентификатор материализованной записи
     * @param size        размер защиты в контрактах
     * @param stopTrigger цена срабатывания стопа
     */
    static String materializedProtection(String internalId, String externalId, String size, String stopTrigger) {
        return """
                {
                  "internalId": "%s",
                  "externalId": "%s",
                  "type": "ATTACHED_STOP_LOSS",
                  "externalStatus": "live",
                  "size": "%s",
                  "stopLossTriggerPrice": "%s",
                  "triggerPriceType": "LAST"
                }
                """.formatted(internalId, externalId, size, stopTrigger);
    }

    /**
     * Добытая ОТДЕЛЬНАЯ условная заявка: форма доменной условной заявки,
     * которой коннектор отвечает на её поиск.
     *
     * <p><b>Тело несёт только наблюдённое состояние, и это несущее.</b>
     * Перенос добытого в нашу строку пропускает пустые поля
     * ({@code AlgoOrderMapper#updateFromFetched}), а один ответ стаба
     * получают все заявки клетки: род условия или биржевой идентификатор в
     * нём переписали бы их у каждой заявки на один и тот же.
     *
     * @param status доменный статус, наблюдённый у источника
     */
    static String algoOrderInStatus(String status) {
        return """
                {
                  "status": "%s",
                  "externalStatus": "%s"
                }
                """.formatted(status, "CANCELED".equals(status) ? "canceled" : "live");
    }

    /**
     * Живой эпизод позиции: чем ставится ЖИВАЯ ЭКСПОЗИЦИЯ сделки.
     *
     * <p><b>Пара «биржевой идентификатор, биржевое время создания» и есть
     * адрес эпизода</b> (docs/models/domain/core/Position.md): источник
     * переиспользует идентификатор у переоткрытой позиции, и добыча
     * сравнивает пару целиком — стаб, двигающий момент между тиками,
     * закрывал бы эпизод и открывал новый каждым проходом.
     *
     * @param externalId           биржевой идентификатор эпизода
     * @param externalInstrumentId биржевое имя инструмента
     * @param size                 размер экспозиции в контрактах
     * @param entryPrice           средняя цена входа
     * @param createdAt            биржевой момент открытия эпизода
     */
    static String livePosition(String externalId, String externalInstrumentId, String size,
                               String entryPrice, String createdAt) {
        return """
                {
                  "externalId": "%s",
                  "externalInstrumentId": "%s",
                  "status": "ACTIVE",
                  "direction": "LONG",
                  "externalSize": "%s",
                  "externalAverageEntryPrice": "%s",
                  "externalMarkPrice": "%s",
                  "externalMargin": "100",
                  "externalUnrealizedProfit": "0",
                  "externalCreatedAt": "%s",
                  "externalModifiedAt": "%s"
                }
                """.formatted(externalId, externalInstrumentId, size, entryPrice, entryPrice,
                createdAt, createdAt);
    }

    /**
     * Запись закрытия эпизода позиции: форма, которой коннектор отвечает на
     * перечень закрытых эпизодов окном.
     *
     * <p><b>Реализованный результат и есть признак добытой записи</b>
     * ({@code Position#closeRecordFetched}): эпизод, закрытый без него,
     * ждёт своей записи, и добыча, её не нашедшая, звена не завершает.
     *
     * @param externalId     биржевой идентификатор эпизода — половина его адреса
     * @param createdAt      биржевой момент открытия — вторая половина
     * @param closedAt       биржевой момент закрытия
     * @param realizedProfit реализованный результат эпизода
     */
    static String closedPosition(String externalId, String createdAt, String closedAt, String realizedProfit) {
        return """
                {
                  "externalId": "%s",
                  "externalInstrumentId": "BTC-USDT-SWAP",
                  "status": "CLOSED",
                  "direction": "LONG",
                  "externalSize": "0",
                  "externalRealizedProfit": "%s",
                  "externalRealizedProfitGross": "%s",
                  "externalResultCurrency": "USDT",
                  "externalCloseAveragePrice": "100",
                  "externalCloseType": "2",
                  "externalFee": "0",
                  "externalFundingCost": "0",
                  "externalCreatedAt": "%s",
                  "externalModifiedAt": "%s"
                }
                """.formatted(externalId, realizedProfit, realizedProfit, createdAt, closedAt);
    }

    /**
     * Движение средств счёта: форма, которой коннектор отвечает на перечень
     * движений окном.
     *
     * <p><b>Категории у движения на проводе нет</b> — её выводит ядро из
     * сырого типа и подтипа по отображению контура (ключ
     * {@code exchange-contour} конфигурации): тип вне отображения садится в
     * принимающую корзину, и это предмет клетки {@code B13.6}.
     *
     * @param billId  биржевой идентификатор движения
     * @param type    сырой тип движения у площадки
     * @param subType сырой подтип; пусто — подтипа нет
     * @param amount  сумма движения в валюте расчёта
     * @param orderId биржевой идентификатор заявки, породившей движение
     * @param moment  биржевой момент движения
     */
    static String bill(String billId, String type, String subType, String amount, String orderId, String moment) {
        return """
                {
                  "externalBillId": "%s",
                  "externalType": "%s",
                  "externalSubType": %s,
                  "amount": "%s",
                  "positionBalanceChange": "0",
                  "externalFee": "0",
                  "ccy": "USDT",
                  "externalInstrumentId": "BTC-USDT-SWAP",
                  "externalOrderId": "%s",
                  "externalCreatedAt": "%s"
                }
                """.formatted(billId, type, isNull(subType) ? "null" : "\"" + subType + "\"", amount,
                orderId, moment);
    }

    /**
     * Живая заявка в СЧЁТ-ШИРОКОМ срезе: форма, которой коннектор
     * отвечает на перечень живых заявок счёта.
     *
     * <p><b>Биржевое имя инструмента здесь несущее.</b> Срез
     * раскладывается по нему ({@code AnomalyScanReader#byInstrument}), и
     * строка без имени в раскладку не попадает вовсе — то есть кейс
     * наблюдал бы молчание детектора вместо его признака.
     *
     * <p><b>Клиентский идентификатор — дискриминатор «наша против
     * чужой»</b>, и он же ключ, по которому детектор локально
     * терминальной сущности ищет нашу строку
     * (docs/components/AnomalyJob.md §«Что ищет»). Маркер контура
     * подаётся кейсом, а не фабрикой: обе стороны признака — вход.
     *
     * @param externalId           биржевой идентификатор заявки
     * @param internalId           клиентский идентификатор
     * @param externalInstrumentId биржевое имя инструмента
     */
    static String pendingOrder(String externalId, String internalId, String externalInstrumentId) {
        return """
                {
                  "internalId": "%s",
                  "externalId": "%s",
                  "externalInstrumentId": "%s",
                  "status": "ACTIVE",
                  "type": "ENTRY",
                  "side": "BUY",
                  "externalStatus": "live",
                  "size": "1",
                  "accumulatedFillSize": "0",
                  "externalCreatedAt": "2026-09-20T10:00:01Z",
                  "externalModifiedAt": "2026-09-20T10:00:02Z"
                }
                """.formatted(internalId, externalId, externalInstrumentId);
    }

    /**
     * Ответ «сущности у площадки нет»: тело, разбираемое в пустоту.
     *
     * <p>Им ставится нога лестницы, которая искомого не нашла, — и
     * отличается она от ОТКАЗА соседа: отказ несёт класс и ведёт к
     * реакции, а пустой ответ есть штатное «не найдено».
     */
    static String absent() {
        return "null";
    }

    /** Перечень из уже собранных тел. */
    static String array(String... items) {
        return "[" + String.join(",", items) + "]";
    }

    /** Перечень из уже собранных тел. */
    static String array(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    /** Пустой перечень: им ставится «у владельца строк нет». */
    static String emptyArray() {
        return "[]";
    }

    /** Тело отказа соседа: форма его единого error-DTO. */
    static String peerFailure(String code) {
        return """
                {"code": "%s", "message": "box stub refusal", "occurredAt": "2026-09-20T00:00:00Z"}
                """.formatted(code);
    }
}
