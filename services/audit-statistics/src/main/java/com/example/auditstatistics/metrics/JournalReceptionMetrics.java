package com.example.auditstatistics.metrics;

import static java.util.Objects.nonNull;

import com.example.auditstatistics.domain.model.PairLagOperands;
import com.example.auditstatistics.util.Constants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * Ряды, которыми сервис отдаёт наблюдателю операнды алерта на лаг пары
 * «группа × тема» (docs/architecture/data-ownership.md §«Outbox и
 * доставка»).
 *
 * <p><b>Правило алерта сравнивает ДВА ЭКСПОРТИРУЕМЫХ РЯДА, а не ряд с
 * калиброванным числом.</b> Порог уезжает наружу рядом с возрастом,
 * поэтому копии калиброванного числа в тексте правила нет: срок хранения
 * темы остаётся в манифесте её владельца, доля — в конфигурации
 * потребителя, а их произведение приезжает к правилу рядом.
 *
 * <p><b>НЕИЗВЕСТНОЕ УНОСИТ РЯД, А НЕ СТАНОВИТСЯ ЧИСЛОМ.</b> Это несущее
 * свойство, а не форма записи: величина без ряда ловится вторым правилом
 * — на пропажу ряда (docs/architecture/platform.md §Наблюдаемость), — а
 * поданная числом молча решает исход. Ноль на пороге кричал бы всегда,
 * ноль на остатке гасил бы алерт, а {@code NaN} — который получился бы
 * сам, отдай мы пустое значение функцией ряда, — делает ЛОЖНЫМ всякое
 * сравнение, то есть гасит алерт тише всех
 * (docs/concept.md, П1 — умолчание не бывает благоприятным).
 *
 * <p><b>Ряды живут ровно столько, сколько живёт их измеритель.</b> Состав
 * заменяется целиком каждым тактом тика состояния приёма, а всякая тропа,
 * на которой такт не измерил, — снятый выключатель, неживой приём, отказ
 * чтения — уносит их все ({@link #forget()}). Ряд, оставшийся от прошлого
 * такта, утверждал бы измеренное там, где ничего не измерялось
 * (docs/components/ReceptionStateJob.md §«Молчание тика уносит и порог, и
 * это уже покрыто»).
 *
 * <p><b>Возраст считается в момент СЪЁМА, а не такта.</b> Ряд держит
 * durable-момент, и возраст растёт между тактами сам. Замороженный
 * тактом, он перестал бы расти вместе с умершим тиком — и алерт,
 * заведённый против бесшумной потери, молчал бы при мёртвом измерителе.
 *
 * <p><b>Пишет один — тик состояния приёма</b> (он же единственный, кто
 * обходит подписку); читает наблюдатель, снимая экспозицию. Durable-следа
 * у этих величин нет намеренно: строкой состояния приёма ни срок, ни
 * остаток не хранятся, и хранимая копия вернула бы второй носитель числа
 * (docs/spec/audit-journal.json, операнд {@code pairs}).
 */
@Component
public class JournalReceptionMetrics {

    private final MultiGauge lastEventAge;
    private final MultiGauge lagAlertThreshold;
    private final MultiGauge unconsumedRecords;

    public JournalReceptionMetrics(MeterRegistry registry) {
        this.lastEventAge = MultiGauge.builder(Constants.ReceptionMetrics.LAST_EVENT_AGE)
                .description("Возраст последнего принятого события пары «группа × тема», мс")
                .register(registry);
        this.lagAlertThreshold = MultiGauge.builder(Constants.ReceptionMetrics.LAG_ALERT_THRESHOLD)
                .description("Порог алерта на лаг пары — доля срока хранения её темы, мс")
                .register(registry);
        this.unconsumedRecords = MultiGauge.builder(Constants.ReceptionMetrics.UNCONSUMED_RECORDS)
                .description("Остаток непринятого по паре «группа × тема», в смещениях")
                .register(registry);
    }

    /**
     * Положить ряды такта, сняв ряды прошлого.
     *
     * <p><b>Состав рядов у трёх величин РАЗНЫЙ, и это не оплошность.</b>
     * Возраст есть у каждой подписанной пары — до первого приёма он
     * считается от момента наблюдения; порог есть только там, где добыт
     * срок темы; остаток — только там, где клиент его отдал. Пара, у
     * которой ряд возраста есть, а ряда порога нет, и есть третий исход
     * предиката — «измеритель не мерит»
     * (docs/spec/audit-journal.json, {@code pairLagAlertFires}).
     */
    public void replaceWith(Collection<PairLagOperands> operands) {
        lastEventAge.register(rows(operands, PairLagOperands::getLastEventMoment, this::ageMs), true);
        lagAlertThreshold.register(rows(operands, PairLagOperands::getLagAlertThresholdMs, Long::doubleValue), true);
        unconsumedRecords.register(rows(operands, PairLagOperands::getUnconsumedRecords, Long::doubleValue), true);
    }

    /** Такт не измерял: ряды пропадают целиком, а не становятся пустыми. */
    public void forget() {
        replaceWith(List.of());
    }

    /**
     * Ряды одной величины: пара, у которой величина пуста, ряда не
     * получает вовсе.
     */
    private <T> List<MultiGauge.Row<?>> rows(Collection<PairLagOperands> operands,
                                             Function<PairLagOperands, T> value,
                                             ToDoubleFunction<T> measure) {
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        for (PairLagOperands pair : operands) {
            T measured = value.apply(pair);
            if (nonNull(measured)) {
                rows.add(MultiGauge.Row.of(topicTag(pair), measured, measure));
            }
        }
        return rows;
    }

    private Tags topicTag(PairLagOperands pair) {
        return Tags.of(Constants.ReceptionMetrics.TOPIC_TAG, pair.getTopic());
    }

    /**
     * Возраст от durable-момента до сейчас.
     *
     * <p><b>Отрицательным он не отдаётся.</b> Момент происшествия ставит
     * производитель своими часами, и событие, пришедшее «из будущего» на
     * величину рассинхронизации часов, дало бы отрицательный возраст — то
     * есть заведомо ниже порога у пары, о которой ничего не известно.
     */
    private double ageMs(OffsetDateTime moment) {
        long elapsed = Duration.between(moment, OffsetDateTime.now(ZoneOffset.UTC)).toMillis();
        return Math.max(elapsed, 0L);
    }
}
