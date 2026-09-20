package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.instrument;
import static com.example.tradingcore.unit.risk.RiskFixture.size;
import static com.example.tradingcore.unit.risk.RiskFixture.sizeWithoutContracts;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.withSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Входные гейты преконтроля: порядок и fail-fast — группа {@code U1}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §Проверки, таблица fail-fast).
 *
 * <p><b>Форма выхода у группы своя:</b> fail-fast возвращает перечень
 * РОВНО ИЗ ОДНОГО члена и остальных проверок не запускает. Поэтому
 * каждая клетка называет и состав перечня, и то, чего в нём нет: проверка,
 * идущая следом, до входа не доехала.
 *
 * <p><b>Базовая сборка</b> — {@code RiskFixture.workingContext()} плюс
 * {@code RiskFixture.entryAction()}: граф предъявлен целиком, правила
 * материализованы и торгуемы, валюта резолвлена, база живая и
 * положительна, оба числа тенанта назначены, пара изолированная без
 * ступени.
 */
class PrecheckInputGateTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U1.1 — базовая сборка: решение разрешающее, перечень отказов пуст")
    void u1_1_theWorkingAssemblyIsAllowedWithoutASingleCheck() {
        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOWED);
        assertThat(result.getChecks()).isEmpty();
    }

    @Test
    @DisplayName("U1.2 — граф предъявлен не целиком: один член, ни одной границы не прочитано")
    void u1_2_anIncompleteGraphStopsBeforeEveryBoundaryRead() {
        DealContext dealContext = contextBuilder(emptyDeal()).graphComplete(false).build();

        RiskValidationResult result = harness.validate(entryAction(), dealContext);

        assertThat(codes(result)).containsExactly(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
        verifyNoInteractions(harness.rulesBoundary(), harness.pairStateBoundary(), harness.appetiteBoundary());
    }

    @Test
    @Tag("debt")
    @DisplayName("U1.3 — признак полноты графа не объявлен: пустота читается как «не предъявлен» (R-5)")
    void u1_3_anAbsentGraphCompleteFlagReadsAsNotPresented() {
        DealContext dealContext = contextBuilder(emptyDeal()).graphComplete(null).build();

        RiskValidationResult result = harness.validate(entryAction(), dealContext);

        assertThat(codes(result))
                .as("ожидание из дома: благоприятное умолчание запрещено")
                .containsExactly(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U1.4 — размера у действия нет вовсе: действие невалидно, правила не читаются")
    void u1_4_anActionWithoutASizeIsInvalid() {
        assertSingleInvalidAction(withSize(entryAction(), null));
    }

    @Test
    @DisplayName("U1.5 — размер в контрактах пуст: тот же отказ")
    void u1_5_anEmptySizeInContractsIsInvalid() {
        assertSingleInvalidAction(withSize(entryAction(), sizeWithoutContracts()));
    }

    @Test
    @DisplayName("U1.6 — размер в контрактах ноль: граница непозитивности включает ноль")
    void u1_6_aZeroSizeIsInvalidToo() {
        assertSingleInvalidAction(withSize(entryAction(), size("0")));
    }

    @Test
    @DisplayName("U1.7 — размер отрицателен: тот же отказ")
    void u1_7_aNegativeSizeIsInvalid() {
        assertSingleInvalidAction(withSize(entryAction(), size("-1")));
    }

    @Test
    @DisplayName("U1.8 — правила инструмента не материализованы: валюта и база не читаются")
    void u1_8_unmaterializedInstrumentRulesStopThePass() {
        harness.givenRules(null);

        RiskValidationResult result = harness.validate(entryAction(), workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.INSTRUMENT_RULES_MISSING);
        verifyNoInteractions(harness.pairStateBoundary(), harness.appetiteBoundary());
    }

    @Test
    @DisplayName("U1.9 — расчётная валюта инструмента пуста: валюта не резолвлена")
    void u1_9_anEmptySettlementCurrencyStopsThePass() {
        assertSingleCode(currencyContext(""), RiskCheckCode.INSTRUMENT_SETTLE_CURRENCY_MISSING);
    }

    @Test
    @DisplayName("U1.10 — расчётная валюта из пробелов: пустым считается и бланковая строка")
    void u1_10_aBlankSettlementCurrencyCountsAsEmpty() {
        assertSingleCode(currencyContext("   "), RiskCheckCode.INSTRUMENT_SETTLE_CURRENCY_MISSING);
    }

    @Test
    @DisplayName("U1.11 — база риска пуста: ни снимка, ни живой базы счёта")
    void u1_11_anAbsentRiskBaseStopsThePass() {
        assertSingleCode(baseContext(null), RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U1.12 — база риска ноль: тот же отказ")
    void u1_12_aZeroRiskBaseStopsThePass() {
        assertSingleCode(baseContext("0"), RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U1.13 — порог серии убытков не назначен: энфорсера остановки не существует")
    void u1_13_anUnassignedLossStreakLimitStopsThePass() {
        harness.givenAppetite(appetite("1", null));

        assertSingleCode(workingContext(), RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U1.14 — максимальный риск на сделку не назначен: ни одно неравенство не считается")
    void u1_14_anUnassignedRiskAppetiteNumberStopsThePass() {
        harness.givenAppetite(appetite(null, 3));

        assertSingleCode(workingContext(), RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U1.15 — строки риск-аппетита нет вовсе: первым мерится порог серии убытков")
    void u1_15_anAbsentAppetiteRowFailsOnTheLossStreakLimitFirst() {
        harness.givenAppetite(null);

        assertSingleCode(workingContext(), RiskCheckCode.LOSS_LIMIT_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U1.16 — ложны сразу полнота графа и размер: следующая проверка до входа не доехала")
    void u1_16_theGraphGateWinsOverTheSizeGate() {
        DealContext dealContext = contextBuilder(emptyDeal()).graphComplete(false).build();

        RiskValidationResult result = harness.validate(withSize(entryAction(), null), dealContext);

        assertThat(codes(result)).containsExactly(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U1.17 — чтение строки пары отказало: исключение выходит наружу неперехваченным")
    void u1_17_aFailingPairStateReadIsNotTurnedIntoAControlledResult() {
        harness.givenPairStateFails(new IllegalStateException("pair state read failed"));

        assertThatThrownBy(() -> harness.validate(entryAction(), workingContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("pair state read failed");
    }

    private void assertSingleInvalidAction(CalculatedStrategyAction action) {
        RiskValidationResult result = harness.validate(action, workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.CALCULATED_ACTION_INVALID);
        verifyNoInteractions(harness.rulesBoundary(), harness.pairStateBoundary(), harness.appetiteBoundary());
    }

    private void assertSingleCode(DealContext dealContext, RiskCheckCode expected) {
        assertThat(codes(harness.validate(entryAction(), dealContext))).containsExactly(expected);
    }

    /** Контекст базовой сборки с названной расчётной валютой инструмента. */
    private static DealContext currencyContext(String settlementCurrency) {
        return contextBuilder(emptyDeal()).instrument(instrument(settlementCurrency)).build();
    }

    /** Контекст базовой сборки с названной живой базой счёта и без снимка у сделки. */
    private static DealContext baseContext(String riskBase) {
        return contextBuilder(emptyDeal()).exchangeAccount(account(riskBase)).build();
    }
}
