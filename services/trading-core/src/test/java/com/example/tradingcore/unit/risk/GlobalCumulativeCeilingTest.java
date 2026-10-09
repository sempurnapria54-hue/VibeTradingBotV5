package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.decimal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingAppetite;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Глобальная редакция кумулятивного потолка сделки (дом —
 * docs/rules/risk-policy.md §«Четыре потолка на разные вопросы»; форма —
 * docs/spec/risk-limits.json, величина {@code withinGlobalCumulative};
 * основание — .claude/decisions/global-cumulative-risk-ceiling.md).
 *
 * <p><b>Числа группы:</b> процент сделки 1, предел множителя 2 — потолок
 * {@code 2 × 1 % × 10000 = 200}. Деталь стратегии шире предела: поактный
 * процент 0.95 и множитель 10 дают стратегийный потолок 950 — так
 * достижима тропа предела, пониженного после приёма стратегии. Риск акта
 * базовой сборки — 92.955.
 *
 * <p><b>Сомножитель — процент СДЕЛКИ, а не поактный процент стратегии:</b>
 * клетка «ровно в потолок» проходит при 200 и отвергалась бы при
 * {@code 2 × 0.95 % × 10000 = 190}.
 */
class GlobalCumulativeCeilingTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U36.1 — взятое плюс риск акта ровно в глобальный потолок: граница включена, "
            + "сомножитель — процент сделки")
    void theSumExactlyAtTheGlobalCeilingPasses() {
        givenGlobalMultiplier("2");

        assertThat(codes(harness.validate(entryAction(), context("107.045"))))
                .as("107.045 + 92.955 = 200 против 2 × 1 % × 10000")
                .isEmpty();
    }

    @Test
    @DisplayName("U36.2 — сумма выше глобального потолка, стратегийный пропускает: отказ своим кодом")
    void theSumOverTheGlobalCeilingIsRejectedWhileTheStrategyEditionPasses() {
        givenGlobalMultiplier("2");

        RiskValidationResult result = harness.validate(entryAction(), context("107.046"));

        assertThat(codes(result)).containsExactly(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_GLOBAL_EXCEEDED);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo("200.001");
    }

    @Test
    @DisplayName("U36.3 — взятое само выше глобального потолка, риск акта ноль: "
            + "класс действия неравенства не выключает")
    void theDealRiskAloneBreachesTheGlobalCeilingOnAProtectiveAct() {
        givenGlobalMultiplier("2");

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), context("250"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_GLOBAL_EXCEEDED);
    }

    /** Предел множителя кумулятивного потолка окружения; прочие числа — базовой сборки. */
    private void givenGlobalMultiplier(String multiplier) {
        harness.givenAppetite(workingAppetite().toBuilder()
                .globalCumulativeRiskPerDealMultiplier(decimal(multiplier))
                .build());
    }

    /** Контекст группы: взятое сделкой за жизнь и деталь шире предела окружения. */
    private static DealContext context(String dealRiskTaken) {
        return contextBuilder(deal(decimal(dealRiskTaken), null))
                .strategyDetail(detail("0.95", "10", "1"))
                .build();
    }
}
