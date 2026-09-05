package com.example.marketdata.persistence.model;

import com.example.marketdata.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция значения индикатора (indicator_type = RSI). */
@Getter
@Setter
@Entity
@DiscriminatorValue("RSI")
public class RsiValueEntity extends IndicatorValueEntity {

    @Column(name = "rsi", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal rsi;
}
