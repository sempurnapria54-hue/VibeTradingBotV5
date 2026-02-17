package com.example.tradingbot.domain.model.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AlgoOrderCommandResult {

    private String internalId;
    private String algoId;
    private String state;
}
