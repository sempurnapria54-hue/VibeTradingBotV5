package com.example.tradingbot.rest.model.response.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeResponse {

    private Long id;
    private String name;
    private String status;
    private String baseUrl;
}
