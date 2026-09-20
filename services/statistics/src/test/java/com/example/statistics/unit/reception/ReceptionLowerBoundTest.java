package com.example.statistics.unit.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statistics.domain.service.ReceptionCompletenessService;
import com.example.statistics.persistence.service.ReceptionCompletenessSource;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Нижняя граница полноты статистики: один операнд и та же арифметика —
 * группа `U2` документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Операнд ОДИН, и это следствие двух принятых решений, а не
 * упрощение</b> (docs/models/domain/other/StatisticsFact.md §«Состояние
 * приёма и полнота чисел статистики»): факты не чистятся ни в одном
 * окружении, и второй оси времени факт не хранит вовсе. Арифметика при
 * этом та же, что у соседа по форме на пустом ряде следствий, и отдельной
 * ветви исполнимая форма не заводит.
 *
 * <p>Общие с журналом ветви живут контрактом
 * {@code ReceptionCompletenessContract}.
 */
class ReceptionLowerBoundTest {

    private static final String GROUP = "statistics-facts";

    private static final OffsetDateTime OBSERVED =
            OffsetDateTime.of(2026, 9, 10, 3, 0, 0, 0, ZoneOffset.UTC);

    private final ReceptionCompletenessService service = new ReceptionCompletenessService();

    @Test
    @DisplayName("U2.1 — подписано 2 пары: граница равна позднейшему моменту наблюдения")
    void u2_1_theBoundIsTheLatestObservation() {
        assertThat(lowerBound(2L, OBSERVED)).contains(OBSERVED);
    }

    @Test
    @DisplayName("U2.2 — подписанных пар ноль: границы нет, о моменте не спрошено")
    void u2_2_anEmptyDomainYieldsNoBoundAndAsksNothingElse() {
        ReceptionCompletenessSource source = source(0L, OBSERVED);

        assertThat(service.lowerBound(source, GROUP)).isEmpty();
        verify(source, never()).latestObservedSince(anyString());
    }

    @Test
    @DisplayName("U2.3 — строки есть, все сняты с подписки: границы нет")
    void u2_3_unsubscribedRowsPromiseNothing() {
        assertThat(lowerBound(0L, OBSERVED))
                .as("та же ветвь, что у соседа по форме")
                .isEmpty();
    }

    @Test
    @DisplayName("U2.4 — максимум наблюдения у снятой строки: отдаётся как получен")
    void u2_4_theFoldDoesNotFilterRowsOfItsOwn() {
        assertThat(lowerBound(1L, OBSERVED))
                .as("область максимума — все строки состояния, и мерит её запрос")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U2.5 — ряда следствий нет: исход совпадает с журнальным")
    void u2_5_anEmptyConsequenceSeriesGivesTheSameOutcomeAsTheJournalCopy() {
        assertThat(lowerBound(1L, OBSERVED))
                .as("арифметика на пустом ряде следствий одна, и отдельной ветви для неё нет")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("U2.6 — метода чтения начала ряда следствий у источника нет вовсе")
    void u2_6_theSourceHasNoConsequenceSeriesStartAtAll() {
        List<String> methods = new ArrayList<>();
        for (Method method : ReceptionCompletenessSource.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }

        assertThat(methods)
                .as("операнд у границы один: чистки у фактов не существует, и начало ряда двигать нечем")
                .containsExactlyInAnyOrder("latestObservedSince", "countSubscribedPairs",
                        "countSubscribedPairsWithBreak");
    }

    // --- оснастка ---------------------------------------------------------

    private Optional<OffsetDateTime> lowerBound(Long subscribedPairs, OffsetDateTime observed) {
        return service.lowerBound(source(subscribedPairs, observed), GROUP);
    }

    private ReceptionCompletenessSource source(Long subscribedPairs, OffsetDateTime observed) {
        ReceptionCompletenessSource source = mock(ReceptionCompletenessSource.class);
        when(source.countSubscribedPairs(GROUP)).thenReturn(subscribedPairs);
        when(source.latestObservedSince(GROUP)).thenReturn(observed);
        return source;
    }
}
