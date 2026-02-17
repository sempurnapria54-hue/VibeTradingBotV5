package com.example.tradingbot.domain.model.trading;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderCommand {

    private Long exchangeId;
    private Long instrumentId;
    private String internalId;
}
