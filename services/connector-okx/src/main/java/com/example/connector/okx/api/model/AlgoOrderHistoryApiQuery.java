package com.example.connector.okx.api.model;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Адресация истории условных заявок инструмента. */
@Getter
@Setter
public class AlgoOrderHistoryApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке")
    private String externalInstrumentId;

    @Schema(description = "Род условия: у площадки заявки разных родов лежат в разных перечнях")
    private AlgoOrder.ConditionType conditionType;

    @Schema(description = "Идентификатор заявки на площадке; пусто — вся история инструмента")
    private String externalId;
}
