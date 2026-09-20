package com.example.audit.unit.reception;

import com.example.audit.domain.model.PairLagOperands;
import com.example.audit.metrics.JournalReceptionMetrics;
import com.example.audit.util.Constants;
import com.example.testsupport.ReceptionMetricsContract;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Копия рядов экспорта в дереве {@code audit}
 * (`.claude/tests/cases/durable-reception.md`, группа `U14`, клетки
 * `U15.7`, `U16.8`).
 *
 * <p>Имена рядов — объявленное расхождение копий: у каждого метка своего
 * сервиса, и находит ряд по имени правило алерта манифеста. Их стык с
 * манифестом мерит своя проба ({@code AlertRuleContractTest}), а здесь
 * наблюдается состав и значения.
 */
class JournalReceptionMetricsTest extends ReceptionMetricsContract {

    @Override
    protected Series series(MeterRegistry registry) {
        JournalReceptionMetrics metrics = new JournalReceptionMetrics(registry);
        return new Series() {

            @Override
            public void replaceWith(List<LagOperands> operands) {
                metrics.replaceWith(own(operands));
            }

            @Override
            public void forget() {
                metrics.forget();
            }
        };
    }

    @Override
    protected String ageSeriesName() {
        return Constants.ReceptionMetrics.LAST_EVENT_AGE;
    }

    @Override
    protected String thresholdSeriesName() {
        return Constants.ReceptionMetrics.LAG_ALERT_THRESHOLD;
    }

    @Override
    protected String unconsumedSeriesName() {
        return Constants.ReceptionMetrics.UNCONSUMED_RECORDS;
    }

    @Override
    protected Class<?> receptionMetricsType() {
        return JournalReceptionMetrics.class;
    }

    private static List<PairLagOperands> own(List<LagOperands> operands) {
        List<PairLagOperands> pairs = new ArrayList<>();
        for (LagOperands operand : operands) {
            pairs.add(new PairLagOperands(operand.topic(), operand.lastEventMoment(),
                    operand.lagAlertThresholdMs(), operand.unconsumedRecords()));
        }
        return pairs;
    }
}
