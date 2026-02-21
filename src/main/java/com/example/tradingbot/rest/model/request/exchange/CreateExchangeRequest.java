package com.example.tradingbot.rest.model.request.exchange;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateExchangeRequest {

    private String name;
    private String status;
    private String baseUrl;
}
