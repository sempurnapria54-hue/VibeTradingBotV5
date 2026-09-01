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
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyLevelSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPlacementRole;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.persistence.service.InstrumentExternalRulesDataService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает область охраны стороны уровня с её домом
 * (docs/spec/stop-distance.json, величина {@code primaryStopOnLossSide}).
 *
 * <p>Несущее: область ограничена ДВУМЯ осями — роль объявления и источник
 * уровня. У защитного создания с наблюдаемым уровнем (трейлинг) уровня в
 * момент постановки нет вовсе, и требовать от него стороны значило бы
 * отвергать ступень, ради которой держится прибыль.
 */
class PrimaryStopSideAreaTest {

    private final InstrumentExternalRulesDataService rulesDataService =
            mock(InstrumentExternalRulesDataService.class);
    private final RiskAppetiteProperties riskAppetite = configuredAppetite();
    private final RiskValidator validator = new RiskValidator(rulesDataService, riskAppetite);

    @Test
    @DisplayName("Первичный объявленный уровень на прибыльной стороне отвергается")
    void primaryDeclaredLevelOnProfitSideRejected() {
        StrategyAlgoOrderAction action = declaredStopAction();

        RiskValidationResult result = validate(action);

        assertTrue(hasBlocking(result, RiskCheckCode.STOP_LOSS_INVALID_SIDE));
    }

    @Test
    @DisplayName("Наблюдаемый уровень (трейлинг) под охрану стороны не подпадает")
    void observedLevelOutsideArea() {
        StrategyAlgoOrderAction action = declaredStopAction();
        action.setStopLossSettings(null);
        action.setTrailingSettings(new TrailingSettings());

        RiskValidationResult result = validate(action);

        assertEquals(StrategyLevelSource.OBSERVED, action.levelSource());
        assertFalse(hasBlocking(result, RiskCheckCode.STOP_LOSS_INVALID_SIDE));
    }

    @Test
    @DisplayName("Перенос уже стоящего уровня под охрану стороны не подпадает")
    void transferOutsideArea() {
        StrategyAlgoOrderAction action = declaredStopAction();
        action.setActionType(StrategyActionType.REPLACE);
        action.setTargetActionKey("primary-stop");

        RiskValidationResult result = validate(action);

        assertEquals(StrategyPlacementRole.TRANSFER, action.placementRole());
        assertFalse(hasBlocking(result, RiskCheckCode.STOP_LOSS_INVALID_SIDE));
    }

    @Test
    @DisplayName("Замещение без указанной цели переносом не считается — роль остаётся первичной")
    void replaceWithoutTargetStaysPrimary() {
        StrategyAlgoOrderAction action = declaredStopAction();
        action.setActionType(StrategyActionType.REPLACE);

        assertEquals(StrategyPlacementRole.PRIMARY, action.placementRole());
    }

    /** Создающее действие с объявленным блоком стопа: PRIMARY + DECLARED. */
    private StrategyAlgoOrderAction declaredStopAction() {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setActionType(StrategyActionType.CREATE);
        action.setStopLossSettings(new com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings());
        return action;
    }

    /** Уровень 110 при якоре 100 у LONG — прибыльная сторона. */
    private RiskValidationResult validate(StrategyAlgoOrderAction action) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalState("live");
        when(rulesDataService.findByInstrumentId(anyLong())).thenReturn(Optional.of(rules));

        Instrument instrument = new Instrument();
        instrument.setId(1L);
        instrument.setMarginMode(Instrument.MarginMode.ISOLATED);

        Deal deal = new Deal();
        deal.setId(1L);
        deal.setDirection(StrategyTradeDirection.LONG);

        Balance balance = new Balance();
        BalanceContainer balanceContainer = new BalanceContainer();
        balanceContainer.setExternalAvailableEquity(new BigDecimal("1000"));
        balanceContainer.setBalances(List.of(balance));

        CalculatedStrategyAction calculated = CalculatedStrategyAction.builder()
                .sourceAction(action)
                .calculatedSize(CalculatedSize.builder().sizeContracts(BigDecimal.ONE).build())
                .calculatedPrice(CalculatedPrice.builder()
                        .roundedPrice(new BigDecimal("100"))
                        .stopLossPrice(ResolvedStopLossPrice.builder()
                                .triggerPrice(new BigDecimal("110"))
                                .build())
                        .build())
                .build();

        return validator.validate(calculated, DealContext.builder()
                .deal(deal)
                .instrument(instrument)
                .strategyDetail(new StrategyDetail())
                .balanceContainer(balanceContainer)
                .build());
    }

    /** Числа риск-аппетита заданы: иначе преконтроль отказывает до всех своих ветвей. */
    private static RiskAppetiteProperties configuredAppetite() {
        RiskAppetiteProperties properties = new RiskAppetiteProperties();
        properties.setGlobalSimultaneousRiskPerDealPercent(BigDecimal.ONE);
        properties.setGlobalCatastrophicRiskPerDealMultiplier(new BigDecimal("100"));
        properties.setGlobalConsecutiveLossLimit(3);
        return properties;
    }

    private boolean hasBlocking(RiskValidationResult result, RiskCheckCode code) {
        return result.getChecks().stream()
                .anyMatch(check -> code.equals(check.getCode())
                        && RiskCheckStatus.BLOCKED.equals(check.getStatus()));
    }
}
