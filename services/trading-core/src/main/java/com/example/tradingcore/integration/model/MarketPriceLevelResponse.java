package com.example.tradingcore.integration.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ценовой уровень структуры рынка от владельца рыночных данных. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketPriceLevelResponse {

    /** Тип уровня. */
    private String type;

    /** Цена уровня. */
    private BigDecimal price;

    /** Свеча, на которой уровень найден. */
    private OffsetDateTime detectedAt;

    /** Свеча, на которой уровень подтверждён. */
    private OffsetDateTime confirmedAt;
}
