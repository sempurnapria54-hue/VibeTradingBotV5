package com.example.tradingbot.client.model.okx.request.get;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetOrdersAlgoPendingSearchParams {

    /**
     * Тип algo-ордера (обязательный):
     * conditional | oco | trigger | move_order_stop
     */
    private String algoOrderExternalType;

    /**
     * Фильтр по конкретному algoId (опционально).
     */
    private String algoOrderExternalId;

    /**
     * Тип инструмента (опционально): SPOT | MARGIN | SWAP | FUTURES | OPTION.
     */
    private String instrumentExternalType;

    /**
     * Инструмент (опционально), например ETH-USDT-SWAP.
     */
    private String instrumentExternalId;

    /**
     * Пагинация по algoId: вернуть записи РАНЬШЕ указанного algoId.
     */
    private String afterAlgoOrderExternalId;

    /**
     * Пагинация по algoId: вернуть записи НОВЕЕ указанного algoId.
     */
    private String beforeAlgoOrderExternalId;

    /**
     * Лимит записей: 1..100, по умолчанию 100.
     */
    private String limit;
}
