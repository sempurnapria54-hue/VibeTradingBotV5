package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskValidator;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.deal.ProtectionCoverageGate;
import com.example.tradingcore.domain.safety.AnomalyFinding;
import com.example.tradingcore.domain.safety.AnomalyReaction;
import com.example.tradingcore.domain.safety.AnomalyScan;
import com.example.tradingcore.domain.safety.DealInvariantDetectors;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.persistence.service.DealDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Детектор переоценки инварианта ликвидации ({@code A13}) — группа
 * {@code U20} документа `.claude/tests/cases/trading-core-safety.md` (дом —
 * docs/components/AnomalyJob.md §«Переоценка инварианта ликвидации»;
 * реакция — docs/rules/instrument-hold.md §Триггеры).
 *
 * <p><b>Базовая сборка.</b> Сделка LONG с одним траншем, чья экспозиция 10
 * покрыта живой отдельной защитой уровня 2800, и живым эпизодом размера 10;
 * граф предъявлен целиком. Цена ликвидации эпизода варьируется клеткой.
 * Сборщик графа, выборка сделок, сверка экспозиции, преконтроль и носитель
 * обязательства покрытия подменены — соседние детекторы того же обхода
 * поставлены молчать; реакция подменена, и её вход — находка — есть выход
 * клетки.
 *
 * <p><b>Признак не подменяется:</b> он считается настоящим предикатом модели
 * сделки по её настоящим полям (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»).
 */
class LiquidationBeforeStopDetectorTest {

    private static final Long DEAL_ID = 3L;

    private final DealDataService dealDataService = mock(DealDataService.class);
    private final DealContextService dealContextService = mock(DealContextService.class);
    private final DealTerminalGate dealTerminalGate = mock(DealTerminalGate.class);
    private final ProtectionCoverageGate protectionCoverageGate = mock(ProtectionCoverageGate.class);
    private final RiskValidator riskValidator = mock(RiskValidator.class);
    private final AnomalyReaction reaction = mock(AnomalyReaction.class);
    private final AnomalyScan scan = mock(AnomalyScan.class);

    private final DealInvariantDetectors detectors = new DealInvariantDetectors(dealDataService,
            dealContextService, dealTerminalGate, protectionCoverageGate, riskValidator, reaction);

    private final Map<String, Object> observedRows = Map.of("instId", INSTRUMENT_EXTERNAL_ID);

    LiquidationBeforeStopDetectorTest() {
        when(dealTerminalGate.exposureReconciled(any(), any())).thenReturn(true);
        when(protectionCoverageGate.trancheViolated(any(), any())).thenReturn(false);
        when(protectionCoverageGate.hasLiveCommitment(any(), any())).thenReturn(false);
        when(riskValidator.ceilingsBreachedWithoutAct(any())).thenReturn(List.of());
        when(scan.observedRowsOf(INSTRUMENT_EXTERNAL_ID)).thenReturn(observedRows);
    }

