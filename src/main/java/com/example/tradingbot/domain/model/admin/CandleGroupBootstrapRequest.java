package com.example.tradingbot.domain.model.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandleGroupBootstrapRequest {

    private List<String> timeframes;
    private Long coverageStartTs;
}
