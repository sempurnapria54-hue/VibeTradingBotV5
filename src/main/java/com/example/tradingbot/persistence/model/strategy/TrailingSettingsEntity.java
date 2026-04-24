package com.example.tradingbot.persistence.model.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrailingSettingsEntity {

    /**
     * Порог профита, после которого можно активировать trailing.
     */
    private BigDecimal activationProfitPercents;

    /**
     * Callback trailing в процентах от экстремума.
     */
    private BigDecimal callbackPercents;

    /**
     * Дополнительный буфер после activationProfitPercents.
     */
    private BigDecimal activationBufferPercents;
}
