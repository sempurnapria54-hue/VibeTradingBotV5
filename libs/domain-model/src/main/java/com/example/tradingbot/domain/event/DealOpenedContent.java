package com.example.tradingbot.domain.event;

/**
 * Содержимое события «сделка создана»: идентичности плюс причина
 * заведения — она и есть то, ради чего событие заведено, и вывести её
 * потребителю неоткуда.
 */
public record DealOpenedContent(String dealInternalId,
                                String exchangeAccountInternalId,
                                String instrumentInternalId,
                                String entryReason,
                                String direction) {
}
