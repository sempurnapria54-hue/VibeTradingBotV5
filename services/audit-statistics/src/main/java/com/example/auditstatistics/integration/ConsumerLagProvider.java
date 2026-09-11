package com.example.auditstatistics.integration;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auditstatistics.util.Constants;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Остаток непринятого по теме — второй операнд алерта на лаг пары
 * (docs/spec/audit-journal.json, {@code unconsumedRecords}).
 *
 * <p><b>Величину отдаёт КЛИЕНТ ПОТРЕБИТЕЛЯ, а не брокер</b> — так её
 * называет дом операнда. Спросить брокера значило бы завести второй
 * административный вызов там, где число уже посчитано на месте, и
 * получить остаток по <b>зафиксированному</b> смещению, тогда как предмет
 * — насколько отстаёт сам потребитель.
 *
 * <p><b>Суммируется попартиционный лаг ТЕМЫ, а не клиента.</b> Величина
 * потемная: сумма по группе маскировала бы остановку на одной теме
 * молчанием другой — тем механизмом, против которого введён второй
 * конъюнкт предиката.
 *
 * <p><b>Пусто — это «неизвестно», и оно не сводится к нулю.</b> Ряд
 * попартиционного лага у клиента появляется только после первой выборки
 * из партиции, и его нет вовсе, когда связи с брокером нет либо партиции
 * паре не назначены. Ноль означает «принимать нечего» и алерт гасит;
 * пустота гасить его не вправе — второй конъюнкт заведён против ложного
 * срабатывания, а не против срабатывания вообще
 * (docs/rules/absent-value-semantics.md).
 *
 * <p><b>{@code NaN} читается как пустота, а не как число.</b> Клиент
 * отдаёт его, пока по партиции не записано ни одного измерения; взятый
 * числом, он сделал бы ложным всякое сравнение, то есть погасил бы алерт
 * тише всех (docs/concept.md, П1).
 *
 * <p><b>Контейнеры приходят ПАРАМЕТРОМ, а не берутся из реестра.</b>
 * Подписку обходит один — тик состояния приёма, — и второго обходчика не
 * заводится: он мог бы застать другой состав контейнеров, чем тот, по
 * которому такт уже решил, жив ли приём
 * (docs/components/ReceptionStateJob.md).
 */
@Component
public class ConsumerLagProvider {

    /**
     * Остаток непринятого по теме в смещениях; пусто — клиент лага не
     * отдал.
     */
    public Optional<Long> unconsumedRecords(Collection<MessageListenerContainer> containers, String topic) {
        Long total = null;
        for (MessageListenerContainer container : containers) {
            for (Map<MetricName, ? extends Metric> byClient : container.metrics().values()) {
                for (Map.Entry<MetricName, ? extends Metric> metric : byClient.entrySet()) {
                    Long partitionLag = partitionLag(metric.getKey(), metric.getValue(), topic);
                    if (nonNull(partitionLag)) {
                        total = isNull(total) ? partitionLag : total + partitionLag;
                    }
                }
            }
        }
        return Optional.ofNullable(total);
    }

    /**
     * Лаг одной партиции названной темы; пусто — метрика не о ней либо
     * измерения по партиции ещё нет.
     */
    private Long partitionLag(MetricName name, Metric metric, String topic) {
        if (isFalse(Constants.ConsumerMetrics.RECORDS_LAG.equals(name.name()))) {
            return null;
        }
        if (isFalse(Objects.equals(topic, name.tags().get(Constants.ConsumerMetrics.TOPIC_TAG)))) {
            return null;
        }
        return measured(metric);
    }

    /** Измеренное значение метрики; пусто — не число либо число не конечное. */
    private Long measured(Metric metric) {
        Object value = metric.metricValue();
        if (isFalse(value instanceof Number)) {
            return null;
        }
        Double measured = ((Number) value).doubleValue();
        if (measured.isNaN() || measured.isInfinite()) {
            return null;
        }
        return measured.longValue();
    }
}
