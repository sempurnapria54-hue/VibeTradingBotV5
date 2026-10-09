package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.appetiteWithMaxLeverage;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.pairStateWithLeverage;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Плечо пары против предела плеча конфигурации на преконтроле (дом —
 * docs/rules/trading-constraints.md: предел проверяется дважды —
 * назначением и преконтролем акта, создающего риск, потому что предел мог
 * понизиться после назначения; основание —
 * .claude/decisions/deal-leverage-ceiling.md).
 *
 * <p><b>Предел группы — десять</b>, как у тестового окружения; при плече
 * одиннадцать оценка ликвидации входа базовой сборки (≈ 2741) остаётся
 * далеко за стопом 2910, и её код к предмету не подмешивается.
 */
class LeverageLimitTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U37.1 — плечо пары равно пределу: граница включена")
    void aPairLeverageExactlyAtTheLimitPasses() {
        harness.givenAppetite(appetiteWithMaxLeverage("1", "10"));
        harness.givenPairState(pairStateWithLeverage(10));

        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U37.2 — плечо пары выше предела, акт создаёт риск: отказ кодом незаданного плеча")
    void aPairLeverageAboveTheLimitRejectsARiskCreatingAct() {
        harness.givenAppetite(appetiteWithMaxLeverage("1", "10"));
        harness.givenPairState(pairStateWithLeverage(11));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.LEVERAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U37.3 — плечо пары выше предела, акт риска не создаёт: отказа нет — плеча он площадке не пишет")
    void aPairLeverageAboveTheLimitLeavesAProtectiveActAlone() {
        harness.givenAppetite(appetiteWithMaxLeverage("1", "10"));
        harness.givenPairState(pairStateWithLeverage(11));

        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), workingContext()))).isEmpty();
    }
}
