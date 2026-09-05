package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция значения индикатора (indicator_type = STOCHASTIC). */
@Getter
@Setter
@Entity
@DiscriminatorValue("STOCHASTIC")
public class StochasticValueEntity extends IndicatorValueEntity {

    @Column(name = "stoch_k", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal k;

    @Column(name = "stoch_d", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal d;
}
