package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxOrderDetailsRequest {

    private String instId;
    private String ordId;
    private String clOrdId;
}
