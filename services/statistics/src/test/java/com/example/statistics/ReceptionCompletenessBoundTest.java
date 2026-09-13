package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.statistics.domain.service.ReceptionCompletenessService;
import com.example.statistics.persistence.service.ReceptionCompletenessSource;
import com.example.statistics.persistence.service.ReceptionStateDataService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Нижняя граница полноты чисел статистики
 * (docs/rules/durable-consumer-reception.md; исполнимая форма —
 * docs/spec/durable-reception.json, {@code receptionLowerBound}).
 *
 * <p><b>Операнд у границы ОДИН, и это предмет пробы.</b> У соседа по форме
 * операндов два — самый ранний момент приёма среди уцелевших строк и
 * позднейший момент наблюдения; здесь второго нет, потому что факты не
 * чистятся ни в одном окружении и начало ряда двигать нечем
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и полнота
 * чисел статистики»).
 *
 * <p><b>Области двух вопросов не совпадают, и это проверяется
 * отдельно.</b> Максимум считается по ВСЕМ строкам состояния, включая
 * снятые с подписки, — факты снятой темы из базы не исчезли; а вопрос
 * «наблюдается ли хоть одна тема» задаётся только подписанным. Проба на
 * состоянии «строки есть, но все сняты с подписки» разводит эти области:
 * без неё чтение «строки есть — значит граница есть» выглядело бы верным.
 */
class ReceptionCompletenessBoundTest {

    private static final String GROUP = "statistics.facts";
    private static final OffsetDateTime OBSERVED =
            OffsetDateTime.of(2026, 9, 8, 10, 0, 0, 0, ZoneOffset.UTC);

    private final ReceptionStateDataService receptionStateDataService = mock(ReceptionStateDataService.class);
    private final ReceptionCompletenessSource source =
            new ReceptionCompletenessSource(receptionStateDataService);
    private final ReceptionCompletenessService service = new ReceptionCompletenessService();

    @Test
    @DisplayName("Границей служит позднейший момент наблюдения пар группы")
    void theObservationMomentIsTheBound() {
        given(OBSERVED, 2L);

        assertThat(service.lowerBound(source, GROUP))
                .as("второго операнда у границы здесь нет: ряд фактов не чистится, и двигать его начало нечем")
                .contains(OBSERVED);
    }

    @Test
    @DisplayName("Строк состояния нет ни одной — границы НЕТ, и это значение, а не ноль")
    void withoutPairsThereIsNoBound() {
        given(null, 0L);

        assertThat(service.lowerBound(source, GROUP))
                .as("группа, не наблюдающая ни одной темы, не обещает ничего")
                .isEmpty();
    }

    /**
     * Состояние достижимо: темы ушли из подписки — конфигурация сменилась,
     * производитель снят, — а строки остались, потому что их удаление
     * опустило бы границу. Наблюдения при этом не идёт ни по одной теме.
     */
    @Test
    @DisplayName("Строки есть, но все сняты с подписки — границы НЕТ")
    void unsubscribedPairsPromiseNothing() {
        given(OBSERVED, 0L);

        assertThat(service.lowerBound(source, GROUP))
                .as("наблюдаемых тем нет ни одной — обещать нечего, ровно как при отсутствии строк; "
                        + "иначе граница и предикат непрерывности сказали бы об одном состоянии разное")
                .isEmpty();
    }

    private void given(OffsetDateTime observedSince, Long subscribedPairs) {
        when(receptionStateDataService.latestObservedSince(GROUP)).thenReturn(observedSince);
        when(receptionStateDataService.countSubscribedPairs(GROUP)).thenReturn(subscribedPairs);
    }
}
