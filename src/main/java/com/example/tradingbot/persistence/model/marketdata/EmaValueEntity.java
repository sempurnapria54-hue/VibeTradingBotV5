package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция EmaValue (indicator_type = EMA). */
@Getter
@Setter
@Entity
@DiscriminatorValue("EMA")
public class EmaValueEntity extends IndicatorValueEntity {

    @Column(name = "ema", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal ema;
}
