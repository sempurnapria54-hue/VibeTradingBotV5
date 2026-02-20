package com.example.tradingbot.domain.model.trading;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CancelOrderRequest {

    private Long exchangeId;
    private Long instrumentId;
    private List<String> ids;
}
