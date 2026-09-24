package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculatedSize;
import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.ExitOutcome;
import com.example.strategy.engine.calc.SizeMode;
import com.example.strategy.engine.calc.StrategyActionCalculationResult;
import com.example.strategy.engine.calc.StrategyActionCalculator;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.calc.CalculationContextFactory;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealActionStateStatus;
import com.example.tradingcore.domain.command.strategy.ActionPlan;
import com.example.tradingcore.domain.command.strategy.ActionRiskGate;
import com.example.tradingcore.domain.command.strategy.CreateOrderActionExecutor;
import com.example.tradingcore.domain.command.strategy.ExitRoundingReader;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.DealActionStateDataService;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/**
 * Исход округления выхода: пропуск и журнальный отчёт — группа `U16`
 * документа `.claude/tests/cases/trading-core-safety.md` (дом —
 * docs/components/SizeCalculator.md, таблица исходов;
 * docs/components/models/CalculatedSize.md §«Енум `ExitOutcome`»).
 *
 * <p><b>Базовая сборка:</b> читатель с подменёнными журналом и службой
 * строк исполнения; рассчитанное действие выхода несёт исход и операнды
 * округления; транш с экспозицией; правила инструмента с минимумом;
 * строка исполнения запланирована.
 */
class ExitRoundingReaderTest {

    private static final Long STATE_ID = 55L;

    private final AnomalyReportService anomalyReportService = mock(AnomalyReportService.class);
    private final DealActionStateDataService stateDataService = mock(DealActionStateDataService.class);
    private final ExitRoundingReader reader = new ExitRoundingReader(anomalyReportService, stateDataService);

    private final CalculationContextFactory contextFactory = mock(CalculationContextFactory.class);
    private final StrategyActionCalculator calculator = mock(StrategyActionCalculator.class);
    private final CreateOrderActionExecutor orderExecutor = new CreateOrderActionExecutor(contextFactory,
            calculator, mock(ActionRiskGate.class), reader);

