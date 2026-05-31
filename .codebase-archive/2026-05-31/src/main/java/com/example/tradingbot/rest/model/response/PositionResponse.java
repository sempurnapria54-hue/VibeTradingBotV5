package com.example.tradingbot.rest.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse extends Auditable {

    private String internalId;
    private String externalId;
    private String status;
    private String side;
    private BigDecimal size;
    private BigDecimal averagePrice;
    private BigDecimal markPrice;
    private BigDecimal liquidationPrice;
    private Integer leverage;
    private String marginMode;
    private BigDecimal unrealizedProfit;

}
