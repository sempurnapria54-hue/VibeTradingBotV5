package com.example.strategies.domain.service;

import static java.util.Objects.isNull;

import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.strategies.exception.PeerReadException;
import com.example.strategies.integration.internal.api.TradingCoreReadClient;
import com.example.strategies.integration.internal.api.model.RiskAppetiteCoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Добывает у ядра принятые им числа риск-аппетита — операнды неравенств
 * создания и активации (docs/rules/strategy-validation.md).
 *
 * <p><b>Один компонент на обе тропы — создание и активацию.</b> Копии
 * одного разбора разошлись бы первой же правкой: у создания и активации
 * операнд один и тот же, и вердикт о нём обязан быть одинаковым.
 *
 * <p><b>Пустого числа у работающего ядра не бывает:</b> непринятый набор
 * роняет его старт (docs/rules/risk-policy.md, правило о числах
 * риск-аппетита). Поэтому ответ без любого из трёх чисел — либо пустой
 * ответ — есть нарушение контракта соседа, а не «числа не назначены», и
 * класс отказа у него тот же, что у неразбираемого тела: {@link PeerReadException}
 * (docs/rules/runtime-error-classification.md). Недоступность ядра остаётся
 * своим классом — её разводит {@code PeerCall}.
 *
 * <p><b>Проверка стоит здесь, а не в обходе деталей</b>: определение без
 * торгуемых деталей неравенств не считает вовсе, и пустота ответа прошла бы
 * мимо охраны — разрешение выдавалось бы на непрочитанном ответе.
 */
@Service
@RequiredArgsConstructor
public class TenantRiskAppetiteReader {

    private final TradingCoreReadClient tradingCoreReadClient;

    /** Принятые ядром числа; ответ без любого из трёх — отказ чтения соседа. */
    public TenantRiskAppetite read() {
        RiskAppetiteCoreResponse response = tradingCoreReadClient.getRiskAppetite();
        if (isNull(response)
                || isNull(response.globalSimultaneousRiskPerDealPercent())
                || isNull(response.globalCumulativeRiskPerDealMultiplier())
                || isNull(response.globalMaxLeverage())) {
            throw new PeerReadException(
                    "Peer trading-core answered [risk-appetite] without the accepted risk appetite numbers");
        }
        return new TenantRiskAppetite(response.globalSimultaneousRiskPerDealPercent(),
                response.globalCumulativeRiskPerDealMultiplier(),
                response.globalMaxLeverage());
    }
}
