package com.example.statistics.unit.reception;

import com.example.statistics.domain.model.ReceptionPairMoments;
import com.example.testsupport.ReceptionPairMomentsContract;
import java.time.OffsetDateTime;

/**
 * Копия модели моментов пары в дереве {@code statistics}
 * (`.claude/tests/cases/durable-reception.md`, группа `U5`, клетка
 * `U15.6`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз:
 * копии дословны, а сличить их на одном classpath нечем.
 */
class ReceptionPairMomentsTest extends ReceptionPairMomentsContract {

    @Override
    protected OffsetDateTime lastEventMoment(String topic, OffsetDateTime lastAccepted,
                                             OffsetDateTime observedSince) {
        return moments(topic, lastAccepted, observedSince).lastEventMoment();
    }

    @Override
    protected String topicOf(String topic, OffsetDateTime lastAccepted, OffsetDateTime observedSince) {
        return moments(topic, lastAccepted, observedSince).getTopic();
    }

    @Override
    protected Class<?> pairMomentsType() {
        return ReceptionPairMoments.class;
    }

    private ReceptionPairMoments moments(String topic, OffsetDateTime lastAccepted,
                                         OffsetDateTime observedSince) {
        return new ReceptionPairMoments(topic, lastAccepted, observedSince);
    }
}
