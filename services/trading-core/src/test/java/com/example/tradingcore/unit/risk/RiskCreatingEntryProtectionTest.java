package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.actionWithUnresolvedStopTrigger;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryActionWithUndeclaredReducingFlag;
import static com.example.tradingcore.unit.risk.RiskFixture.entrySourceAction;
import static com.example.tradingcore.unit.risk.RiskFixture.reducingOnlyAction;
import static com.example.tradingcore.unit.risk.RiskFixture.size;
import static com.example.tradingcore.unit.risk.RiskFixture.takeProfitAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Risk-creating вход без резолвимого уровня — группа {@code U5}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §Проверки, строка
 * {@code RISK_CREATING_ENTRY_WITHOUT_STOP}; класс действия —
 * docs/rules/risk-policy.md §«Риск акта зависит от класса действия»).
 */
class RiskCreatingEntryProtectionTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U5.1 — вход без уровня остановки убытка вовсе: сайзинга в обход потолка не происходит")
    void u5_1_aRiskCreatingEntryWithoutAStopIsRejected() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, null), workingContext())))
                .containsExactly(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP);
    }

    @Test
    @DisplayName("U5.2 — уровень объявлен, цена срабатывания пуста: тот же отказ")
    void u5_2_aDeclaredButUnresolvedStopIsRejectedToo() {
        CalculatedStrategyAction entryWithDeclaredButUnresolvedStop = CalculatedStrategyAction.builder()
                .sourceAction(entrySourceAction())
                .calculatedPrice(actionWithUnresolvedStopTrigger().getCalculatedPrice())
                .calculatedSize(size("10"))
                .description("entry with declared but unresolved stop")
                .build();

        assertThat(codes(harness.validate(entryWithDeclaredButUnresolvedStop, workingContext())))
                .containsExactly(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP);
    }

    @Test
    @DisplayName("U5.3 — действие помечено «только уменьшает позицию»: это не создание риска")
    void u5_3_aPositionReducingActionCreatesNoRisk() {
        assertThat(codes(harness.validate(reducingOnlyAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U5.4 — создание условной заявки: входной тропы алго-заявкой не существует")
    void u5_4_anAlgoOrderCreationIsNotAnEntry() {
        assertThat(codes(harness.validate(takeProfitAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U5.5 — признак «только уменьшает» пуст: пустое читается как «создаёт риск»")
    void u5_5_anUndeclaredReducingFlagReadsAsRiskCreating() {
        assertThat(codes(harness.validate(entryActionWithUndeclaredReducingFlag(null), workingContext())))
                .containsExactly(RiskCheckCode.RISK_CREATING_ENTRY_WITHOUT_STOP);
    }

    @Test
    @DisplayName("U5.6 — вход с уровнем и ценой срабатывания: отказа нет")
    void u5_6_anEntryWithAResolvedStopPasses() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, STOP.toPlainString()), workingContext())))
                .isEmpty();
    }
}
