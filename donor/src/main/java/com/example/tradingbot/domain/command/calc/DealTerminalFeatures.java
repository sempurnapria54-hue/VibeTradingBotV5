package com.example.tradingbot.domain.command.calc;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import lombok.Value;

/**
 * Четыре признака отбора сделки для отчёта, посчитанные на терминальном
 * ребре. Кладутся ЦЕЛИКОМ и одной транзакцией с числом: частичная запись
 * оставила бы соседние признаки посчитанными по прежнему графу.
 *
 * <p>Пусто у признака означает «признак неприменим» — тропа закрытия без
 * входа (docs/models/domain/aggregate/Deal.md §Структура).
 */
@Value
public class DealTerminalFeatures {

    /** Торговый исход закрытия. */
    Deal.CloseOutcome closeOutcome;

    /** Исход сверки разбивки. */
    Deal.ReconciliationStatus reconciliationStatus;

    /** Полнота разбивки. */
    Deal.BreakdownCompleteness breakdownIncomplete;

    /** Почему знаменатель R пуст или непуст. */
    Deal.RiskBenchmarkAvailability riskBenchmarkAvailability;

    /** Запись закрытия добыта, а исход из её типа не выводится — предикат журнального отчёта. */
    Boolean unrecognizedCloseTypeReported;
}
