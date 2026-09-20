package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * База всех потолков — группа {@code U9} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code base}).
 *
 * <p><b>Делитель наблюдается ПОАКТНЫМ потолком, и только им.</b> Деталь
 * группы объявляет одновременный потолок стратегии в десять процентов, а
 * катастрофический множитель — трёхсоткратным: при базе 9000 ни одно
 * неравенство, кроме поактного, к границе не подходит, и его код
 * означает «делителем взята именно эта база». Риск акта базовой сборки —
 * 92.955, поактный потолок при базе 10000 равен 100, при базе 9000 — 90.
 */
class CeilingBaseTest {

    /** База, при которой риск акта базовой сборки поактный потолок ПЕРЕБИРАЕТ. */
    private static final String TIGHT_BASE = "9000";

    /** База, при которой он в поактный потолок укладывается. */
    private static final String LOOSE_BASE = "10000";

    private final RiskHarness harness = new RiskHarness();

    /**
     * Числа тенанта у группы свои: одновременный процент поднят до
     * десяти, иначе глобальная редакция одновременного потолка при базе
     * 9000 срабатывала бы вместе с поактной и предмет группы делился бы
     * на два кода.
     */
    @BeforeEach
    void givenARoomyTenantAppetite() {
        harness.givenAppetite(appetite("10", 3));
    }

    @Test
    @DisplayName("U9.1 — снимка базы у сделки нет: делителем служит живая база счёта")
    void u9_1_withoutASnapshotTheLiveAccountBaseIsTheDivider() {
        assertThat(codes(harness.validate(entryAction(), context(null, TIGHT_BASE))))
                .as("поактный потолок посчитан от живой базы 9000")
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
        assertThat(codes(harness.validate(entryAction(), context(null, LOOSE_BASE))))
                .as("та же сделка при живой базе 10000 проходит")
                .isEmpty();
    }

    @Test
    @DisplayName("U9.2 — снимок базы есть: делителем служит снимок, живая база не читается")
    void u9_2_theDealSnapshotWinsOverTheLiveAccountBase() {
        assertThat(codes(harness.validate(entryAction(), context(TIGHT_BASE, LOOSE_BASE))))
                .as("снимок 9000 связывает, хотя живая база 10000 потолок бы не перебрала")
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U9.3 — снимок равен живой базе: различить ветви нечем, и это ожидаемо")
    void u9_3_anEqualSnapshotIsIndistinguishableFromTheLiveBase() {
        assertThat(codes(harness.validate(entryAction(), context(TIGHT_BASE, TIGHT_BASE))))
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U9.4 — снимка нет, живая база пуста: ни одно неравенство не считается")
    void u9_4_anAbsentBaseStopsBeforeEveryInequality() {
        assertThat(codes(harness.validate(entryAction(), context(null, null))))
                .containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U9.5 — снимок есть, но он ноль: непозитивный снимок делителем не становится")
    void u9_5_aZeroSnapshotIsNotADivider() {
        assertThat(codes(harness.validate(entryAction(), context("0", LOOSE_BASE))))
                .containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U9.6 — снимок отрицателен: на живую базу резолв не откатывается")
    void u9_6_aNegativeSnapshotDoesNotFallBackToTheLiveBase() {
        assertThat(codes(harness.validate(entryAction(), context("-1", LOOSE_BASE))))
                .containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U9.7 — счёта в контексте нет: исключение выходит наружу на чтении правил")
    void u9_7_anAbsentAccountFailsAtTheRulesRead() {
        DealContext withoutAccount = contextBuilder(deal(BigDecimal.ZERO, null))
                .exchangeAccount(null)
                .build();

        assertThatThrownBy(() -> harness.validate(entryAction(), withoutAccount))
                .isInstanceOf(NullPointerException.class);
    }

    /** Контекст группы: снимок базы у сделки и живая база счёта названы порознь. */
    private static DealContext context(String snapshotBase, String accountBase) {
        StrategyDetail onlyPerActionIsTight = detail("1", "3", "10", "300");
        return contextBuilder(deal(BigDecimal.ZERO, RiskFixture.decimal(snapshotBase)))
                .exchangeAccount(account(accountBase))
                .strategyDetail(onlyPerActionIsTight)
                .build();
    }
}
