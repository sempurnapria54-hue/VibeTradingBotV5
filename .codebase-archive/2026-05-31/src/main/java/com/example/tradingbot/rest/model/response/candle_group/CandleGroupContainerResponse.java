package com.example.tradingbot.rest.model.response.candle_group;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroupContainerResponse {

    private List<CandleGroup> candleGroups;
}
