package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция значения индикатора (indicator_type = EMA). */
@Getter
@Setter
@Entity
@DiscriminatorValue("EMA")
public class EmaValueEntity extends IndicatorValueEntity {

    @Column(name = "ema", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal ema;
}
