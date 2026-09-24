package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.at;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketBreakoutEvent;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import com.example.tradingbot.domain.util.DomainMath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Рыночные модели: плотность ряда, структура, середина спреда, свежесть —
 * группа `U18` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/models/domain/other/CandleGroup.md §«Целостность по count
 * (density-инвариант)», docs/models/domain/other/MarketStructure.md,
 * docs/models/mapping/MarketPriceData.md,
 * docs/models/domain/core/BalanceContainer.md §Свежесть).
 *
 * <p><b>Базовая сборка:</b> группа свечей с таймфреймом, фактическими
 * границами и поддерживаемым счётчиком; структура рынка с уровнями и
 * событием пробоя; цена момента с двумя сторонами спреда; контейнер
 * баланса с моментом обновления снимка.
 *
 * <p><b>Две клетки группы не прогоняются, и это не пропуск.</b> `U18.12`
 * и `U18.14` ожидания не имеют: через сколько длительностей бара
 * наступает «новый закрытый бар» и что происходит на пустом моменте
 * «сейчас», не называет ни один носитель корпуса (находка `D-2`, звено
 * `Z1`).
 */
class MarketModelsTest {

    private static final long MINUTE = TimeFrame.ONE_MINUTE.getDurationMillis();

    /** Обе границы включены. */
    @Test
    @DisplayName("U18.1 — границы отстоят на целое число длительностей бара")
    void u18_1_bothBoundsAreCounted() {
        assertThat(group(0L, 3 * MINUTE, null).expectedCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("U18.2 — границы совпадают")
    void u18_2_coincidingBoundsGiveOne() {
        assertThat(group(0L, 0L, null).expectedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("U18.3 — любая из границ пуста")
    void u18_3_anAbsentBoundGivesZero() {
        assertThatCode(() -> group(null, 3 * MINUTE, null).expectedCount()).doesNotThrowAnyException();
        assertThat(group(null, 3 * MINUTE, null).expectedCount()).isZero();
        assertThat(group(0L, null, null).expectedCount()).isZero();
    }

    @Test
    @DisplayName("U18.4 — счётчик равен ожидаемому")
    void u18_4_theExactCountIsDense() {
        assertThat(group(0L, 3 * MINUTE, 4L).isDense()).isTrue();
    }

    /** Дефицит локализуется докачкой. */
    @Test
    @DisplayName("U18.5 — счётчик меньше ожидаемого")
    void u18_5_aDeficitIsNotDense() {
        assertThat(group(0L, 3 * MINUTE, 3L).isDense()).isFalse();
    }

    /** Сравнение на равенство, а не «не меньше». */
    @Test
    @DisplayName("U18.6 — счётчик больше ожидаемого")
    void u18_6_aSurplusIsNotDense() {
        assertThat(group(0L, 3 * MINUTE, 5L).isDense()).isFalse();
    }

    @Test
    @DisplayName("U18.7 — счётчик пуст при непустых границах")
    void u18_7_anAbsentCountReadsAsZero() {
        assertThat(group(0L, 3 * MINUTE, null).isDense()).isFalse();
    }

    /** Названное следствие формы, а не объявленное домом состояние. */
    @Test
    @DisplayName("U18.8 — обе границы пусты и счётчик пуст")
    void u18_8_zeroEqualsZero() {
        assertThat(group(null, null, null).isDense()).isTrue();
    }

    @Test
    @DisplayName("U18.9 — статус группы — готовая")
    void u18_9_theReadyStatusIsActive() {
        assertThat(groupWithStatus(CandleGroup.Status.ACTIVE).isActive()).isTrue();
    }

    /** Циклы её не ведут, требование потребителя не оживляет. */
    @ParameterizedTest
    @EnumSource(value = CandleGroup.Status.class, names = {"ERROR", "DELETED"})
    @DisplayName("U18.10 — статус — ошибка либо логическое удаление")
    void u18_10_terminalGroupStatuses(CandleGroup.Status status) {
        assertThat(groupWithStatus(status).isTerminal()).isTrue();
    }

    @Test
    @DisplayName("U18.11 — последняя загруженная свеча пуста")
    void u18_11_withoutALastBarThereIsNoNewClosedBar() {
        assertThat(group(0L, null, null).hasNewClosedBar(10 * MINUTE)).isFalse();
    }

    /** Направление ответа домом задано, порог — нет (звено `Z1`). */
    @Test
    @DisplayName("U18.13 — прошло заведомо много длительностей бара")
    void u18_13_aLongSilenceGivesANewClosedBar() {
        assertThat(group(0L, 0L, null).hasNewClosedBar(100 * MINUTE)).isTrue();
    }

    @Test
    @DisplayName("U18.15 — структура с заполненным событием пробоя")
    void u18_15_aBreakoutEventConfirmsTheBreakout() {
        MarketStructure subject = new MarketStructure();
        subject.setBreakoutEvent(new MarketBreakoutEvent());

        assertThat(subject.hasConfirmedBreakout()).isTrue();
    }

    @Test
    @DisplayName("U18.16 — структура без события")
    void u18_16_noBreakoutEventNoConfirmation() {
        assertThat(new MarketStructure().hasConfirmedBreakout()).isFalse();
    }

    @Test
    @DisplayName("U18.17 — уровней запрошенного типа два, моменты подтверждения пусты: отдан стоящий позже")
    void u18_17_theLaterListedLevelWinsWithoutMoments() {
        MarketPriceLevel later = level(MarketPriceLevel.Type.RANGE_HIGH, "130");
        MarketStructure subject = structure(level(MarketPriceLevel.Type.RANGE_HIGH, "120"), later);

        assertThat(subject.findLevel(MarketPriceLevel.Type.RANGE_HIGH)).isSameAs(later);
    }

    @Test
    @DisplayName("U18.26 — свинги одного типа: отдан последний подтверждённый, а не первый в перечне")
    void u18_26_theLatestConfirmedSwingIsReturned() {
        MarketPriceLevel oldest = level(MarketPriceLevel.Type.SWING_LOW, "2800");
        oldest.setConfirmedAt(at(1));
        MarketPriceLevel latest = level(MarketPriceLevel.Type.SWING_LOW, "2900");
        latest.setConfirmedAt(at(3));
        MarketPriceLevel listedLastButOlder = level(MarketPriceLevel.Type.SWING_LOW, "2850");
        listedLastButOlder.setConfirmedAt(at(2));
        MarketStructure subject = structure(oldest, latest, listedLastButOlder);

        assertThat(subject.findLevel(MarketPriceLevel.Type.SWING_LOW)).isSameAs(latest);
    }

    @Test
    @DisplayName("U18.27 — свинг с неизвестным моментом между известными: он не сбрасывает известный максимум")
    void u18_27_anUnknownMomentDoesNotResetTheKnownLatest() {
        MarketPriceLevel latest = level(MarketPriceLevel.Type.SWING_LOW, "2900");
        latest.setConfirmedAt(at(10));
        MarketPriceLevel unknown = level(MarketPriceLevel.Type.SWING_LOW, "2850");
        MarketPriceLevel older = level(MarketPriceLevel.Type.SWING_LOW, "2800");
        older.setConfirmedAt(at(5));
        MarketStructure subject = structure(latest, unknown, older);

        assertThat(subject.findLevel(MarketPriceLevel.Type.SWING_LOW)).isSameAs(latest);
    }

    @Test
    @DisplayName("U18.18 — уровня запрошенного типа нет")
    void u18_18_anAbsentLevelTypeGivesEmptiness() {
        MarketStructure subject = structure(level(MarketPriceLevel.Type.RANGE_HIGH, "120"));

        assertThat(subject.findLevel(MarketPriceLevel.Type.RANGE_LOW)).isNull();
    }

    /** Обхода не происходит. */
    @Test
    @DisplayName("U18.19 — перечень уровней пуст либо тип запроса пуст")
    void u18_19_anEmptyListOrAnAbsentTypeGivesEmptiness() {
        assertThat(structure().findLevel(MarketPriceLevel.Type.RANGE_LOW)).isNull();
        assertThat(structure(level(MarketPriceLevel.Type.RANGE_LOW, "100")).findLevel(null)).isNull();
    }

    @Test
    @DisplayName("U18.20 — обе стороны спреда заполнены")
    void u18_20_theMidPriceIsTheHalfSum() {
        assertThat(priceData("100", "102").midPrice()).isEqualByComparingTo("101");
    }

    /** Подстановки второй стороны нет. */
    @Test
    @DisplayName("U18.21 — одна из сторон спреда пуста")
    void u18_21_anAbsentSideEmptiesTheMidPrice() {
        assertThat(priceData("100", null).midPrice()).isNull();
        assertThat(priceData(null, "102").midPrice()).isNull();
    }

    /** Результат несёт точность доменного контекста и отказом не оборачивается. */
    @Test
    @DisplayName("U18.22 — полусумма не представима в пределах контекста")
    void u18_22_theDomainContextBoundsThePrecision() {
        String wide = "1.000000000000000000000000000000000000001";
        MarketPriceData subject = priceData(wide, wide);

        assertThatCode(subject::midPrice).doesNotThrowAnyException();
        assertThat(subject.midPrice().precision()).isLessThanOrEqualTo(DomainMath.CONTEXT.getPrecision());
    }

    @Test
    @DisplayName("U18.23 — момент обновления снимка позже поданного порога")
    void u18_23_aLaterSnapshotIsFresher() {
        assertThat(container(at(5)).isFresherThan(at(1))).isTrue();
    }

    /** Сравнение строгое. */
    @Test
    @DisplayName("U18.24 — момент равен порогу ровно")
    void u18_24_anEqualMomentIsNotFresher() {
        assertThat(container(at(5)).isFresherThan(at(5))).isFalse();
    }

    @Test
    @DisplayName("U18.25 — момент обновления пуст либо порог пуст")
    void u18_25_anAbsentMomentIsNotFresher() {
        assertThatCode(() -> container(null).isFresherThan(at(1))).doesNotThrowAnyException();
        assertThat(container(null).isFresherThan(at(1))).isFalse();
        assertThat(container(at(5)).isFresherThan(null)).isFalse();
    }

    private static CandleGroup group(Long first, Long last, Long count) {
        CandleGroup group = new CandleGroup();
        group.setTimeframe(TimeFrame.ONE_MINUTE);
        group.setActualFirstUtcMillis(first);
        group.setActualLastUtcMillis(last);
        group.setCount(count);
        return group;
    }

    private static CandleGroup groupWithStatus(CandleGroup.Status status) {
        CandleGroup group = new CandleGroup();
        group.setStatus(status);
        return group;
    }

    private static MarketStructure structure(MarketPriceLevel... levels) {
        MarketStructure structure = new MarketStructure();
        structure.setLevels(List.of(levels));
        return structure;
    }

    private static MarketPriceLevel level(MarketPriceLevel.Type type, String price) {
        MarketPriceLevel level = new MarketPriceLevel();
        level.setType(type);
        level.setPrice(dec(price));
        return level;
    }

    private static MarketPriceData priceData(String bid, String ask) {
        MarketPriceData data = new MarketPriceData();
        data.setExternalBidPrice(dec(bid));
        data.setExternalAskPrice(dec(ask));
        return data;
    }

    private static BalanceContainer container(java.time.OffsetDateTime updatedAt) {
        BalanceContainer container = new BalanceContainer();
        container.setExternalUpdatedAt(updatedAt);
        container.setExternalTotalEquity(BigDecimal.TEN);
        return container;
    }
}
