package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.deal;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.episode;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.standaloneStop;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheOf;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.position.Position;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Инвариант «ликвидация за стопом» у удерживаемой позиции — группа `U22`
 * документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/risk-limits.json, {@code heldStopBeforeLiquidation}).
 *
 * <p><b>Клетки — примеры исполнимой формы, переведённые один к одному.</b>
 * Операнды формы собираются настоящими полями: действующий уровень на всю
 * позицию — отдельной защитой транша с экспозицией
 * (docs/spec/protection-coverage.json, {@code stopCurrentLive}), живой эпизод
 * — строкой позиции в активном статусе с положительным размером, цена
 * ликвидации — полем этой строки. Подмены предикатов нет ни одной.
 */
class DealLiquidationInvariantTest {

    @Test
    @DisplayName("U22.1 — удерживаемый LONG: стоп выше цены ликвидации площадки")
    void u22_1_longStopAboveLiquidationHolds() {
        assertThat(held(StrategyTradeDirection.LONG, "2800", "10", "2750").heldStopBeforeLiquidation()).isTrue();
    }

    /** Вход инвариант прошёл; сдвинула ликвидацию площадка, а не наш акт. */
    @Test
    @DisplayName("U22.2 — удерживаемый LONG: ликвидация поднялась выше стопа")
    void u22_2_longLiquidationAboveStopBreaches() {
        assertThat(held(StrategyTradeDirection.LONG, "2800", "10", "2810").heldStopBeforeLiquidation()).isFalse();
    }

    /** Ценовой мёртвой зоны нет, но и равенство выполненным не читается. */
    @Test
    @DisplayName("U22.3 — удерживаемый LONG: стоп ровно на цене ликвидации")
    void u22_3_longStopOnLiquidationBreaches() {
        assertThat(held(StrategyTradeDirection.LONG, "2750", "10", "2750").heldStopBeforeLiquidation()).isFalse();
    }

    @Test
    @DisplayName("U22.4 — удерживаемый SHORT: стоп ниже цены ликвидации площадки")
    void u22_4_shortStopBelowLiquidationHolds() {
        assertThat(held(StrategyTradeDirection.SHORT, "3200", "10", "3250").heldStopBeforeLiquidation()).isTrue();
    }

    @Test
    @DisplayName("U22.5 — удерживаемый SHORT: ликвидация опустилась ниже стопа")
    void u22_5_shortLiquidationBelowStopBreaches() {
        assertThat(held(StrategyTradeDirection.SHORT, "3200", "10", "3190").heldStopBeforeLiquidation()).isFalse();
    }

    @Test
    @DisplayName("U22.6 — удерживаемый SHORT: стоп ровно на цене ликвидации")
    void u22_6_shortStopOnLiquidationBreaches() {
        assertThat(held(StrategyTradeDirection.SHORT, "3250", "10", "3250").heldStopBeforeLiquidation()).isFalse();
    }

    /**
     * Транш с экспозицией своего уровня не несёт: окно обязательства
     * покрытия либо его нарушение — предмет другого детектора, и второго
     * ответа тому же состоянию форма не даёт.
     */
    @Test
    @DisplayName("U22.7 — удерживаемая позиция без действующего уровня на всю позицию")
    void u22_7_noLiveStopIsUnmeasured() {
        Deal subject = deal(Deal.Status.ACTIVE, trancheOf(trancheWithExposure("10"), List.of(), List.of()));
        subject.setDirection(StrategyTradeDirection.LONG);
        subject.setPositions(List.of(liveEpisode("10", "2810")));

        assertThat(subject.heldStopBeforeLiquidation()).isNull();
    }

    /** Оценка вместо факта площадки не подставляется. */
    @Test
    @DisplayName("U22.8 — площадка цены ликвидации не называет")
    void u22_8_noLiquidationPriceIsUnmeasured() {
        assertThat(held(StrategyTradeDirection.LONG, "2800", "10", null).heldStopBeforeLiquidation()).isNull();
    }

    /**
     * Без гейта живости пара «уровень 2800, цена 2810» дала бы нарушение у
     * LONG — риск позиции, которой нет. Строка эпизода активна, но размер её
     * нулевой: живым эпизодом она не является.
     */
    @Test
    @DisplayName("U22.9 — живого эпизода нет, строка ещё несёт цену ликвидации")
    void u22_9_noLiveEpisodeIsUnmeasured() {
        assertThat(held(StrategyTradeDirection.LONG, "2800", "0", "2810").heldStopBeforeLiquidation()).isNull();
    }

    /**
     * Сделка с одним траншем, чья экспозиция покрыта отдельной защитой
     * названного уровня, и одной строкой эпизода.
     */
    private static Deal held(StrategyTradeDirection direction, String stopLevel, String episodeSize,
                             String liquidationPrice) {
        DealTranche tranche = trancheOf(trancheWithExposure("10"), List.of(),
                List.of(standaloneStop(1L, AlgoOrder.Status.ACTIVE, "10", stopLevel)));
        Deal subject = deal(Deal.Status.ACTIVE, tranche);
        subject.setDirection(direction);
        subject.setPositions(List.of(liveEpisode(episodeSize, liquidationPrice)));
        return subject;
    }

    /** Активная строка эпизода с размером и ценой ликвидации площадки. */
    private static Position liveEpisode(String size, String liquidationPrice) {
        Position episode = episode(Position.Status.ACTIVE, size, null);
        episode.setExternalLiquidationPrice(dec(liquidationPrice));
        return episode;
    }
}
