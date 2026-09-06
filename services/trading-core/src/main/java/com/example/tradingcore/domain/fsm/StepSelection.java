package com.example.tradingcore.domain.fsm;

import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import lombok.Value;

/**
 * Исход отбора шага на проходе: применимый шаг либо реакция на
 * устаревание данных, которую отбор довести до обработчика обязан
 * (docs/rules/market-data-freshness.md).
 *
 * <p><b>Два поля, а не одно.</b> Реакции {@code GRACEFUL_CLOSE} и
 * {@code KILL_SWITCH} шага не выбирают и всё же требуют хода: первая
 * уводит сделку управляемым сворачиванием, вторая — аварийным снятием
 * риска. Молчаливо приравненные к «применимого шага нет», они не
 * исполнялись бы вовсе.
 *
 * <p>Живёт только в памяти прохода — читателя за сериализацией нет.
 */
@Value
public class StepSelection {

    /** Применимый шаг; пусто — шага на этом проходе нет. */
    StrategyStep step;

    /**
     * Реакция шага на устаревание данных, выводящая сделку из штатного
     * ведения; пусто — такой реакции нет. Ждущая и блокирующая ветви сюда
     * не попадают: их исход — «шаг не исполняется», и он выражен пустым
     * шагом.
     */
    MarketDataExpiredAction escalation;

    /** Применимого шага нет и реакции тоже. */
    public static StepSelection none() {
        return new StepSelection(null, null);
    }

    /** Шаг отобран. */
    public static StepSelection of(StrategyStep step) {
        return new StepSelection(step, null);
    }

    /** Данные шага устарели, и его реакция выводит сделку из штатного ведения. */
    public static StepSelection escalated(MarketDataExpiredAction escalation) {
        return new StepSelection(null, escalation);
    }

    /** Отбор дал шаг к исполнению. */
    public Boolean hasStep() {
        return nonNull(step);
    }

    /** Отбор дал реакцию на устаревание данных. */
    public Boolean hasEscalation() {
        return nonNull(escalation);
    }
}
