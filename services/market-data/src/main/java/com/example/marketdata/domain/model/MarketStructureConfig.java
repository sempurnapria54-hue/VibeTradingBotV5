package com.example.marketdata.domain.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заказанная идентичность вычисления структуры рынка: таймфрейм,
 * параметры окна и идентичности её входов.
 *
 * <p><b>Идентичности входов входят в идентичность результата.</b> Две
 * структуры с одинаковым окном, но разными ER/ATR — разные вычисления, и
 * без входов в ключе последняя записанная затирала бы чужую
 * (docs/models/domain/other/MarketStructure.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class MarketStructureConfig {

    /** Внутренний идентификатор идентичности. */
    private Long id;

    /** Межсервисный идентификатор: им идентичность называют потребители. */
    private String internalId;

    /** Таймфрейм серии, по которой считается структура. */
    private TimeFrame timeframe;

    /** Параметры расчёта окна и уровней. */
    private MarketStructureParams params;

    /** Идентичность входного ER; пусто — вход не объявлен. */
    private Long efficiencyRatioConfigId;

    /** Идентичность входного ATR; пусто — вход не объявлен. */
    private Long atrConfigId;

    /**
     * Дефекты параметров, на которых вычисление не определено:
     * неположительная глубина окна либо поиска свингов. Пусто — параметры
     * годны.
     *
     * <p><b>Пустота глубины дефектом здесь не считается, и это названо, а не
     * забыто.</b> Пустые числа структуры дают объявленный консервативный
     * исход «неизвестно», а пустое окно — пропуск идентичности с записью в
     * журнал; неположительная глубина объявленного исхода не имеет вовсе —
     * нулевой поиск свингов делает пивотом каждый бар, отрицательный роняет
     * расчёт, а у окна такая глубина отказывает чтению ряда
     * (docs/architecture/market-data-collection.md §«Как потребность доходит
     * до сбора»).
     */
    public List<String> parameterDefects() {
        List<String> defects = new ArrayList<>();
        if (isNull(params)) {
            defects.add("params are required");
            return defects;
        }
        rejectNonPositive(defects, "lookbackBars", params.getLookbackBars());
        rejectNonPositive(defects, "swingLookbackBars", params.getSwingLookbackBars());
        return defects;
    }

    private static void rejectNonPositive(List<String> defects, String name, Integer depth) {
        if (nonNull(depth) && depth <= 0) {
            defects.add(name + " must be positive");
        }
    }
}
