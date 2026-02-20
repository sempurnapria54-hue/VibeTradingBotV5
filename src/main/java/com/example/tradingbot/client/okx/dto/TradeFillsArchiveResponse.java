package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeFillsArchiveResponse {

    private String fileHref;
    private String state;
    private String ts;
    private String code;
    private String msg;
}
