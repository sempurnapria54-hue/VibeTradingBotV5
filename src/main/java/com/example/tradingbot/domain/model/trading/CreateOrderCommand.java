package com.example.tradingbot.domain.model.trading;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderCommand {

    private Long exchangeId;
    private Long instrumentId;
    private String side;
    private String ordType;
    private String sz;
    private String px;
}
