package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция значения индикатора (indicator_type = EFFICIENCY_RATIO). */
@Getter
@Setter
@Entity
@DiscriminatorValue("EFFICIENCY_RATIO")
public class EfficiencyRatioValueEntity extends IndicatorValueEntity {

    @Column(name = "efficiency_ratio", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal efficiencyRatio;
}
