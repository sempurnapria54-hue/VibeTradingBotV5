package com.example.tradingbot.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.api.model.request.CreateStrategyApiRequest;
import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.config.RiskAppetiteProperties;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Связывает риск-числа детали с их конфигурационными пределами
 * (docs/spec/strategy-reference.json, величины
 * {@code hasRequiredRiskFields}, {@code strategyRiskWithinGlobal},
 * {@code catastrophicMultiplierWithinGlobal}).
 *
 * <p>Несущее: числа стратегии и числа конфигурации живут в разных
 * носителях, и охрана создания — единственное место, где они
 * встречаются. Эталон репозитория читается артефактом, а не пересказом:
 * его числа и есть та сторона, которую конфигурация обязана допускать.
 */
class ReferenceStrategyRiskNumbersTest {

    private static final String REFERENCE_STRATEGY = "strategy-examples/trend-following-ema.json";

    @Test
    @DisplayName("Эталон репозитория объявляет все четыре риск-числа детали")
    void referenceDeclaresAllFourRiskNumbers() throws IOException {
        JsonNode detail = referenceDetail();

        for (String number : List.of("riskPerActionPercent", "cumulativeRiskPerDealMultiplier",
                "strategySimultaneousRiskPerDealPercent", "strategyCatastrophicRiskPerDealMultiplier")) {
            assertFalse(detail.path(number).isMissingNode(), "эталон не объявил " + number);
        }
    }

    @Test
    @DisplayName("Числа профиля test эталон допускают — иначе контур не создаст стратегию, под которой работает")
    void testProfileNumbersAdmitReference() throws IOException {
        JsonNode detail = referenceDetail();
        // Значения профиля test равны объявлениям эталона (см. application-test.yaml):
        // равенство — самая тесная планка, которая эталон ещё пропускает.
        RiskAppetiteProperties appetite = appetite(
                detail.get("strategySimultaneousRiskPerDealPercent").decimalValue(),
                detail.get("strategyCatastrophicRiskPerDealMultiplier").decimalValue());

        assertEquals(0, detail.get("strategySimultaneousRiskPerDealPercent").decimalValue()
                .compareTo(appetite.getGlobalSimultaneousRiskPerDealPercent()));
        assertEquals(0, detail.get("strategyCatastrophicRiskPerDealMultiplier").decimalValue()
                .compareTo(appetite.getGlobalCatastrophicRiskPerDealMultiplier()));
    }

    @Test
    @DisplayName("Объявление выше конфигурационного предела — создание отвергается адресно")
    void aboveGlobalIsRejected() {
        CreateStrategyApiRequest request = requestWith(new BigDecimal("2"), new BigDecimal("100"));

        String reason = rejectReason(request, appetite(BigDecimal.ONE, new BigDecimal("100")));

        assertTrue(reason.contains("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL"), reason);
    }

    @Test
    @DisplayName("Множитель выше конфигурационного предела — свой реджект, не общий")
    void aboveGlobalMultiplierHasItsOwnReject() {
        CreateStrategyApiRequest request = requestWith(BigDecimal.ONE, new BigDecimal("200"));

        String reason = rejectReason(request, appetite(BigDecimal.ONE, new BigDecimal("100")));

        assertTrue(reason.contains("STRATEGY_CATASTROPHIC_MULTIPLIER_ABOVE_GLOBAL"), reason);
        assertFalse(reason.contains("STRATEGY_SIMULTANEOUS_RISK_ABOVE_GLOBAL"),
                "адресует отказ тот конъюнкт, который ложен");
    }

    @Test
    @DisplayName("Конфигурационное число не задано — создание отвергается: сверять не с чем")
    void unconfiguredAppetiteRejectsCreation() {
        CreateStrategyApiRequest request = requestWith(BigDecimal.ONE, new BigDecimal("100"));

        String reason = rejectReason(request, new RiskAppetiteProperties());

        assertTrue(reason.contains("RISK_APPETITE_NOT_CONFIGURED"), reason);
    }

    @Test
    @DisplayName("Торгуемая деталь без объявленного риск-числа отвергается")
    void undeclaredRiskNumberIsRejected() {
        CreateStrategyApiRequest request = requestWith(BigDecimal.ONE, null);

        String reason = rejectReason(request, appetite(BigDecimal.ONE, new BigDecimal("100")));

        assertTrue(reason.contains("STRATEGY_RISK_NUMBER_NOT_DECLARED"), reason);
    }

    private String rejectReason(CreateStrategyApiRequest request, RiskAppetiteProperties appetite) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> new StrategyCreateRequestValidator(appetite).validateCreate(request));
        return failure.getReason();
    }

    private RiskAppetiteProperties appetite(BigDecimal simultaneousPercent, BigDecimal catastrophicMultiplier) {
        RiskAppetiteProperties properties = new RiskAppetiteProperties();
        properties.setGlobalSimultaneousRiskPerDealPercent(simultaneousPercent);
        properties.setGlobalCatastrophicRiskPerDealMultiplier(catastrophicMultiplier);
        properties.setGlobalConsecutiveLossLimit(3);
        return properties;
    }

    /** Минимальная торгуемая деталь: прочие нарушения к делу не относятся, отказ несёт их все. */
    private CreateStrategyApiRequest requestWith(BigDecimal simultaneousPercent, BigDecimal catastrophicMultiplier) {
        StrategyDetailApiModel detail = new StrategyDetailApiModel();
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND.name());
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.FOLLOW_PHASE.name());
        detail.setRiskPerActionPercent(BigDecimal.ONE);
        detail.setCumulativeRiskPerDealMultiplier(new BigDecimal("2"));
        detail.setStrategySimultaneousRiskPerDealPercent(simultaneousPercent);
        detail.setStrategyCatastrophicRiskPerDealMultiplier(catastrophicMultiplier);

        CreateStrategyApiRequest request = new CreateStrategyApiRequest();
        request.setDetails(List.of(detail));
        return request;
    }

    private JsonNode referenceDetail() throws IOException {
        return new ObjectMapper()
                .readTree(new ClassPathResource(REFERENCE_STRATEGY).getInputStream())
                .get("details").get(0);
    }
}
