package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryActionWithUndeclaredReducingFlag;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
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
 * Поактный потолок при проценте 0.92955 равен 92.955 — ровно риску акта
 * базовой сборки; катастрофический при множителе 30 равен 3000 — ровно
 * его нотиналу. Пара «ровно / на волос ниже» пинит оба числа: одиночный
 * зелёный прогон их не различает.
 */
class ActRiskAndNotionalTest {

    /** Потолки ровно на посчитанных слагаемых: обе границы включены. */
    private static final StrategyDetail EXACTLY_AT_BOTH = detail("0.92955", "3", "10", "30");

    /** Потолки на волос ниже: обе границы перебраны. */
    private static final StrategyDetail A_HAIR_BELOW_BOTH = detail("0.9295", "3", "10", "29.99");

    /** Тесен только поактный потолок: катастрофический к границе не подходит. */
    private static final StrategyDetail ONLY_PER_ACTION_TIGHT = detail("0.9295", "3", "10", "300");

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U10.1 — risk-creating вход с уровнем: риск акта 92.955, нотинал акта 3000")
    void u10_1_bothActSummandsArePinnedByTheirCeilings() {
        assertThat(codes(harness.validate(entryAction(), context(EXACTLY_AT_BOTH))))
                .as("потолки, равные слагаемым, не перебираются")
                .isEmpty();
        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW_BOTH))))
                .as("потолки на волос ниже перебираются обоими слагаемыми")
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U10.2 — действие risk-weakening: оба слагаемых акта — ноль")
    void u10_2_aRiskWeakeningActContributesNothing() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()),
                context(A_HAIR_BELOW_BOTH)))).isEmpty();
    }

    @Test
    @DisplayName("U10.3 — стоимость контракта у правил пуста: оба слагаемых — ноль")
    void u10_3_anAbsentContractValueZeroesBothSummands() {
        harness.givenRules(rules(null, "1", "1", "0.0005"));

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW_BOTH)))).isEmpty();
    }

    @Test
    @DisplayName("U10.4 — ставка комиссии пуста: риск акта ноль, отказ приходит проверкой ставки")
    void u10_4_anAbsentFeeRateZeroesTheActRisk() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", null));

        assertThat(codes(harness.validate(entryAction(), context(ONLY_PER_ACTION_TIGHT))))
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("U10.5 — якорь пуст: оба слагаемых — ноль")
    void u10_5_anAbsentAnchorZeroesBothSummands() {
        assertThat(codes(harness.validate(entryAction("10", null, STOP.toPlainString()),
                context(A_HAIR_BELOW_BOTH)))).isEmpty();
    }

    @Test
    @DisplayName("U10.6 — уровень на прибыльной стороне: риск акта отрицателен и клэмпа нулём нет")
    void u10_6_aNegativeActRiskEntersTheInequalityAsItIs() {
        DealContext dealTakingRiskAlready = contextBuilder(deal(new BigDecimal("350"), null))
                .strategyDetail(detail("1", "3", "10", "300"))
                .build();

        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), dealTakingRiskAlready)))
                .as("350 + (−96.95) = 253.05 против потолка 300; клэмп нулём дал бы отказ")
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U10.7 — признак «только уменьшает» пуст: слагаемые как у создающего риск")
    void u10_7_anUndeclaredReducingFlagCountsAsRiskCreating() {
        assertThat(codes(harness.validate(entryActionWithUndeclaredReducingFlag(STOP.toPlainString()),
                context(A_HAIR_BELOW_BOTH))))
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    /** Контекст базовой сборки с названной деталью стратегии. */
    private static DealContext context(StrategyDetail detail) {
        return contextBuilder(emptyDeal()).strategyDetail(detail).build();
    }
}
