package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetOrdersHistoryArchiveSearchParams {

    /**
     * Тип инструмента (обязательный).
     * SPOT | MARGIN | SWAP | FUTURES | OPTION.
     * Для твоего бота обычно SWAP.
     */
    private String instrumentExternalType;

    /**
     * Инструмент (опционально), например ETH-USDT-SWAP.
     */
    private String instrumentExternalId;

    /**
     * Тип(ы) ордера (опционально). Можно перечислять через запятую.
     * Примеры: market, limit, post_only, fok, ioc, optimal_limit_ioc.
     */
    private String externalType;

    /**
     * Статус завершённого ордера (опционально):
     * filled | canceled | mmp_canceled.
     */
    private String externalStatus;

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
     * Лимит записей: 1..100, по умолчанию 100.
     */
    private String limit;
}
