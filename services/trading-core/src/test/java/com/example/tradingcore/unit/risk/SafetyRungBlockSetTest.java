package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.pairState;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.reducingOnlyAction;
import static com.example.tradingcore.unit.risk.RiskFixture.takeProfitAction;
import static com.example.tradingcore.unit.risk.RiskFixture.trailingAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.account.AccountInstrumentState;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Блок-сет стоящей ступени радиуса — группа {@code U17} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code safetyRungBlocksAction};
 * блок-сет — docs/rules/instrument-hold.md §Enforcement).
 *
 * <p><b>Базовая сборка</b> — U1.1: у пары «счёт, инструмент» ступень не
 * стои́т.
 */
class SafetyRungBlockSetTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U17.1 — ступени нет, risk-creating вход: отказа нет")
    void u17_1_withoutARungARiskCreatingEntryPasses() {
        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U17.2 — ступень запрета входа: в пояснении назван номинал ступени")
    void u17_2_anEntryBlockedRungRejectsTheEntryAndNamesItself() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
        assertThat(result.getChecks().getFirst().getComment()).contains("ENTRY_BLOCKED");
    }

    @Test
    @DisplayName("U17.3 — ступень запрета торговли: тот же отказ")
    void u17_3_aTradeBlockedRungRejectsTheEntryToo() {
        harness.givenPairState(pairState(Instrument.SafetyRung.TRADE_BLOCKED));

        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
        assertThat(result.getChecks().getFirst().getComment()).contains("TRADE_BLOCKED");
    }

    /** Транша у действия нет: ослабляет ли уровень защиту, спросить не у кого. */
    @Test
    @DisplayName("U17.4 — создание защиты с уровнем остановки убытка, транш не назван: отказ")
    void u17_4_aProtectiveActTouchingTheStopLevelIsBlocked() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext())))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    @Test
    @DisplayName("U17.5 — постановка уровня фиксации прибыли: контроля она не ослабляет")
    void u17_5_aTakeProfitPlacementPassesUnderTheRung() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(takeProfitAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U17.6 — действие «только уменьшает позицию»: выход из-под блок-сета")
    void u17_6_aPositionReducingActPassesUnderTheRung() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(reducingOnlyAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U17.7 — ступень у пары пуста: отказа нет")
    void u17_7_anEmptyRungRejectsNothing() {
        harness.givenPairState(pairState(null));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U17.8 — ступень стои́т у ДРУГОЙ пары того же инструмента: ключ — пара")
    void u17_8_aRungOnAnotherPairOfTheSameInstrumentIsNotRead() {
        AccountInstrumentState neighbouringAccountStandsInARung = pairState(Instrument.SafetyRung.TRADE_BLOCKED);
        neighbouringAccountStandsInARung.setExchangeAccountId(ACCOUNT_ID + 1);
        harness.givenPairState(pairState(Instrument.SafetyRung.ACTIVE));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("строка соседнего счёта существует, но читается строка своей пары")
                .isEmpty();
        verify(harness.pairStateBoundary()).getRequiredByPair(ACCOUNT_ID, INSTRUMENT_ID);
        assertThat(neighbouringAccountStandsInARung.hasStandingSafetyRung()).isTrue();
    }

    @Test
    @DisplayName("U17.9 — ступень и превышенный поактный потолок: ступень идёт ПЕРВОЙ")
    void u17_9_theRungPrecedesTheCeilingsInTheAccumulationOrder() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(entryAction(), contextBuilder(emptyDeal())
                .strategyDetail(detail("0.9295", "3", "10", "300"))
                .build())))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                        RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U17.10 — перенос стопа ближе к цене, чем защита транша: подтяжка проходит")
    void u17_10_aTighteningTransferPassesUnderTheRung() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(transferAction("2950"), workingContext(), protectedTranche())))
                .isEmpty();
    }

    @Test
    @DisplayName("U17.11 — перенос стопа дальше от цены, чем защита транша: отказ")
    void u17_11_aLooseningTransferIsBlocked() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(transferAction("2850"), workingContext(), protectedTranche())))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    @Test
    @DisplayName("U17.12 — уровень акта равен действующему уровню транша: отказа нет")
    void u17_12_anEqualLevelDoesNotWeaken() {
        harness.givenPairState(pairState(Instrument.SafetyRung.TRADE_BLOCKED));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext(),
                protectedTranche()))).isEmpty();
    }

    @Test
    @DisplayName("U17.13 — у транша нет живой защиты с уровнем: первая защита проходит")
    void u17_13_theFirstProtectionOverAnUncoveredTranchePasses() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext(),
                tranche(List.of(), List.of())))).isEmpty();
    }

    /** Перенос может заместить лучшую защиту; уровень между двумя её отодвигал бы. */
    @Test
    @DisplayName("U17.14 — две защиты транша, уровень акта между ними: отказ")
    void u17_14_aLevelBetweenTwoProtectionsIsBlocked() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));
        DealTranche twoLevels = tranche(List.of(),
                List.of(protection(55L, TRANCHE_ID, "2900", "100"), protection(56L, TRANCHE_ID, "2950", "100")));

        assertThat(codes(harness.validate(transferAction("2920"), workingContext(), twoLevels)))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    /** Уровень трейлинга наблюдается после активации: подтяжку нечем доказать. */
    @Test
    @DisplayName("U17.15 — трейлинг над защищённым траншем: отказ")
    void u17_15_aTrailingActStaysInTheBlockSet() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(harness.validate(trailingAction("2990"), workingContext(), protectedTranche())))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    /** Транш с одной живой отдельной защитой на уровне базовой сборки. */
    private static DealTranche protectedTranche() {
        return tranche(List.of(), List.of(protection(STOP.toPlainString())));
    }
}
