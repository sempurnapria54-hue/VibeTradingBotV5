package com.example.tradingbot.persistence.model.marketdata;

import com.example.tradingbot.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Persistence-проекция MacdValue (indicator_type = MACD). */
@Getter
@Setter
@Entity
@DiscriminatorValue("MACD")
public class MacdValueEntity extends IndicatorValueEntity {

    @Column(name = "macd_line", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal macdLine;

    @Column(name = "signal_line", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal signalLine;

    @Column(name = "histogram", precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal histogram;
}
