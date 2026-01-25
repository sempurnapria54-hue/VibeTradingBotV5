package com.example.tradingbot.client.okx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class OkxCandle {

    @JsonProperty(index = 0)
    private String timestamp;

    @JsonProperty(index = 1)
    private String open;

    @JsonProperty(index = 2)
    private String high;

    @JsonProperty(index = 3)
    private String low;

    @JsonProperty(index = 4)
    private String close;

    @JsonProperty(index = 5)
    private String volume;

    @JsonProperty(index = 6)
    private String volumeCurrency;

    @JsonProperty(index = 7)
    private String volumeCurrencyQuote;

    @JsonProperty(index = 8)
    private String confirmed;
}
