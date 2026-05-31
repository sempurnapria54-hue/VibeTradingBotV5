package com.example.tradingbot.client.model.okx.request.get;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetOrdersHistorySearchParams {

    /**
     * Тип инструмента (обязательный).
     * SPOT | MARGIN | SWAP | FUTURES | OPTION.
     * Для твоего бота обычно SWAP.
     */
    private String instrumentExternalType;

    /**
     * Семейство инструмента (опционально).
     * Актуально для FUTURES/SWAP/OPTION (пример: BTC-USD).
     */
    private String instrumentExternalFamily;

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
     * Категория/причина завершения (опционально).
     * Примеры: normal, adl, full_liquidation, partial_liquidation, delivery, twap и т.д.
     */
    private String externalCategory;

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
     * Фильтр по времени создания ордера cTime "от" (Unix ms).
     */
    private String begin;

    /**
     * Фильтр по времени создания ордера cTime "до" (Unix ms).
     */
    private String end;

    /**
     * Лимит записей: 1..100, по умолчанию 100.
     */
    private String limit;
}
