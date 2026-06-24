package com.example.tradingbot.domain.safety;

import lombok.Value;

/**
 * Сигнал «поднять реактивный safety-холд scope»: его несёт
 * {@link com.example.tradingbot.domain.deal.DealTransition} рядом с уводом
 * своей сделки в ERROR. Сам сигнал не закрывает сделку — он адресует
 * инструмент-/биржа-широкую реакцию, которую координирует
 * {@link SafetyHoldCoordinator} над сделкой в проходе оркестратора. Severity
 * не носит: реактивный контур по определению CRITICAL
 * (docs/rules/error-handling-policy.md, дизайн холдов шага 6). RVO.
 */
@Value
public class HoldSignal {

    /** Уровень холда: инструмент (L3) или биржа (L4). */
    HoldScope scope;

    /** Машинно-читаемый код причины (попадает в AnomalyReport.code). */
    String code;

    /** Холд инструмента (L3) с кодом причины. */
    public static HoldSignal instrument(String code) {
        return new HoldSignal(HoldScope.INSTRUMENT, code);
    }

    /** Холд биржи (L4) с кодом причины. */
    public static HoldSignal exchange(String code) {
        return new HoldSignal(HoldScope.EXCHANGE, code);
    }
}
