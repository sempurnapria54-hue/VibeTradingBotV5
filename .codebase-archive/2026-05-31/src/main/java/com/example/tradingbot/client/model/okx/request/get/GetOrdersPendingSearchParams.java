package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetOrdersPendingSearchParams {

    /**
     * Тип инструмента.
     * Возможные значения: SPOT, MARGIN, SWAP, FUTURES, OPTION.
     * Если не указан, вернёт ордера по всем типам инструментов.
     */
    private String instrumentExternalType;

    /**
     * Идентификатор инструмента (например ETH-USDT-SWAP).
     * Если не указан, вернёт ордера по всем инструментам (с учётом других фильтров).
     */
    private String instrumentExternalId;

    /**
     * Статус ордера.
     * В доке: live, partially_filled.
     * Если не указан — вернёт оба типа “незавершённых” ордеров.
     */
    private String externalStatus;

    /**
     * Тип ордера.
     * Примеры: market, limit, post_only, fok, ioc, optimal_limit_ioc и др.
     * Если не указан — без фильтра по типу.
     */
    private String externalType;

    /**
     * Пагинация по ordId (external order id):
     * вернуть записи РАНЬШЕ (older) указанного ordId.
     */
    private String afterOrderExternalId;

    /**
     * Пагинация по ordId (external order id):
     * вернуть записи НОВЕЕ (newer) указанного ordId.
     */
    private String beforeOrderExternalId;

    /**
     * Количество записей в ответе.
     * В доке: по умолчанию 100, максимум 100.
     */
    private String limit;
}
