package com.example.tradingbot.rest.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentResponse extends Auditable {

    private String internalId;
    private String exchangeInternalId;
    private String instId;
    private String instType;
    private String status;
}
