package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция EfficiencyRatioValue (indicator_type = EFFICIENCY_RATIO). */
@Getter
@Setter
@Entity
@DiscriminatorValue("EFFICIENCY_RATIO")
public class EfficiencyRatioValueEntity extends IndicatorValueEntity {

    @Column(name = "efficiency_ratio", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal efficiencyRatio;
}
