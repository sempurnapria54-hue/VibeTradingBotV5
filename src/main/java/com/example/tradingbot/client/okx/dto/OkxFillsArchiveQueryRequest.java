package com.example.tradingbot.client.okx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OkxFillsArchiveQueryRequest {

    private String year;
    private String quarter;
}
