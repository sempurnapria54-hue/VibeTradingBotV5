package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auditstatistics.config.EnvironmentProperties;
import com.example.auditstatistics.config.JournalCleanupProperties;
import com.example.auditstatistics.config.ReceptionProperties;
import com.example.auditstatistics.domain.jobs.JobExecutionGuard;
import com.example.auditstatistics.domain.jobs.JournalCleanupJob;
import com.example.auditstatistics.domain.model.JournalRetentionProfile;
import com.example.auditstatistics.domain.service.JournalCleanupService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Проход чистки журнала: применимость, глубина, порционность и снятие
 * момента разрыва (docs/components/JournalCleanupJob.md).
 *
 * <p><b>Что здесь проверяется по существу.</b> Удаление необратимо, и обе
 * ошибки применимости дороги по-разному: чистка в {@code prod} уносит
 * журнал, ради которого сервис заведён; отказ чистить в
 * непроизводственном окружении оставляет первый операнд нижней границы
 * полноты без писателя. Поэтому все три состояния оси проверяются
 * поимённо, включая пустое — «ось не доехала».
 *
 * <p>Семантику самих записей (что удаляется по моменту ПРИЁМА, что момент
 * разрыва внутри границы не гаснет, что чужая группа не тронута) держит
 * SQL, и проверена она живым прогоном —
 * .claude/work/progress/phase-2-step-10-code-pass-k5.md.
 */
class JournalCleanupTest {

    private static final String GROUP = "audit-statistics.journal";
    private static final Integer DEPTH_DAYS = 14;

    private final JobExecutionGuard executionGuard = mock(JobExecutionGuard.class);
    private final JournalCleanupService cleanupService = mock(JournalCleanupService.class);
    private final JournalCleanupProperties properties = properties();
    private final EnvironmentProperties environmentProperties = new EnvironmentProperties();
    private final ReceptionProperties receptionProperties = new ReceptionProperties();
    private final JournalCleanupJob job = new JournalCleanupJob(properties,
            environmentProperties,
            receptionProperties,
            executionGuard,
            cleanupService);

    @BeforeEach
    void setUp() {
        receptionProperties.setGroupId(GROUP);
        environmentProperties.setJournalRetentionProfile(JournalRetentionProfile.REDUCED);
        when(cleanupService.deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt())).thenReturn(0);
        letTheGuardThrough();
    }

    @Test
    @DisplayName("Профиль REDUCED — проход удаляет старое и гасит момент разрыва")
    void theReducedProfileDeletesAndClearsGaps() {
        job.tick();

        verify(cleanupService).deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt());
        verify(cleanupService).clearGapsOutsideLowerBound(GROUP);
    }

    @Test
    @DisplayName("Профиль UNBOUNDED — предмета у прохода нет: не удаляется и не гасится ничего")
    void theUnboundedProfileMakesNoPass() {
        environmentProperties.setJournalRetentionProfile(JournalRetentionProfile.UNBOUNDED);

        job.tick();

        verifyNothingWritten();
    }

    @Test
    @DisplayName("Ось не доехала — проход не идёт: чистить по значению, которого никто не назначал, нельзя")
    void anAbsentProfileMakesNoPass() {
        environmentProperties.setJournalRetentionProfile(null);

        job.tick();

        verifyNothingWritten();
    }

    @Test
    @DisplayName("Выключатель снят — проход не идёт и до охраны не доходит")
    void theSwitchOffStopsTheJob() {
        properties.setEnabled(Boolean.FALSE);

        job.tick();

        verify(executionGuard, never()).runExclusively(anyString(), any(Runnable.class));
        verifyNothingWritten();
    }

    @Test
    @DisplayName("Перекрывающий проход пропускается охраной: тело идёт только через неё")
    void anOverlappingPassIsSkipped() {
        holdTheGuard();

        job.tick();

        verifyNothingWritten();
    }

    @Test
    @DisplayName("Граница удаления — глубина назад по моменту приёма, а не произвольное окно")
    void theThresholdIsTheDepthBack() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        job.tick();

        ArgumentCaptor<OffsetDateTime> threshold = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(cleanupService).deleteBatchRecordedBefore(threshold.capture(), anyInt());
        assertThat(Duration.between(before.minusDays(DEPTH_DAYS), threshold.getValue()).abs())
                .as("глубина берётся из конфигурации сервиса, а не назначается внутри исполнителя")
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("Удаление повторяется, пока порция полна, и прекращается на неполной")
    void theDeletionRepeatsWhileTheBatchIsFull() {
        AtomicInteger passes = new AtomicInteger();
        when(cleanupService.deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt()))
                .thenAnswer(invocation -> {
                    Integer batchSize = invocation.getArgument(1);
                    if (passes.incrementAndGet() < 3) {
                        return batchSize;
                    }
                    return batchSize - 1;
                });

        job.tick();

        verify(cleanupService, times(3)).deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt());
    }

    @Test
    @DisplayName("Момент разрыва гасится ПОСЛЕ удаления: граница считается по уцелевшим строкам")
    void theGapsAreClearedAfterTheDeletion() {
        job.tick();

        InOrder order = inOrder(cleanupService);
        order.verify(cleanupService).deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt());
        order.verify(cleanupService).clearGapsOutsideLowerBound(GROUP);
    }

    private void verifyNothingWritten() {
        verify(cleanupService, never()).deleteBatchRecordedBefore(any(OffsetDateTime.class), anyInt());
        verify(cleanupService, never()).clearGapsOutsideLowerBound(anyString());
    }

    /** Охрана свободна: тело прохода исполняется. */
    private void letTheGuardThrough() {
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(executionGuard).runExclusively(anyString(), any(Runnable.class));
    }

    /** Охрана занята предыдущим проходом: тело не исполняется вовсе. */
    private void holdTheGuard() {
        doAnswer(invocation -> null).when(executionGuard).runExclusively(anyString(), any(Runnable.class));
    }

    private JournalCleanupProperties properties() {
        JournalCleanupProperties configured = new JournalCleanupProperties();
        configured.setEnabled(Boolean.TRUE);
        configured.setDepthDays(DEPTH_DAYS);
        return configured;
    }
}
