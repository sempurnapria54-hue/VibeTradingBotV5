package com.example.tradingbot.domain.command.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.tradingbot.config.RiskAppetiteProperties;
import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.calc.CalculatedPrice;
import com.example.tradingbot.domain.command.calc.CalculatedSize;
import com.example.tradingbot.domain.command.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.command.calc.ResolvedStopLossPrice;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает «пустое место — отказ» с кодом, а не с доком
 * (docs/rules/risk-policy.md §«Числа назначает держатель; пустое место —
 * отказ», docs/rules/loss-streak-halt.md §«Пустое место — отказ»).
 *
 * <p>Несущее: незаданное число конфигурации ОТВЕРГАЕТ действие, а не
 * пропускает его, и отказ адресный — два разных пустых операнда обязаны
 * различаться в данных (П3). Ставка комиссии — тот же класс: подставленное
 * число выглядит фактом, не будучи им, и ошибается в разрешающую сторону.
 */
class RiskAppetiteGateTest {

    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);

    @Test
    @DisplayName("Порог серии убытков не задан — действие отвергается своим кодом")
    void missingLossLimitRejectsAction() {
        RiskAppetiteProperties appetite = configured();
        appetite.setGlobalConsecutiveLossLimit(null);

        RiskValidationResult result = validate(appetite, "0.0005");

        assertTrue(hasBlocking(result, RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED));
    }

    @Test
    @DisplayName("Максимальный риск на сделку не задан — действие отвергается своим кодом")
    void missingRiskAppetiteRejectsAction() {
        RiskAppetiteProperties appetite = configured();
        appetite.setGlobalSimultaneousRiskPerDealPercent(null);

        RiskValidationResult result = validate(appetite, "0.0005");

        assertTrue(hasBlocking(result, RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED));
        assertFalse(hasBlocking(result, RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED),
                "два пустых операнда различимы в данных: отказ адресует тот, что пуст");
    }

    @Test
    @DisplayName("Ставка комиссии не резолвится — действие с уровнем отвергается")
    void missingFeeRateRejectsLevelBearingAction() {
        RiskValidationResult result = validate(configured(), null);

        assertTrue(hasBlocking(result, RiskCheckCode.FEE_RATE_UNAVAILABLE));
    }

    @Test
    @DisplayName("Числа заданы и ставка резолвится — эти ворота молчат")
    void configuredNumbersPassTheGate() {
        RiskValidationResult result = validate(configured(), "0.0005");

        assertFalse(hasBlocking(result, RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED));
        assertFalse(hasBlocking(result, RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED));
        assertFalse(hasBlocking(result, RiskCheckCode.FEE_RATE_UNAVAILABLE));
    }

    @Test
    @DisplayName("Незаданное число конфигурации при живом риске не уводит сделку в ошибку")
    void unconfiguredNumberIsCarvedOut() {
        // Повод внутренний: живой риск под контролем, а увод в ERROR создавал
        // бы исполнение по рынку (docs/processes/risk-evaluation.md).
        RiskAppetiteProperties appetite = configured();
        appetite.setGlobalConsecutiveLossLimit(null);
        RiskValidationResult result = validate(appetite, "0.0005");

        RiskBlockAction action = new RiskBlockResolver()
                .resolve(DealContext.builder().deal(new Deal()).build(), DealTranche.Status.MANAGING, result);

        assertEquals(RiskBlockAction.Type.SKIP_ACTION, action.getType());
    }

    private static RiskAppetiteProperties configured() {
        RiskAppetiteProperties properties = new RiskAppetiteProperties();
        properties.setGlobalSimultaneousRiskPerDealPercent(BigDecimal.ONE);
        properties.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal("100"));
        properties.setGlobalConsecutiveLossLimit(3);
        return properties;
    }

    /** Действие с объявленным уровнем стопа: ставка комиссии ему нужна. */
    private RiskValidationResult validate(RiskAppetiteProperties appetite, String takerFeeRate) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalState("live");
        rules.setExternalTakerFeeRate(takerFeeRate);
        when(rulesDataService.findByInstrumentId(anyLong())).thenReturn(Optional.of(rules));

        Instrument instrument = new Instrument();
        instrument.setId(1L);
        instrument.setMarginMode(Instrument.MarginMode.ISOLATED);

        Deal deal = new Deal();
        deal.setId(1L);
        deal.setDirection(StrategyTradeDirection.LONG);

        BalanceContainer balanceContainer = new BalanceContainer();
        balanceContainer.setExternalAvailableEquity(new BigDecimal("1000"));

        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setActionType(StrategyActionType.REPLACE);
        action.setTargetActionKey("primary-stop");

        CalculatedStrategyAction calculated = CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedSize(CalculatedSize.builder().sizeContracts(BigDecimal.ONE).build())
                .calculatedPrice(CalculatedPrice.builder()
                        .roundedPrice(new BigDecimal("100"))
                        .stopLossPrice(ResolvedStopLossPrice.builder()
                                .triggerPrice(new BigDecimal("90"))
                                .build())
                        .build())
                .build();

        return new RiskValidator(rulesDataService, appetite).validate(calculated, DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .strategyDetail(new StrategyDetail())
                .balanceContainer(balanceContainer)
                .build());
    }

    private boolean hasBlocking(RiskValidationResult result, RiskCheckCode code) {
        return result.getChecks().stream()
                .anyMatch(check -> code.equals(check.getCode())
                        && RiskCheckStatus.BLOCKED.equals(check.getStatus()));
    }
}