    @Test
    @DisplayName("U16.1 — исход «ниже минимума»: строка пропущена, план без команды, отчёт по строке")
    void u16_1_aSkippedExitIsNotSentAndIsJournaled() {
        DealActionState state = planned();

        Optional<ActionPlan> plan = reader.skipBelowMinSize(calculated(ExitOutcome.SKIPPED), context(), state,
                pairContext(), tranche());

        assertThat(plan).hasValueSatisfying(value -> assertThat(value.isEmpty()).isTrue());
        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.SKIPPED);
        verify(stateDataService).save(state);
        assertThat(journaledCode()).isEqualTo(Constants.Hold.PARTIAL_EXIT_BELOW_MIN_SIZE);
    }

    @Test
    @DisplayName("U16.2 — операнды округления едут в отчёт, предмет отчёта — строка исполнения")
    @SuppressWarnings("unchecked")
    void u16_2_theRoundingOperandsRideTheReport() {
        reader.skipBelowMinSize(calculated(ExitOutcome.SKIPPED), context(), planned(), pairContext(), tranche());

        ArgumentCaptor<Map<String, Object>> operands = ArgumentCaptor.forClass(Map.class);
        verify(anomalyReportService).journalOnce(any(), any(), eq("dealActionState:" + STATE_ID),
                operands.capture());
        Map<String, Object> rounding = (Map<String, Object>) operands.getValue().get("exitRounding");
        assertThat(rounding)
                .containsEntry("exitOutcome", ExitOutcome.SKIPPED)
                .containsEntry("closeFraction", new BigDecimal("0.1"))
                .containsEntry("exitSize", new BigDecimal("0"))
                .containsEntry("exitRemainder", new BigDecimal("5"))
                .containsEntry("instrumentMinSize", new BigDecimal("1"))
                .containsKey("instrumentMinSizeSource");
        assertThat((BigDecimal) rounding.get("trancheExposure")).isEqualByComparingTo("5");
    }

    @ParameterizedTest
    @EnumSource(value = ExitOutcome.class, names = "SKIPPED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U16.3 — прочие исходы: пропуска нет, строка не тронута")
    void u16_3_otherOutcomesAreNotSkipped(ExitOutcome outcome) {
        DealActionState state = planned();

        assertThat(reader.skipBelowMinSize(calculated(outcome), context(), state, pairContext(), tranche()))
                .isEmpty();
        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.PLANNED);
        verify(stateDataService, never()).save(any());
    }

    @Test
    @DisplayName("U16.4 — исход «округлён до полного»: отчёт у отправляемого действия")
    void u16_4_aRoundedToFullExitIsJournaled() {
        reader.journalRoundedToFull(calculated(ExitOutcome.FULL), context(), planned(), pairContext(), tranche());

        assertThat(journaledCode()).isEqualTo(Constants.Hold.PARTIAL_EXIT_ROUNDED_TO_FULL);
    }

    @ParameterizedTest
    @EnumSource(value = ExitOutcome.class, names = {"PARTIAL", "FULL_BY_FRACTION"})
    @DisplayName("U16.5 — штатный частичный и полный по доле: отчёта нет")
    void u16_5_declaredOutcomesAreSilent(ExitOutcome outcome) {
        reader.journalRoundedToFull(calculated(outcome), context(), planned(), pairContext(), tranche());
        reader.skipBelowMinSize(calculated(outcome), context(), planned(), pairContext(), tranche());

        verify(anomalyReportService, never()).journalOnce(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U16.6 — не-выход (исход пуст): пропуска и отчёта нет")
    void u16_6_aNonExitIsIgnored() {
        CalculatedStrategyAction entry = CalculatedStrategyAction.builder()
                .calculatedSize(CalculatedSize.builder().sizeMode(SizeMode.OPEN_OR_INCREASE).build())
                .build();

        assertThat(reader.skipBelowMinSize(entry, context(), planned(), pairContext(), tranche())).isEmpty();
        reader.journalRoundedToFull(entry, context(), planned(), pairContext(), tranche());

        verify(anomalyReportService, never()).journalOnce(any(), any(), any(), any());
    }

    @Test
    @DisplayName("U16.7 — запись отчёта бросает: решение о пропуске стоит, исключение наружу не уходит")
    void u16_7_aFailingJournalDoesNotChangeTheDecision() {
        when(anomalyReportService.journalOnce(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("journal down"));
        DealActionState state = planned();

        Optional<ActionPlan> plan = reader.skipBelowMinSize(calculated(ExitOutcome.SKIPPED), context(), state,
                pairContext(), tranche());

        assertThat(plan).isPresent();
        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.SKIPPED);
    }

    /** Исполнитель типа читает исход: пропущенный выход команды не несёт. */
    @Test
    @DisplayName("U16.8 — reduce-only заявка выхода с исходом «ниже минимума»: команды нет, строка пропущена")
    void u16_8_theOrderExecutorSendsNoSkippedExit() {
        when(contextFactory.build(any(), any(), any())).thenReturn(context());
        when(calculator.calculate(any())).thenReturn(
                StrategyActionCalculationResult.success(calculated(ExitOutcome.SKIPPED)));
        DealActionState state = planned();

        ActionPlan plan = orderExecutor.next(new StrategyStep(), reducingExit(), state, pairContext(deal(7L)),
                tranche());

        assertThat(plan.hasCommand()).isFalse();
        assertThat(state.getStatus()).isEqualTo(DealActionStateStatus.SKIPPED);
        assertThat(journaledCode()).isEqualTo(Constants.Hold.PARTIAL_EXIT_BELOW_MIN_SIZE);
    }

    /** Округлённый до полного выход уходит командой, и отчёт заведён. */
    @Test
    @DisplayName("U16.9 — reduce-only заявка выхода с исходом «округлён до полного»: команда уходит, отчёт заведён")
    void u16_9_theOrderExecutorSendsARoundedExitAndJournalsIt() {
        when(contextFactory.build(any(), any(), any())).thenReturn(context());
        when(calculator.calculate(any())).thenReturn(
                StrategyActionCalculationResult.success(calculated(ExitOutcome.FULL)));

        ActionPlan plan = orderExecutor.next(new StrategyStep(), reducingExit(), planned(), pairContext(deal(7L)),
                tranche());

        assertThat(plan.hasCommand()).isTrue();
        assertThat(journaledCode()).isEqualTo(Constants.Hold.PARTIAL_EXIT_ROUNDED_TO_FULL);
    }

    private String journaledCode() {
        ArgumentCaptor<HoldSignal> signal = ArgumentCaptor.forClass(HoldSignal.class);
        verify(anomalyReportService).journalOnce(any(), signal.capture(), any(), any());
        assertThat(signal.getValue().tearsDownRisk()).as("отчёт некритичен").isFalse();
        return signal.getValue().getCode();
    }

    private static CalculatedStrategyAction calculated(ExitOutcome outcome) {
        return CalculatedStrategyAction.builder()
                .calculatedSize(CalculatedSize.builder()
                        .sizeMode(SizeMode.REDUCE_ONLY)
                        .exitOutcome(outcome)
                        .closeFraction(new BigDecimal("0.1"))
                        .exitSize(new BigDecimal("0"))
                        .exitRemainder(new BigDecimal("5"))
                        .sizeContracts(BigDecimal.ZERO)
                        .build())
                .calculatedPrice(CalculatedPrice.builder().sendPriceToExchange(false).build())
                .build();
    }

    private static CalculationContext context() {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalMinSize("1");
        rules.setExternalLotSize("1");
        StrategyOrderAction action = new StrategyOrderAction();
        action.setKey("bull_partial_exit");
        return CalculationContext.builder().action(action).instrumentExternalRules(rules).build();
    }

    private static StrategyOrderAction reducingExit() {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setId(103L);
        action.setKey("bull_partial_exit");
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setDirection(StrategyTradeDirection.LONG);
        action.setPositionReducingOnly(true);
        return action;
    }

    private static DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setEntryFilled(new BigDecimal("5"));
        return tranche;
    }

    private static DealActionState planned() {
        DealActionState state = new DealActionState();
        state.setId(STATE_ID);
        state.setStatus(DealActionStateStatus.PLANNED);
        return state;
    }
}
