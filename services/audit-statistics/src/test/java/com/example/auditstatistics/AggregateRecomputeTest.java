package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.AggregateRecomputeProperties;
import com.example.auditstatistics.domain.jobs.AggregateRecomputeJob;
import com.example.auditstatistics.domain.jobs.JobExecutionGuard;
import com.example.auditstatistics.domain.service.AggregateRecomputeService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Проход пересчёта агрегатов: окно, порционность и отбор суток по
 * уцелевшему журналу (docs/rules/statistics-aggregates.md §«Пересчёт —
 * проекция, а не накопитель»).
 *
 * <p><b>Что здесь проверяется по существу.</b> Ошибка отбора суток не
 * видна ничем: строка суток, часть строк которых чистка уже унесла,
 * собирается группой меньшего состава, ложится по ключу зерна поверх
 * верной и получает <b>свежий</b> момент сборки. Человек увидит число,
 * помеченное «собрано только что», и оно будет тише и меньше
 * действительного.
 *
 * <p>Семантику самих запросов — что считает группировка и по каким ключам
 * — держит SQL, и проверена она живым прогоном:
 * .claude/work/history/2026-09-11-phase-2-step-10-audit-statistics/phase-2-step-10-code-pass-k6.md.
 */
class AggregateRecomputeTest {

    private static final Integer WINDOW_DAYS = 3;
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final OffsetDateTime LONG_AGO =
            OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final JobExecutionGuard executionGuard = mock(JobExecutionGuard.class);
    private final AggregateRecomputeService recomputeService = mock(AggregateRecomputeService.class);
    private final AggregateRecomputeProperties properties = properties();
    private final AggregateRecomputeJob job = new AggregateRecomputeJob(properties,
            executionGuard,
            recomputeService);

    @BeforeEach
    void setUp() {
        when(recomputeService.earliestJournalMoment()).thenReturn(LONG_AGO);
        letTheGuardThrough();
    }

    @Test
    @DisplayName("Окно проходится посуточными порциями, от свежих суток к старым")
    void theWindowIsWalkedBucketByBucketFromTheFreshest() {
        job.tick();

        InOrder order = inOrder(recomputeService);
        order.verify(recomputeService).recomputeDay(eq(TODAY), any(OffsetDateTime.class));
        order.verify(recomputeService).recomputeDay(eq(TODAY.minusDays(1)), any(OffsetDateTime.class));
        order.verify(recomputeService).recomputeDay(eq(TODAY.minusDays(2)), any(OffsetDateTime.class));
        verify(recomputeService, times(WINDOW_DAYS)).recomputeDay(any(LocalDate.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Ширина окна — величина конфигурации: сколько суток назначено, столько порций и идёт")
    void theWindowWidthComesFromTheConfiguration() {
        properties.setWindowDays(1);

        job.tick();

        verify(recomputeService, times(1)).recomputeDay(any(LocalDate.class), any(OffsetDateTime.class));
        verify(recomputeService).recomputeDay(eq(TODAY), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Сутки, начавшиеся раньше самой ранней уцелевшей строки, не пересчитываются")
    void aBucketStartingBeforeTheSurvivingJournalIsNotRecomputed() {
        when(recomputeService.earliestJournalMoment())
                .thenReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());

        job.tick();

        verify(recomputeService).recomputeDay(eq(TODAY), any(OffsetDateTime.class));
        verify(recomputeService, never()).recomputeDay(eq(TODAY.minusDays(1)), any(OffsetDateTime.class));
        verify(recomputeService, never()).recomputeDay(eq(TODAY.minusDays(2)), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Сутки, начавшиеся РОВНО в момент операнда, пересчитываются: сравнение строгое")
    void aBucketStartingExactlyAtTheOperandIsRecomputed() {
        when(recomputeService.earliestJournalMoment())
                .thenReturn(TODAY.minusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());

        job.tick();

        verify(recomputeService).recomputeDay(eq(TODAY.minusDays(1)), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Пустой журнал — не пересчитывается ничего: группировать нечего")
    void anEmptyJournalRecomputesNothing() {
        when(recomputeService.earliestJournalMoment()).thenReturn(null);

        job.tick();

        verify(recomputeService, never()).recomputeDay(any(LocalDate.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Операнд отбора читается на КАЖДУЮ порцию, а не один раз на проход")
    void theOperandIsReadForEveryBucket() {
        job.tick();

        verify(recomputeService, times(WINDOW_DAYS)).earliestJournalMoment();
    }

    @Test
    @DisplayName("Обрыв между порциями оставляет пересчитанными ровно пройденные сутки")
    void aBreakBetweenBucketsLeavesThePassedBucketsRecomputed() {
        doThrow(new IllegalStateException("порция оборвалась"))
                .when(recomputeService).recomputeDay(eq(TODAY.minusDays(1)), any(OffsetDateTime.class));

        assertThatThrownBy(job::tick)
                .as("отказ порции не глушится: он останавливает проход, а следующий тик берёт окно заново")
                .isInstanceOf(IllegalStateException.class);

        verify(recomputeService).recomputeDay(eq(TODAY), any(OffsetDateTime.class));
        verify(recomputeService, never()).recomputeDay(eq(TODAY.minusDays(2)), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Момент сборки — момент прохода, и он едет в каждую порцию")
    void theAssembledMomentIsTheMomentOfThePass() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofSeconds(1));

        job.tick();

        ArgumentCaptor<OffsetDateTime> assembled = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(recomputeService, times(WINDOW_DAYS)).recomputeDay(any(LocalDate.class), assembled.capture());
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(1));
        assertThat(assembled.getAllValues())
                .as("число, показанное без своей актуальности, читается как «сейчас»")
                .allSatisfy(moment -> assertThat(moment).isAfter(before).isBefore(after));
    }

    @Test
    @DisplayName("Выключатель снят — проход не идёт и до охраны не доходит")
    void theSwitchOffStopsTheJob() {
        properties.setEnabled(Boolean.FALSE);

        job.tick();

        verify(executionGuard, never()).runExclusively(anyString(), any(Runnable.class));
        verify(recomputeService, never()).recomputeDay(any(LocalDate.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Перекрывающий проход пропускается охраной: тело идёт только через неё")
    void anOverlappingPassIsSkipped() {
        holdTheGuard();

        job.tick();

        verify(recomputeService, never()).earliestJournalMoment();
        verify(recomputeService, never()).recomputeDay(any(LocalDate.class), any(OffsetDateTime.class));
    }

    private void letTheGuardThrough() {
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(executionGuard).runExclusively(anyString(), any(Runnable.class));
    }

    private void holdTheGuard() {
        doAnswer(invocation -> null).when(executionGuard).runExclusively(anyString(), any(Runnable.class));
    }

    private AggregateRecomputeProperties properties() {
        AggregateRecomputeProperties created = new AggregateRecomputeProperties();
        created.setEnabled(Boolean.TRUE);
        created.setWindowDays(WINDOW_DAYS);
        return created;
    }
}
