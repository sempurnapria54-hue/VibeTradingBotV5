package com.example.tradingbot.rest.model.response.instrument;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentResponse {

    private Long id;
    private Long exchangeId;
    private String instId;
    private String instType;
    private String status;
}
