package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.appetiteWithMaxLeverage;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryActionWithUndeclaredReducingFlag;
import static com.example.tradingcore.unit.risk.RiskFixture.pairStateWithLeverage;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Риск и нотинал акта по классу действия — группа {@code U10}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/rules/risk-policy.md §«Риск акта зависит от класса действия»;
 * форма слагаемых — docs/spec/risk-limits.json, величина
 * {@code actWithinPerAction}).
 *
 * <p><b>Оба слагаемых наблюдаются потолками, настроенными ВПРИТЫК.</b>
 * База группы — тысяча: поактный потолок при проценте 9.2955 равен 92.955
 * — ровно риску акта базовой сборки; потолок нотинала при пределе плеча 3
 * равен 3000 — ровно его нотиналу. Пара «ровно / на волос ниже» пинит оба
 * числа: одиночный зелёный прогон их не различает. Прежняя редакция держала
 * базу в десять тысяч, а потолок нотинала — множителем катастрофического
 * потолка 30; множитель снят, а предел плеча ниже единицы вне области
 * приёма (.claude/decisions/deal-leverage-ceiling.md), поэтому граница
 * перенесена уменьшением базы.
 *
 * <p>Процент одновременного риска сделки поднят до ста, плечо пары —
 * единица: при базе в тысячу и пределе плеча 3 их рабочие значения
 * срабатывали бы на каждой клетке группы своими кодами.
 */
class ActRiskAndNotionalTest {

    /** База группы: делитель обоих пинящих потолков. */
    private static final String GROUP_BASE = "1000";

    /** Поактный потолок ровно на риске акта. */
    private static final StrategyDetail EXACTLY_AT_PER_ACTION = detail("9.2955", "3", "10");

    /** Поактный потолок на волос ниже риска акта. */
    private static final StrategyDetail A_HAIR_BELOW_PER_ACTION = detail("9.295", "3", "10");

    /** Предел плеча, при котором потолок нотинала ровно на нотинале акта. */
    private static final String EXACTLY_AT_NOTIONAL = "3";

    /** Предел плеча, при котором потолок нотинала на волос ниже нотинала акта. */
    private static final String A_HAIR_BELOW_NOTIONAL = "2.999";

    /** Предел плеча, при котором потолок нотинала к границе не подходит. */
    private static final String ROOMY_NOTIONAL = "30";

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenAPairLeverageWithinEveryCellLimit() {
        harness.givenPairState(pairStateWithLeverage(1));
        givenMaxLeverage(ROOMY_NOTIONAL);
    }

    @Test
    @DisplayName("U10.1 — risk-creating вход с уровнем: риск акта 92.955, нотинал акта 3000")
    void u10_1_bothActSummandsArePinnedByTheirCeilings() {
        givenMaxLeverage(EXACTLY_AT_NOTIONAL);
        assertThat(codes(harness.validate(entryAction(), context(EXACTLY_AT_PER_ACTION))))
                .as("потолки, равные слагаемым, не перебираются")
                .isEmpty();
        givenMaxLeverage(A_HAIR_BELOW_NOTIONAL);
        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW_PER_ACTION))))
                .as("потолки на волос ниже перебираются обоими слагаемыми")
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U10.2 — действие risk-weakening: оба слагаемых акта — ноль")
    void u10_2_aRiskWeakeningActContributesNothing() {
        givenMaxLeverage(A_HAIR_BELOW_NOTIONAL);
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()),
                context(A_HAIR_BELOW_PER_ACTION)))).isEmpty();
    }

    @Test
    @DisplayName("U10.3 — стоимость контракта у правил пуста: отказ «правила не материализованы», а не ноль")
    void u10_3_anAbsentContractValueRefusesTheAct() {
        harness.givenRules(rules(null, "1", "1", "0.0005"));
        givenMaxLeverage(A_HAIR_BELOW_NOTIONAL);

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW_PER_ACTION))))
                .containsExactly(RiskCheckCode.INSTRUMENT_RULES_MISSING);
    }

    @Test
    @DisplayName("U10.4 — ставка комиссии пуста: риск акта ноль, отказ приходит проверкой ставки")
    void u10_4_anAbsentFeeRateZeroesTheActRisk() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", null));

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW_PER_ACTION))))
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("U10.5 — якорь пуст: слагаемые не измерены, акт отказывает вычислением")
    void u10_5_anAbsentAnchorRefusesTheAct() {
        givenMaxLeverage(A_HAIR_BELOW_NOTIONAL);
        assertThat(codes(harness.validate(entryAction("10", null, STOP.toPlainString()),
                context(A_HAIR_BELOW_PER_ACTION))))
                .containsExactly(RiskCheckCode.CALCULATED_ACTION_INVALID);
    }

    @Test
    @DisplayName("U10.6 — уровень на прибыльной стороне: отрицательный риск акта обрезается нулём")
    void u10_6_aNegativeActRiskIsClampedToZero() {
        DealContext dealTakingRiskAlready = contextBuilder(deal(new BigDecimal("350"), null))
                .strategyDetail(detail("1", "3", "10"))
                .build();

        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), dealTakingRiskAlready)))
                .as("350 + 0 против потолка 300: отрицательное слагаемое принятого риска не гасит")
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                        RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    @Test
    @DisplayName("U10.7 — признак «только уменьшает» пуст: слагаемые как у создающего риск")
    void u10_7_anUndeclaredReducingFlagCountsAsRiskCreating() {
        givenMaxLeverage(A_HAIR_BELOW_NOTIONAL);
        assertThat(codes(harness.validate(entryActionWithUndeclaredReducingFlag(STOP.toPlainString()),
                context(A_HAIR_BELOW_PER_ACTION))))
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    /** Предел плеча окружения; процент одновременного риска сделки — сто. */
    private void givenMaxLeverage(String maxLeverage) {
        harness.givenAppetite(appetiteWithMaxLeverage("100", maxLeverage));
    }

    /** Контекст группы: база тысяча, названная деталь стратегии. */
    private static DealContext context(StrategyDetail detail) {
        return contextBuilder(emptyDeal()).exchangeAccount(account(GROUP_BASE)).strategyDetail(detail).build();
    }
}
