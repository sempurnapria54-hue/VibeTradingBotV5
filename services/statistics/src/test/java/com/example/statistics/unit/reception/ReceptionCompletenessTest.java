package com.example.statistics.unit.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statistics.domain.model.ReceptionCompleteness;
import com.example.statistics.domain.service.ReceptionCompletenessService;
import com.example.statistics.persistence.service.ReceptionCompletenessSource;
import com.example.testsupport.ReceptionCompletenessContract;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Копия свёртки полноты в дереве {@code statistics}
 * (`.claude/tests/cases/durable-reception.md`, группы `U3`, `U4`, `U6`,
 * клетки `U15.9`, `U16.4`, `U16.5`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз.
 * Ветви нижней границы — объявленное расхождение: у статистики операнд
 * один, и его кейсы здесь свои ({@code ReceptionLowerBoundTest}).
 *
 * <p><b>Момента приёма следствия источник не отдаёт вовсе</b> — метода
 * чтения начала ряда следствий у него нет, и порт его не подставляет.
 */
class ReceptionCompletenessTest extends ReceptionCompletenessContract {

    private final ReceptionCompletenessService service = new ReceptionCompletenessService();

    @Override
    protected Optional<OffsetDateTime> lowerBound(SourceAnswers answers, String consumerGroup) {
        return service.lowerBound(sourceOf(answers), consumerGroup);
    }

    @Override
    protected Boolean continuityClaimable(SourceAnswers answers, String consumerGroup,
                                          OffsetDateTime staleBefore) {
        return service.continuityClaimable(sourceOf(answers), consumerGroup, staleBefore);
    }

    @Override
    protected Completeness completeness(SourceAnswers answers, String consumerGroup,
                                        OffsetDateTime staleBefore) {
        ReceptionCompleteness completeness = service.completeness(sourceOf(answers), consumerGroup, staleBefore);
        return new Completeness(completeness.getLowerBound(), completeness.getContinuityClaimable());
    }

    @Override
    protected Class<?> completenessServiceType() {
        return ReceptionCompletenessService.class;
    }

    @Override
    protected Class<?> completenessSourceType() {
        return ReceptionCompletenessSource.class;
    }

    @Override
    protected Class<?> completenessFormType() {
        return ReceptionCompleteness.class;
    }

    private ReceptionCompletenessSource sourceOf(SourceAnswers answers) {
        ReceptionCompletenessSource source = mock(ReceptionCompletenessSource.class);
        when(source.countSubscribedPairs(anyString()))
                .thenAnswer(invocation -> answers.countSubscribedPairs(invocation.getArgument(0)));
        when(source.countSubscribedPairsWithBreak(anyString(), any()))
                .thenAnswer(invocation -> answers.countSubscribedPairsWithBreak(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(source.latestObservedSince(anyString()))
                .thenAnswer(invocation -> answers.latestObservedSince(invocation.getArgument(0)));
        return source;
    }
}
