package com.example.tradingbot.domain.model.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCommandResult {

    private String internalId;
    private String ordId;
    private String state;
}
