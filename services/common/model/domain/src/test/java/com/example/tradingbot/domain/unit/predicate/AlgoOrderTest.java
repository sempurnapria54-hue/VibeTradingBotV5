package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneTakeProfit;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneTrailing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Условная заявка: два множества живости, уровень, покрытие — группа
 * `U10` документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/algo-order-lifecycle.json, {@code algoIsLive},
 * {@code algoIsActiveLike}; docs/spec/protection-coverage.json,
 * {@code isLive} носителя STANDALONE, {@code carriesActiveStopLevel},
 * {@code protectionStopLevel}, {@code coveredSize}).
 *
 * <p><b>Базовая сборка:</b> отдельная условная заявка со статусом, типом
 * условия, объявленным размером, сработавшим остатком и деревом условия —
 * триггером либо трейлингом.
 */
class AlgoOrderTest {

    /** Два множества различаются ровно локально созданным статусом. */
    @Test
    @DisplayName("U10.1 — локально созданный статус")
    void u10_1_createdIsLiveLocallyButNotOnTheExchange() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.CREATED, "10", "90");

        assertThat(subject.isLive()).isTrue();
        assertThat(subject.isExchangeLive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AlgoOrder.Status.class, names = {"PENDING", "ACTIVE", "PARTIALLY_COMPLETED"})
    @DisplayName("U10.2 — отправлена, активна, частично сработала — порознь")
    void u10_2_exchangeStatusesAreLiveInBothSets(AlgoOrder.Status status) {
        AlgoOrder subject = standaloneStop(1L, status, "10", "90");

        assertThat(subject.isLive()).isTrue();
        assertThat(subject.isExchangeLive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AlgoOrder.Status.class, names = {"COMPLETED", "CANCELED", "ERROR"})
    @DisplayName("U10.3 — каждый из трёх терминальных статусов порознь")
    void u10_3_terminalStatusesAreLiveInNeitherSet(AlgoOrder.Status status) {
        AlgoOrder subject = standaloneStop(1L, status, "10", "90");

        assertThat(subject.isLive()).isFalse();
        assertThat(subject.isExchangeLive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AlgoOrder.ConditionType.class, names = {"TAKE_PROFIT", "PARTIAL_TAKE_PROFIT"})
    @DisplayName("U10.4 — тип условия — тейк-профит либо частичный тейк-профит")
    void u10_4_takeProfitCarriesNoStopLevel(AlgoOrder.ConditionType conditionType) {
        AlgoOrder subject = standaloneTakeProfit(1L, AlgoOrder.Status.ACTIVE, "10");
        subject.setConditionType(conditionType);

        assertThat(subject.carriesActiveStopLevel()).isFalse();
        assertThat(subject.stopLevel()).isNull();
    }

    @Test
    @DisplayName("U10.5 — трейлинг, наблюдённая цена заполнена")
    void u10_5_anObservedTrailingCarriesTheObservedPrice() {
        AlgoOrder subject = standaloneTrailing(1L, AlgoOrder.Status.ACTIVE, "10", "88");

        assertThat(subject.carriesActiveStopLevel()).isTrue();
        assertThat(subject.stopLevel()).isEqualByComparingTo("88");
    }

    /** Цена активации на ответ не влияет. */
    @Test
    @DisplayName("U10.6 — трейлинг, наблюдённой цены нет")
    void u10_6_anUnobservedTrailingCarriesNothing() {
        AlgoOrder subject = standaloneTrailing(1L, AlgoOrder.Status.ACTIVE, "10", null);

        assertThat(subject.carriesActiveStopLevel()).isFalse();
        assertThat(subject.stopLevel()).isNull();
    }

    @Test
    @DisplayName("U10.7 — трейлинг, дерева условия нет вовсе")
    void u10_7_aTrailingWithoutConditionDoesNotThrow() {
        AlgoOrder subject = standaloneTrailing(1L, AlgoOrder.Status.ACTIVE, "10", null);
        subject.setCondition(null);

        assertThatCode(subject::stopLevel).doesNotThrowAnyException();
        assertThat(subject.carriesActiveStopLevel()).isFalse();
        assertThat(subject.stopLevel()).isNull();
    }

    /** Наблюдённая триггерная цена старше объявленной. */
    @Test
    @DisplayName("U10.8 — триггерная цена наблюдена биржей и объявлена нами")
    void u10_8_theObservedTriggerPriceWins() {
        assertThat(stopWith("100", "95").stopLevel()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("U10.9 — наблюдённой цены нет, объявленная есть")
    void u10_9_theDeclaredTriggerPriceIsTheFallback() {
        assertThat(stopWith("100", null).stopLevel()).isEqualByComparingTo("100");
    }

    /** Свойство разведения «разрешимость против числа»: два ответа расходятся. */
    @Test
    @DisplayName("U10.10 — дерева условия либо триггера нет")
    void u10_10_resolvabilityAndValueAnswerSeparately() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.setCondition(null);

        assertThat(subject.carriesActiveStopLevel()).isTrue();
        assertThat(subject.stopLevel()).isNull();
    }

    @Test
    @DisplayName("U10.11 — объявленный размер положителен, сработавший остаток пуст")
    void u10_11_theDeclaredSizeCovers() {
        assertThat(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90").coveredSize())
                .isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("U10.12 — объявленный размер положителен, часть сработала")
    void u10_12_theTriggeredRemainderIsSubtracted() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.setExternalSize(dec("4"));

        assertThat(subject.coveredSize()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("U10.13 — объявленный размер пуст")
    void u10_13_anAbsentDeclaredSizeIsNotClamped() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, null, "90");
        subject.setExternalSize(dec("4"));

        assertThat(subject.coveredSize()).isEqualByComparingTo("-4");
    }

    @Test
    @DisplayName("U10.14 — тип условия совпадает с типом дерева условия")
    void u10_14_aMatchingProjectionPassesSilently() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");

        assertThatCode(subject::validateConditionProjection).doesNotThrowAnyException();
        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
    }

    @Test
    @DisplayName("U10.15 — тип условия расходится с типом дерева")
    void u10_15_aDivergentProjectionRefuses() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.getCondition().setType(AlgoOrder.ConditionType.TAKE_PROFIT);

        assertThatThrownBy(subject::validateConditionProjection).isInstanceOf(IllegalStateException.class);
        assertThat(subject.getStatus()).isEqualTo(AlgoOrder.Status.ACTIVE);
        assertThat(subject.getConditionType()).isEqualTo(AlgoOrder.ConditionType.STOP_LOSS);
    }

    @Test
    @DisplayName("U10.16 — тип условия пуст при непустом дереве")
    void u10_16_anEmptyProjectionRefuses() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.setConditionType(null);

        assertThatThrownBy(subject::validateConditionProjection).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("U10.17 — дерева условия нет вовсе")
    void u10_17_anAbsentConditionRefuses() {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", "90");
        subject.setCondition(null);

        assertThatThrownBy(subject::validateConditionProjection).isInstanceOf(IllegalStateException.class);
    }

    private static AlgoOrder stopWith(String declared, String observed) {
        AlgoOrder subject = standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", null);
        subject.setCondition(new Condition(AlgoOrder.ConditionType.STOP_LOSS,
                new Trigger(new TriggerPrice(null, dec(declared), null, dec(observed)), null), null));
        return subject;
    }
}
