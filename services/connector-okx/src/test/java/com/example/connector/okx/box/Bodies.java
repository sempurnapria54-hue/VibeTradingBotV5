package com.example.connector.okx.box;

/**
 * Тела запросов к поверхности коннектора: базовая сборка плюс сдвиг одной
 * оси.
 *
 * <p><b>На проводе здесь доменные модели, а не свой api-слой</b>
 * ({@code docs/architecture/contracts.md} §«Два канала»), поэтому и тела
 * собираются их полями. Базовая сборка объявлена один раз: клетки
 * «род заявки выводится из наличия цены» и «намерение только сокращать
 * уезжает» различаются ровно ОДНОЙ осью, и без общей сборки расхождение
 * двух записей сделало бы красный прогон непрочитываемым.
 *
 * <p>Собирается текстом, а не объектом: часть клеток подаёт форму, которую
 * типизованная сборка не выражает.
 */
final class Bodies {

    /** Наш идентификатор заявки: несёт маркер контура, как и в проде. */
    static final String ORDER_INTERNAL_ID = "vtb-order-1";

    /** Биржевой идентификатор заявки. */
    static final String ORDER_EXTERNAL_ID = "ord-1";

    /** Наш идентификатор условной заявки. */
    static final String ALGO_INTERNAL_ID = "vtb-algo-1";

    /** Биржевой идентификатор условной заявки. */
    static final String ALGO_EXTERNAL_ID = "algo-1";

    private Bodies() {
    }

    /** Лимитная заявка: цена задана, значит род выводится в лимитный. */
    static String limitOrder() {
        return "{\"internalId\":\"" + ORDER_INTERNAL_ID + "\",\"side\":\"BUY\","
                + "\"size\":1,\"price\":50000}";
    }

    /** Та же заявка без цены: род выводится в рыночный. */
    static String marketOrder() {
        return "{\"internalId\":\"" + ORDER_INTERNAL_ID + "\",\"side\":\"BUY\",\"size\":1}";
    }

    /** Лимитная заявка с намерением «только сокращать позицию». */
    static String reducingOnlyOrder() {
        return "{\"internalId\":\"" + ORDER_INTERNAL_ID + "\",\"side\":\"SELL\","
                + "\"size\":1,\"price\":50000,\"positionReducingOnly\":true}";
    }

    /** Лимитная заявка со встроенной защитой: одна нога стоп-лосса. */
    static String orderWithAttachedProtection() {
        return "{\"internalId\":\"" + ORDER_INTERNAL_ID + "\",\"side\":\"BUY\","
                + "\"size\":1,\"price\":50000,\"attachedAlgoOrders\":["
                + "{\"internalId\":\"vtb-att-1\",\"stopLossTriggerPrice\":49000,"
                + "\"triggerPriceType\":\"MARK\"}]}";
    }

    /** Заявка к снятию: несёт оба идентификатора. */
    static String orderToCancel() {
        return "{\"internalId\":\"" + ORDER_INTERNAL_ID + "\",\"externalId\":\""
                + ORDER_EXTERNAL_ID + "\",\"side\":\"BUY\",\"size\":1,\"price\":50000}";
    }

    /** Условная заявка названного рода к снятию. */
    static String algoOrderToCancel(String conditionType) {
        return "{\"internalId\":\"" + ALGO_INTERNAL_ID + "\",\"externalId\":\""
                + ALGO_EXTERNAL_ID + "\",\"conditionType\":\"" + conditionType + "\"}";
    }

    /** Условная заявка к постановке: род условия плюс нога стоп-лосса. */
    static String algoOrderToPlace(String conditionType) {
        return "{\"internalId\":\"" + ALGO_INTERNAL_ID + "\",\"direction\":\"SELL\","
                + "\"size\":1,\"conditionType\":\"" + conditionType + "\","
                + "\"condition\":{\"type\":\"" + conditionType + "\",\"trigger\":{"
                + "\"stopLoss\":{\"type\":\"MARK\",\"value\":49000}}}}";
    }

    /** Материализованная защита к снятию. */
    static String attachedProtectionToCancel() {
        return "{\"internalId\":\"vtb-att-1\",\"externalId\":\"algo-att-1\"}";
    }
}
