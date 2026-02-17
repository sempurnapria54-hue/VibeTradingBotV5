package com.example.tradingbot.domain.model.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {

    private Long id;
    private Long exchangeId;
    private String instId;
    private String instType;
    private String status;
}
