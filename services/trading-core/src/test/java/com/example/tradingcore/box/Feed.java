package com.example.tradingcore.box;

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
        return bundle("{\"type\": \"%s\"}".formatted(phaseType), "null");
    }

    /** Та же связка БЕЗ фазы: классифицировать её владелец не смог. */
    static String featuresWithoutPhase() {
        return bundle("null", "null");
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
        return bundle("{\"type\": \"%s\"}".formatted(phaseType), priceData(lastPrice));
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

    private static String bundle(String marketPhase, String marketPriceData) {
        return """
                {
                  "latestIndicators": {},
                  "previousIndicators": {},
                  "structures": {},
                  "marketPhase": %s,
                  "marketPriceData": %s
                }
                """.formatted(marketPhase, marketPriceData);
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
