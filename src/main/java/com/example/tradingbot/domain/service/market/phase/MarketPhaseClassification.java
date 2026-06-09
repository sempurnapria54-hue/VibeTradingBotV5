package com.example.tradingbot.domain.service.market.phase;

import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Результат классификации фазы: тип и производный confirmedAt (гейт
 * использования без look-ahead). Runtime value-объект MarketPhaseClassifier.
 * См. docs/components/MarketPhaseClassifier.md.
 */
@Getter
@RequiredArgsConstructor
public class MarketPhaseClassification {

    /** Тип фазы (первая сработавшая клауза, либо UNKNOWN). */
    private final MarketPhase.Type type;

    /** Производный confirmedAt сработавшей клаузы (для UNKNOWN — время бара оценки). */
    private final OffsetDateTime confirmedAt;
}
