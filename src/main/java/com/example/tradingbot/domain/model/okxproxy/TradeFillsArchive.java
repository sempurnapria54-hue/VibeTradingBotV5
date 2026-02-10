package com.example.tradingbot.domain.model.okxproxy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillsArchive {

    private String fileHref;
    private String state;
    private String timestamp;
    private String code;
    private String message;
}
