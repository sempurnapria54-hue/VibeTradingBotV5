package com.example.tradingbot.domain.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillsArchive {

    private String requestLinkAlreadyExists;
    private String exchangeTimestamp;
    private String state;
    private String fileHref;
}
