package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetAlgoOrdersHistorySearchParams {

    /**
     * Тип algo-ордера (обязательный).
     * conditional | oco | trigger | move_order_stop.
     */
    private String externalAlgoOrderType;

    /**
     * Инструмент (опционально), например ETH-USDT-SWAP.
     */
    private String instrumentExternalId;

    /**
     * Тип инструмента (опционально).
     * SPOT | MARGIN | SWAP | FUTURES | OPTION.
     */
    private String instrumentExternalType;

    /**
     * Статус algo-ордера в истории (условно обязательный).
     * effective | canceled | order_failed.
     * <p>
     * Нужно передать хотя бы один из:
     * - externalStatus
     * - algoOrderExternalId
     */
    private String externalStatus;

    /**
     * Пагинация по algoId:
     * вернуть записи РАНЬШЕ (older) указанного algoId.
     */
    private String afterAlgoOrderExternalId;

    /**
     * Пагинация по algoId:
     * вернуть записи НОВЕЕ (newer) указанного algoId.
     */
    private String beforeAlgoOrderExternalId;

    /**
     * Лимит записей: 1..100, по умолчанию 100.
     */
    private String limit;

    /**
     * Внешний ID algo-ордера (условно обязательный).
     * <p>
     * Нужно передать хотя бы один из:
     * - externalStatus
     * - algoOrderExternalId
     */
    private String algoOrderExternalId;
}