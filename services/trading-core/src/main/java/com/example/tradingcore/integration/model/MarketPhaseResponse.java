package com.example.tradingcore.integration.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Фаза рынка от владельца рыночных данных.
 *
 * <p>Своей метки времени у фазы нет: она не персистируется и вычисляется
 * на момент запроса, а свежесть наследует от входов.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketPhaseResponse {

    /** Тип фазы; {@code UNKNOWN} — вход недоступен либо ни одна клауза не истинна. */
    private String type;
}
