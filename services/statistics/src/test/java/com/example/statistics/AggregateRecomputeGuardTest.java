package com.example.statistics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statistics.domain.service.AggregateRecomputeService;
import com.example.statistics.domain.service.AggregateWriteService;
import com.example.statistics.persistence.repository.DealGrainRow;
import com.example.statistics.persistence.repository.IncidentGrainRow;
import com.example.statistics.persistence.service.AggregateSourceDataService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Охрана отбора суток: проход пишет сутки зерна только тогда, когда ряд
 * фактов этого зерна их покрывает
 * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}).
 *
 * <p><b>Что здесь проверяется по существу.</b> Ошибка отбора не видна
 * ничем: сутки, начавшиеся раньше начала ряда, покрыты им частично —
 * собранная по ним строка ложится по ключу зерна поверх верной и получает
 * <b>свежий</b> момент сборки. Человек увидит число, помеченное «собрано
 * только что», и оно будет тише и меньше действительного.
 *
 * <p><b>Охрана своя у каждого зерна, и это предмет двух проб ниже.</b> Ряды
 * наполняются независимо: общая охрана запретила бы пересчёт суток,
 * покрытых одним зерном и не покрытых другим.
 */
class AggregateRecomputeGuardTest {

    private static final LocalDate BUCKET = LocalDate.of(2026, 9, 10);
    private static final OffsetDateTime BUCKET_START =
            BUCKET.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    private static final OffsetDateTime ASSEMBLED_AT =
            OffsetDateTime.of(2026, 9, 12, 10, 0, 0, 0, ZoneOffset.UTC);

    private final AggregateSourceDataService source = mock(AggregateSourceDataService.class);
    private final AggregateWriteService writeService = mock(AggregateWriteService.class);
    private final AggregateRecomputeService service = new AggregateRecomputeService(source, writeService);

    private final DealGrainRow dealRow = mock(DealGrainRow.class);
    private final IncidentGrainRow incidentRow = mock(IncidentGrainRow.class);

    @Test
    @DisplayName("Оба ряда покрывают сутки — собираются и пишутся оба зерна")
    void bothGrainsAreCollectedWhenBothSeriesCoverTheBucket() {
        when(source.earliestDealFactMoment()).thenReturn(BUCKET_START.minusDays(1));
        when(source.earliestIncidentFactMoment()).thenReturn(BUCKET_START.minusDays(1));
        when(source.collectDealGrain(any(), any())).thenReturn(List.of(dealRow));
        when(source.collectIncidentGrain(any(), any())).thenReturn(List.of(incidentRow));

        service.recomputeDay(BUCKET, ASSEMBLED_AT);

        verify(writeService).writeDay(eq(BUCKET), eq(List.of(dealRow)), eq(List.of(incidentRow)),
                eq(ASSEMBLED_AT));
    }

    @Test
    @DisplayName("Сутки начались раньше начала ряда зерна — это зерно не собирается вовсе")
    void aGrainWhoseSeriesStartsLaterIsNotCollected() {
        when(source.earliestDealFactMoment()).thenReturn(BUCKET_START.plusHours(3));
        when(source.earliestIncidentFactMoment()).thenReturn(BUCKET_START.minusDays(1));
        when(source.collectIncidentGrain(any(), any())).thenReturn(List.of(incidentRow));

        service.recomputeDay(BUCKET, ASSEMBLED_AT);

        verify(source, never()).collectDealGrain(any(), any());
        ArgumentCaptor<List<DealGrainRow>> deals = captor();
        verify(writeService).writeDay(eq(BUCKET), deals.capture(), eq(List.of(incidentRow)),
                eq(ASSEMBLED_AT));
        org.assertj.core.api.Assertions.assertThat(deals.getValue())
                .as("непокрытое зерно отдаёт пустой перечень: прежние числа остаются на своём моменте сборки")
                .isEmpty();
    }

    @Test
    @DisplayName("Сутки начались РОВНО в момент первого факта — ряд покрывает их целиком")
    void aBucketStartingExactlyAtTheSeriesStartIsCollected() {
        when(source.earliestDealFactMoment()).thenReturn(BUCKET_START);
        when(source.earliestIncidentFactMoment()).thenReturn(BUCKET_START);
        when(source.collectDealGrain(any(), any())).thenReturn(List.of(dealRow));
        when(source.collectIncidentGrain(any(), any())).thenReturn(List.of(incidentRow));

        service.recomputeDay(BUCKET, ASSEMBLED_AT);

        verify(source).collectDealGrain(any(), any());
        verify(source).collectIncidentGrain(any(), any());
    }

    @Test
    @DisplayName("Оба ряда пусты — не пишется ничего: группировать нечего")
    void emptySeriesWriteNothing() {
        when(source.earliestDealFactMoment()).thenReturn(null);
        when(source.earliestIncidentFactMoment()).thenReturn(null);

        service.recomputeDay(BUCKET, ASSEMBLED_AT);

        verify(source, never()).collectDealGrain(any(), any());
        verify(source, never()).collectIncidentGrain(any(), any());
        verify(writeService, never()).writeDay(any(), anyList(), anyList(), any());
    }

    /**
     * Сутки, у которых оба зерна дали пустую группировку, транзакции не
     * открывают вовсе: писать нечего, а пустая транзакция на каждые сутки
     * окна — работа без исхода.
     */
    @Test
    @DisplayName("Покрытые сутки без строк ни одного зерна транзакции не открывают")
    void aCoveredBucketWithoutRowsOpensNoTransaction() {
        when(source.earliestDealFactMoment()).thenReturn(BUCKET_START.minusDays(1));
        when(source.earliestIncidentFactMoment()).thenReturn(BUCKET_START.minusDays(1));
        when(source.collectDealGrain(any(), any())).thenReturn(List.of());
        when(source.collectIncidentGrain(any(), any())).thenReturn(List.of());

        service.recomputeDay(BUCKET, ASSEMBLED_AT);

        verify(writeService, never()).writeDay(any(), anyList(), anyList(), any());
    }

    @SuppressWarnings("unchecked")
    private <T> ArgumentCaptor<List<T>> captor() {
        return ArgumentCaptor.forClass((Class<List<T>>) (Class<?>) List.class);
    }
}
