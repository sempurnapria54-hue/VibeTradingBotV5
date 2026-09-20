package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Пол дистанции стопа — группа {@code U7} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-at-stop.json, величина {@code stopDistanceFloor};
 * правило — docs/rules/risk-policy.md §«Пол дистанции стопа»).
 *
 * <p><b>Базовая сборка</b> — U1.1: направление длинное, ставка
 * резолвится. Пол round-trip комиссии при якоре 3000 и уровне 2999
 * равен {@code 0.0005 × 5999 = 2.9995}, а знаковая дистанция — единице:
 * уровень, поставленный вплотную, срабатывает в убыток без движения
 * цены.
 */
class StopDistanceFloorTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U7.1 — знаковая дистанция заметно больше пола: отказа нет")
    void u7_1_aDistanceWellAboveTheFloorPasses() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, STOP.toPlainString()), workingContext())))
                .isEmpty();
    }

    @Test
    @DisplayName("U7.2 — дистанция меньше пола: в фактическом значении сама дистанция")
    void u7_2_aDistanceBelowTheFloorIsRejectedWithTheDistanceAsTheActualValue() {
        RiskValidationResult result = harness.validate(entryAction("10", ANCHOR, "2999"), workingContext());

        assertThat(codes(result)).containsExactly(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
        assertThat(result.getChecks().getFirst().getActualValue())
                .as("фактическое значение отказа — сама знаковая дистанция")
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("U7.3 — дистанция РАВНА полу: граница включена")
    void u7_3_aDistanceExactlyAtTheFloorPasses() {
        // Якорь 1100 и уровень 900 при ставке 0.1 дают дистанцию 200 и пол
        // 0.1 × 2000 = 200 — равенство точное, без округления.
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", "0.1"));

        assertThat(codes(harness.validate(entryAction("1", new BigDecimal("1100"), "900"), workingContext())))
                .as("на самой границе отказа нет")
                .isEmpty();
        assertThat(codes(harness.validate(entryAction("1", new BigDecimal("1100"), "901"), workingContext())))
                .as("шаг внутрь пола — и тот же вход уже отвергается")
                .containsExactly(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }

    @Test
    @DisplayName("U7.4 — уровень на прибыльной стороне: под пол такой уровень не подпадает")
    void u7_4_aNegativeDistanceIsOutsideTheFloor() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), workingContext())))
                .doesNotContain(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }

    @Test
    @DisplayName("U7.5 — знаковая дистанция ноль: проверка идёт только при положительной")
    void u7_5_aZeroDistanceIsOutsideTheFloor() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3000"), workingContext())))
                .doesNotContain(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }

    @Test
    @DisplayName("U7.6 — ставка комиссии пуста: отказ приходит своей проверкой, не этой")
    void u7_6_anUnresolvedRateSilencesTheFloorAndSpeaksWithItsOwnCode() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "1", null));

        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "2999"), workingContext())))
                .containsExactly(RiskCheckCode.FEE_RATE_UNAVAILABLE);
    }

    @Test
    @DisplayName("U7.7 — роль постановки перенос: пол — гейт уровня, а не гейт класса действия")
    void u7_7_theFloorAppliesToATransferToo() {
        assertThat(codes(harness.validate(transferAction("2999"), workingContext())))
                .containsExactly(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }

    @Test
    @DisplayName("U7.8 — якорь фактический: пол посчитан от средней цены живого эпизода")
    void u7_8_theFloorIsMeasuredFromTheActualEpisodeAnchor() {
        Deal deal = emptyDeal();
        deal.setPositions(List.of(episode("10", new BigDecimal("2000"))));

        // От фактического якоря 2000 дистанция равна единице при поле
        // 0.0005 × 3999 = 1.9995; от плановой цены 3000 она была бы 1001.
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "1999"), context(deal))))
                .containsExactly(RiskCheckCode.STOP_DISTANCE_BELOW_FLOOR);
    }
}