    @Test
    @DisplayName("U20.1 — ликвидация поднялась выше стопа LONG: находка пары, жёсткая, своим кодом, два тика")
    void u20_1_aBreachRaisesTheHardPairFinding() {
        ExchangeAccount account = account();
        givenPass(heldDeal("2810"), true);

        detectors.detect(scan, account);

        ArgumentCaptor<AnomalyFinding> finding = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(finding.capture(), eq(account));
        assertThat(finding.getValue().getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(finding.getValue().getRung()).isEqualTo(HoldRung.HARD);
        assertThat(finding.getValue().getCode()).isEqualTo(Constants.Hold.INSTRUMENT_LIQUIDATION_BEFORE_STOP);
        assertThat(finding.getValue().getHysteresisTicks()).isEqualTo(2);
        assertThat(finding.getValue().getJournalOnly()).isFalse();
        assertThat(finding.getValue().getInstrument().getExternalId()).isEqualTo(INSTRUMENT_EXTERNAL_ID);
        assertThat(finding.getValue().getExternalObservation()).isEqualTo(observedRows);
    }

    @Test
    @DisplayName("U20.2 — стоп выше цены ликвидации: находки нет")
    void u20_2_aHeldInvariantIsSilent() {
        givenPass(heldDeal("2750"), true);

        detectors.detect(scan, account());

        verify(reaction, never()).apply(any(), any());
    }

    /** Пустоту детектор читает молчанием: оценка вместо факта площадки не подставляется. */
    @Test
    @DisplayName("U20.3 — площадка цены ликвидации не называет: находки нет")
    void u20_3_anUnmeasuredInvariantIsSilent() {
        givenPass(heldDeal(null), true);

        detectors.detect(scan, account());

        verify(reaction, never()).apply(any(), any());
    }

    /**
     * В окне замены защиты уровень на всю позицию читается по худшей из двух,
     * и прежняя, ещё не снятая, дала бы признак, которого после замены не будет.
     */
    @Test
    @DisplayName("U20.4 — нарушение при живом обязательстве покрытия у транша: находки нет")
    void u20_4_aLiveCoverageCommitmentSilencesTheDetector() {
        givenPass(heldDeal("2810"), true);
        when(protectionCoverageGate.hasLiveCommitment(any(), any())).thenReturn(true);

        detectors.detect(scan, account());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("U20.5 — нарушение на неполном графе сделки: находки нет")
    void u20_5_anIncompleteGraphSilencesTheDetector() {
        givenPass(heldDeal("2810"), false);

        detectors.detect(scan, account());

        verify(reaction, never()).apply(any(), any());
    }

    @Test
    @DisplayName("U20.6 — ликвидация опустилась ниже стопа SHORT: находка той же формы")
    void u20_6_aShortBreachRaisesTheSameFinding() {
        Deal breached = heldDeal("3190");
        breached.setDirection(StrategyTradeDirection.SHORT);
        breached.getTranches().getFirst().setAlgoOrders(new ArrayList<>(List.of(stop("3200"))));
        givenPass(breached, true);

        detectors.detect(scan, account());

        ArgumentCaptor<AnomalyFinding> finding = ArgumentCaptor.forClass(AnomalyFinding.class);
        verify(reaction).apply(finding.capture(), any());
        assertThat(finding.getValue().getCode()).isEqualTo(Constants.Hold.INSTRUMENT_LIQUIDATION_BEFORE_STOP);
    }

    // --- сборка ------------------------------------------------------------

    private void givenPass(Deal subject, Boolean graphComplete) {
        when(dealDataService.findNonTerminalByExchangeAccountId(any())).thenReturn(List.of(subject));
        when(dealContextService.build(subject)).thenReturn(DealContext.builder()
                .deal(subject)
                .exchangeAccount(account())
                .instrument(instrument())
                .graphComplete(graphComplete)
                .build());
    }

    /** Сделка базовой сборки с названной ценой ликвидации эпизода; пусто — не названа. */
    private Deal heldDeal(String liquidationPrice) {
        DealTranche tranche = new DealTranche();
        tranche.setId(21L);
        tranche.setStatus(DealTranche.Status.MANAGING);
        tranche.setEntryFilled(new BigDecimal("10"));
        tranche.setOrders(new ArrayList<>());
        tranche.setAlgoOrders(new ArrayList<>(List.of(stop("2800"))));
        Position episode = new Position();
        episode.setId(900L);
        episode.setStatus(Position.Status.ACTIVE);
        episode.setExternalSize(new BigDecimal("10"));
        episode.setExternalLiquidationPrice(isNull(liquidationPrice) ? null : new BigDecimal(liquidationPrice));
        Deal subject = deal(DEAL_ID);
        subject.setDirection(StrategyTradeDirection.LONG);
        subject.setTranches(new ArrayList<>(List.of(tranche)));
        subject.setPositions(new ArrayList<>(List.of(episode)));
        return subject;
    }

    /** Живая отдельная защита размера экспозиции с названным уровнем остановки убытка. */
    private AlgoOrder stop(String level) {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.MARK);
        stopLoss.setValue(new BigDecimal(level));
        AlgoOrder protection = new AlgoOrder();
        protection.setId(55L);
        protection.setDealTrancheId(21L);
        protection.setStatus(AlgoOrder.Status.ACTIVE);
        protection.setConditionType(AlgoOrder.ConditionType.STOP_LOSS);
        protection.setSize(new BigDecimal("10"));
        protection.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS, new Trigger(stopLoss, null), null));
        return protection;
    }
}
