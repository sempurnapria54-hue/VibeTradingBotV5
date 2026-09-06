package com.example.tradingbot.domain.event;

/**
 * Содержимое события «сделка закрыта»: идентичности плюс исход терминала
 * — статус, причина закрытия и посчитанное число с его валютой.
 */
public record DealClosedContent(String dealInternalId,
                                String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String status,
                                String closeReason,
                                String result,
                                String resultCurrency) {
}
