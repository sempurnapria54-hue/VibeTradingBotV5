package com.example.tradingbot.rest.model.request.instrument;

import com.example.tradingbot.rest.model.request.candle_group.CreateCandleGroupRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateInstrumentRequest {

    private String exchangeInternalId;
    private String externalId;
    private String type;
    private String marginMode;
    private String externalMarginMode;
    private Set<CreateCandleGroupRequest> candleGroups;
}
