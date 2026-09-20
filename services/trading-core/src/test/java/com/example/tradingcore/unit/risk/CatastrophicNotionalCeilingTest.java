package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.appetite;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.weakeningAction;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Катастрофический потолок нотинала — группа {@code U15} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code withinDealNotional}; сам
 * потолок — там же, величина {@code catastrophicLossCeiling}).
 *
 * <p><b>Базовая сборка</b> — U13.1: эпизод в десять контрактов по цене
 * 3000 даёт нотинал эпизода 3000, вход на десять контрактов — нотинал
 * акта 3000. Потолок равен {@code 10 % × 10000 × множитель}, то есть
 * тысяче, умноженной на множитель: клетка задаёт его так, чтобы граница
 * проходила ровно по проверяемому числу. Процент риск-аппетита поднят до
 * десяти намеренно — при единице глобальная редакция одновременного
 * потолка срабатывала бы на каждой клетке группы.
 *
 * <p>Ноги группы несут ЗАЯВЛЕННЫЙ РИСК ноль: предмет здесь нотинал, а
 * ненулевая доля риска подмешивала бы к нему одновременный потолок.
 */
class CatastrophicNotionalCeilingTest {

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenARoomySimultaneousCeiling() {
        harness.givenAppetite(appetite("10", 3));
    }

    @Test
    @DisplayName("U15.1 — экспозиция плюс нотинал акта ниже потолка: отказа нет")
    void u15_1_aSumWellWithinTheCeilingPasses() {
        assertThat(codes(harness.validate(entryAction(), liveContext("300")))).isEmpty();
    }

    @Test
    @DisplayName("U15.2 — сумма РАВНА потолку: граница включена")
    void u15_2_aSumExactlyAtTheCeilingPasses() {
        assertThat(codes(harness.validate(entryAction(), liveContext("6"))))
                .as("3000 + 3000 = 6000 ровно в потолок 1000 × 6")
                .isEmpty();
    }

    @Test
    @DisplayName("U15.3 — сумма выше потолка: в фактическом значении сама сумма")
    void u15_3_aSumOverTheCeilingIsRejectedWithTheSumAsTheActualValue() {
        RiskValidationResult result = harness.validate(entryAction(), liveContext("5.999"));

        assertThat(codes(result)).containsExactly(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo("6000");
    }

    @Test
    @DisplayName("U15.4 — множитель не объявлен: неравенство не считается")
    void u15_4_anUndeclaredMultiplierStopsTheInequality() {
        assertThat(codes(harness.validate(entryAction(), liveContext(null))))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U15.5 — первый вход сделки: операнд — только нотинал акта")
    void u15_5_theFirstEntryCountsOnlyItsOwnNotional() {
        assertThat(codes(harness.validate(entryAction(), emptyContext("3"))))
                .as("нотинал акта 3000 ровно в потолок 1000 × 3")
                .isEmpty();
        assertThat(codes(harness.validate(entryAction(), emptyContext("2.999"))))
                .as("потолок на волос ниже — и тот же вход его перебирает")
                .containsExactly(RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U15.6 — живая нога налита наполовину: в экспозицию входит неисполненная доля")
    void u15_6_aHalfFilledLegEntersByItsUnfilledShare() {
        DealContext dealContext = contextWith(episode("10", ANCHOR), List.of(freeLeg("100", "50")), "21");

        assertThat(codes(harness.validate(entryAction(), dealContext)))
                .as("15000 + 3000 + 3000 = 21000; полный размер ноги дал бы 36000")
                .isEmpty();
    }

    @Test
    @DisplayName("U15.7 — нога исполнена целиком: вклад ноль, объём уехал в нотинал эпизода")
    void u15_7_aFullyFilledLegIsNotCountedTwice() {
        DealContext dealContext = contextWith(episode("10", ANCHOR), List.of(freeLeg("100", "100")), "6");

        assertThat(codes(harness.validate(entryAction(), dealContext)))
                .as("3000 + 3000 = 6000 ровно в потолок; двойного счёта нет")
                .isEmpty();
    }

    @Test
    @DisplayName("U15.8 — эпизод жив, якорь пуст: нотинал эпизода не входит, нотинал ног входит")
    void u15_8_anEmptyAnchorDropsTheEpisodeNotionalOnly() {
        DealContext exact = contextWith(episode("10", null), List.of(freeLeg("100", "50")), "15");
        DealContext aHairBelow = contextWith(episode("10", null), List.of(freeLeg("100", "50")), "14.999");

        assertThat(codes(harness.validate(weakeningAction(), exact)))
                .as("нотинал ног 15000 ровно в потолок; нотинала эпизода без якоря нет")
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
        assertThat(codes(harness.validate(weakeningAction(), aHairBelow)))
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U15.9 — стоимость контракта пуста: экспозиция сделки ноль")
    void u15_9_anAbsentContractValueZeroesTheDealExposure() {
        harness.givenRules(rules(null, "1", "1", "0.0005"));
        DealContext dealContext = contextWith(episode("10", ANCHOR), List.of(freeLeg("100", "50")), "0.00001");

        assertThat(codes(harness.validate(weakeningAction(), dealContext)))
                .as("потолок в одну сотую не перебирается: складывать нечего")
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U15.10 — плановая цена живой ноги пуста: вклад этой ноги ноль, остальные складываются")
    void u15_10_aLegWithoutAPlannedPriceContributesNothing() {
        Order priceless = freeLeg("100", "50");
        priceless.setPlannedEntryPrice(null);
        DealContext dealContext = contextWith(episode("10", ANCHOR),
                List.of(priceless, freeLeg("100", "50")), "18");

        assertThat(codes(harness.validate(weakeningAction(), dealContext)))
                .as("15000 + 3000 = 18000 ровно в потолок; вторая нога добавила бы ещё 15000")
                .isEmpty();
    }

    /** Живая нога без заявленного риска: предмет группы — нотинал, а не риск. */
    private static Order freeLeg(String plannedSize, String filled) {
        return entryLeg(Order.Status.ACTIVE, "0", plannedSize, filled);
    }

    /** Контекст базовой сборки группы с названным катастрофическим множителем. */
    private static DealContext liveContext(String catastrophicMultiplier) {
        return contextWith(episode("10", ANCHOR), List.of(), catastrophicMultiplier);
    }

    /** Контекст без эпизода и без ног: первый вход сделки. */
    private static DealContext emptyContext(String catastrophicMultiplier) {
        return contextBuilder(emptyDeal())
                .strategyDetail(detail("10", "3", "10", catastrophicMultiplier))
                .build();
    }

    /** Контекст с названными эпизодом, живыми ногами и катастрофическим множителем. */
    private static DealContext contextWith(Position episode, List<Order> legs, String catastrophicMultiplier) {
        Deal deal = deal(BigDecimal.ZERO, null);
        deal.setPositions(List.of(episode));
        deal.setTranches(List.of(tranche(legs, List.of(protection(STOP.toPlainString())))));
        return contextBuilder(deal)
                .strategyDetail(detail("10", "3", "10", catastrophicMultiplier))
                .build();
    }
}
