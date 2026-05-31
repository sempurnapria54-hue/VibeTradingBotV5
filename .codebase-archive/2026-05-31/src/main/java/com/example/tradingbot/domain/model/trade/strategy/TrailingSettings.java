package com.example.tradingbot.domain.model.trade.strategy;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Настройки trailing-защиты.
 */
@Getter
@Setter
public class TrailingSettings {

    /**
     * После какого профита можно включить trailing.
     * Если null — сразу.
     */
    private BigDecimal activationProfitPercents;

    /**
     * Расстояние trailing от экстремума.
     */
    private BigDecimal callbackPercents;

    /**
     * Дополнительный буфер после активации.
     */
    private BigDecimal activationBufferPercents;
}
