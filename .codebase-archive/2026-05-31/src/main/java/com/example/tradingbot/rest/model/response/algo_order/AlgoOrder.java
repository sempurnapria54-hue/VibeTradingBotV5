package com.example.tradingbot.rest.model.response.algo_order;

import com.example.tradingbot.rest.model.response.Auditable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlgoOrder extends Auditable {

    private String internalId;
    private String status;
    private String conditionType;
    private BigDecimal size;
    private String direction;
    private String externalId;
    private String externalType;
    private String externalStatus;
    private String externalDirection;
    private String externalPositionSide;
    private Condition condition;

}
