package com.example.audit.unit.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.audit.domain.model.JournalCompleteness;
import com.example.audit.domain.service.JournalCompletenessService;
import com.example.audit.persistence.service.JournalCompletenessSource;
import com.example.testsupport.ReceptionCompletenessContract;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Копия свёртки полноты в дереве {@code audit}
 * (`.claude/tests/cases/durable-reception.md`, группы `U3`, `U4`, `U6`,
 * клетки `U15.9`, `U16.4`, `U16.5`).
 *
 * <p>Ожидания живут в контракте общего артефакта и объявлены один раз:
 * сличить две копии на одном classpath нечем, поэтому каждое дерево
 * прогоняет одно и то же ожидание своей копией. Ветви нижней границы —
 * объявленное расхождение, и они здесь свои: {@code JournalLowerBoundTest}.
 */
class JournalCompletenessTest extends ReceptionCompletenessContract {

    private final JournalCompletenessService service = new JournalCompletenessService();

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
        JournalCompleteness completeness = service.completeness(sourceOf(answers), consumerGroup, staleBefore);
        return new Completeness(completeness.getLowerBound(), completeness.getContinuityClaimable());
    }

    @Override
    protected Class<?> completenessServiceType() {
        return JournalCompletenessService.class;
    }

    @Override
    protected Class<?> completenessSourceType() {
        return JournalCompletenessSource.class;
    }

    @Override
    protected Class<?> completenessFormType() {
        return JournalCompleteness.class;
    }

    /**
     * Источник операндов — граница персистентности, и подменяется ровно
     * она: своя проверка у неё есть, а поднимать базу ради четырёх чисел
     * значило бы мерить не то (.claude/rules/codestyle.md §«Тесты доменных
     * моделей»).
     */
    private JournalCompletenessSource sourceOf(SourceAnswers answers) {
        JournalCompletenessSource source = mock(JournalCompletenessSource.class);
        when(source.countSubscribedPairs(anyString()))
                .thenAnswer(invocation -> answers.countSubscribedPairs(invocation.getArgument(0)));
        when(source.countSubscribedPairsWithBreak(anyString(), any()))
                .thenAnswer(invocation -> answers.countSubscribedPairsWithBreak(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(source.latestObservedSince(anyString()))
                .thenAnswer(invocation -> answers.latestObservedSince(invocation.getArgument(0)));
        when(source.earliestRecordedAt()).thenAnswer(invocation -> answers.earliestRecordedAt());
        return source;
    }
}
