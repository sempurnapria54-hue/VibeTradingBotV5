package com.example.tradingbot.rest.model.response.instrument;


import com.example.tradingbot.rest.model.response.Auditable;
import com.example.tradingbot.rest.model.response.candle_group.CandleGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Instrument extends Auditable {

    private String internalId;
    private String externalId;
    private String externalType;
    private String status;
    private String marginMode;
    private String externalMarginMode;
    private Integer leverage;
    private List<CandleGroup> candleGroups;
}
