package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция AtrValue (indicator_type = ATR). */
@Getter
@Setter
@Entity
@DiscriminatorValue("ATR")
public class AtrValueEntity extends IndicatorValueEntity {

    @Column(name = "atr", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal atr;
}
