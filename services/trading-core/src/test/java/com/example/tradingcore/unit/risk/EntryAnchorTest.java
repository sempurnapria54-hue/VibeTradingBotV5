package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Якорь себестоимости — группа {@code U4} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/stop-distance.json, величина {@code entryAnchor}).
 *
 * <p><b>Якорь наблюдается СТОРОНОЙ уровня остановки убытка.</b> Своего
 * выхода у него нет — он операнд, — и различить «взята средняя цена
 * эпизода» от «взята плановая цена действия» можно только там, где обе
 * дают разные исходы: уровень 2900 лежит НИЖЕ плановой цены 3000 и ВЫШЕ
 * средней цены эпизода 2800, то есть при первом якоре проходит, при
 * втором отвергается. Размер входа взят вдвое меньше базового: при
 * базовом риск акта на уровне 2900 сам по себе перебирает поактный
 * потолок, и его код подмешался бы к предмету группы.
 */
class EntryAnchorTest {

    private static final BigDecimal EPISODE_AVERAGE = new BigDecimal("2800");

    private static final String BETWEEN_THE_TWO_ANCHORS = "2900";

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U4.1 — живой эпизод со средней ценой: якорь фактический, плановая не читается")
    void u4_1_aLiveEpisodeAveragePriceWinsOverThePlannedPrice() {
        Deal deal = dealWith(episode("10", EPISODE_AVERAGE));

        assertThat(codes(harness.validate(entryAction("5", ANCHOR, BETWEEN_THE_TWO_ANCHORS), context(deal))))
                .as("уровень 2900 выше фактического якоря 2800 — значит якорь взят у эпизода")
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U4.2 — живого эпизода нет: якорь — округлённая плановая цена действия")
    void u4_2_withoutALiveEpisodeThePlannedPriceIsTheAnchor() {
        assertThat(codes(harness.validate(entryAction("5", ANCHOR, BETWEEN_THE_TWO_ANCHORS), workingContext())))
                .as("уровень 2900 ниже плановой цены 3000 — сторона верна")
                .isEmpty();
    }

    @Test
    @DisplayName("U4.3 — средняя цена живого эпизода пуста: якорь отказывает вычислением (R-6)")
    void u4_3_anEpisodeWithoutAnAveragePriceMakesTheAnchorFail() {
        Deal deal = dealWith(episode("10", null));

        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), context(deal))))
                .as("ожидание из дома: ветвь, а не coalesce — уровневые проверки молчат, акт отказывает")
                .doesNotContain(RiskCheckCode.STOP_LOSS_INVALID_SIDE)
                .contains(RiskCheckCode.CALCULATED_ACTION_INVALID);
    }

    @Test
    @DisplayName("U4.4 — ни эпизода, ни цены у действия: якорь пуст, уровневые проверки молчат, акт не измерен")
    void u4_4_anEmptyAnchorSilencesEveryLevelCheck() {
        assertThat(codes(harness.validate(entryAction("10", null, "3100"), workingContext())))
                .as("уровневых кодов нет; слагаемые акта не измерены — отказ вычислением, а не ноль")
                .containsExactly(RiskCheckCode.CALCULATED_ACTION_INVALID);
    }

    @Test
    @DisplayName("U4.5 — строка эпизода активна при нулевом размере: живого эпизода нет (R-6)")
    void u4_5_anActiveRowWithZeroSizeIsNotALiveEpisode() {
        Deal deal = dealWith(episode("0", EPISODE_AVERAGE));

        assertThat(codes(harness.validate(entryAction("5", ANCHOR, BETWEEN_THE_TWO_ANCHORS), context(deal))))
                .as("ожидание из дома: оба конъюнкта hasLiveEpisode обязательны")
                .doesNotContain(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    /** Сделка базовой сборки с названным эпизодом позиции. */
    private static Deal dealWith(Position episode) {
        Deal deal = emptyDeal();
        deal.setPositions(List.of(episode));
        return deal;
    }
}
