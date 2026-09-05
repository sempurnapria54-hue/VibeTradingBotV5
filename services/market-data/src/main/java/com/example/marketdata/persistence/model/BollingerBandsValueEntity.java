package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция значения индикатора (indicator_type = BOLLINGER_BANDS). */
@Getter
@Setter
@Entity
@DiscriminatorValue("BOLLINGER_BANDS")
public class BollingerBandsValueEntity extends IndicatorValueEntity {

    @Column(name = "upper_band", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal upperBand;

    @Column(name = "middle_band", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal middleBand;

    @Column(name = "lower_band", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal lowerBand;

    @Column(name = "bandwidth", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal bandwidth;

    @Column(name = "percent_b", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal percentB;
}
