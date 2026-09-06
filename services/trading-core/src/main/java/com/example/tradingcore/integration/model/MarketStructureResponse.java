package com.example.tradingcore.integration.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Готовая структура рынка от владельца рыночных данных.
 *
 * <p><b>Событие пробоя приезжает плоскими полями</b> — так его отдаёт
 * владелец; вложенную форму, которую читает условие
 * {@code RANGE_BREAKOUT_CONFIRMED}, собирает маппер. Пустой тип
 * сломанного уровня означает «подтверждённого пробоя в окне нет».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketStructureResponse {

    /** Тип структуры рынка. */
    private String type;

    /** Начало окна свечей расчёта. */
    private OffsetDateTime windowStartAt;

    /** Конец окна свечей расчёта; точка отсчёта свежести. */
    private OffsetDateTime windowEndAt;

    /** Свеча, на которой структура подтверждена. */
    private OffsetDateTime confirmedAt;

    /** Ценовые уровни структуры. */
    private List<MarketPriceLevelResponse> levels;

    /** Тип уровня, сломанного подтверждённым пробоем; пусто — пробоя нет. */
    private String breakoutBrokenLevelType;

    /** Направление подтверждённого пробоя. */
    private String breakoutDirection;

    /** Цена сломанного уровня. */
    private BigDecimal breakoutLevelPrice;

    /** Свеча подтверждения пробоя. */
    private OffsetDateTime breakoutConfirmedAt;
}
