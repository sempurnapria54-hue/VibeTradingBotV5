package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statistics.config.StatisticsPersistenceConfig;
import com.example.statistics.domain.service.AggregateRecomputeService;
import com.example.statistics.domain.service.AggregateWriteService;
import com.example.statistics.persistence.repository.DealGrainRow;
import com.example.statistics.persistence.repository.IncidentGrainRow;
import com.example.statistics.persistence.service.AggregateSourceDataService;
import com.example.statistics.persistence.service.DealAggregateDataService;
import com.example.statistics.persistence.service.IncidentAggregateDataService;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Границы порции пересчёта: одни сутки, обе таблицы, одна транзакция
 * названного менеджера (docs/rules/statistics-aggregates.md §«Пересчёт —
 * проекция, а не накопитель»).
 *
 * <p><b>Почему это отдельная проба.</b> Половина записанной порции
 * показала бы человеку сутки, у которых сделочные числа новые, а счётчики
 * происшествий прежние, — и расхождение читалось бы как торговое
 * событие, а не как обрыв записи. Обрыв же МЕЖДУ порциями законен и
 * ничего не стои́т: проекция от порядка не зависит.
 *
 * <p><b>Менеджер транзакций назван поимённо.</b> Подключение собирается
 * своей формой, минуя автоконфигурацию, и на имени менеджера стои́т эта
 * проба: снятое имя вернуло бы выбор умолчанию.
 */
class AggregateWriteBoundariesTest {

    private static final LocalDate BUCKET = LocalDate.of(2026, 9, 9);
    private static final OffsetDateTime ASSEMBLED =
            OffsetDateTime.of(2026, 9, 10, 4, 15, 0, 0, ZoneOffset.UTC);

    private final DealAggregateDataService dealAggregateDataService = mock(DealAggregateDataService.class);
    private final IncidentAggregateDataService incidentAggregateDataService =
            mock(IncidentAggregateDataService.class);
    private final AggregateSourceDataService sourceDataService = mock(AggregateSourceDataService.class);
    private final AggregateWriteService writeService = new AggregateWriteService(dealAggregateDataService,
            incidentAggregateDataService);
    private final AggregateRecomputeService recomputeService = new AggregateRecomputeService(sourceDataService,
            writeService);

    @Test
    @DisplayName("Порция пишет ОБА зерна одним ходом: они собраны из своих фактов за одни сутки")
    void oneBucketWritesBothGrains() {
        DealGrainRow dealRow = mock(DealGrainRow.class);
        IncidentGrainRow incidentRow = mock(IncidentGrainRow.class);

        writeService.writeDay(BUCKET, List.of(dealRow), List.of(incidentRow), ASSEMBLED);

        verify(dealAggregateDataService).upsert(dealRow, BUCKET, ASSEMBLED);
        verify(incidentAggregateDataService).upsert(incidentRow, BUCKET, ASSEMBLED);
    }

    @Test
    @DisplayName("Сутки порции меряются полуинтервалом: полночь принадлежит следующим суткам")
    void theBucketIsAHalfOpenInterval() {
        OffsetDateTime longAgo = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(sourceDataService.earliestDealFactMoment()).thenReturn(longAgo);
        when(sourceDataService.earliestIncidentFactMoment()).thenReturn(longAgo);

        recomputeService.recomputeDay(BUCKET, ASSEMBLED);

        OffsetDateTime dayStart = BUCKET.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        verify(sourceDataService).collectDealGrain(dayStart, dayStart.plusDays(1));
        verify(sourceDataService).collectIncidentGrain(dayStart, dayStart.plusDays(1));
    }

    @Test
    @DisplayName("Запись порции идёт транзакцией с НАЗВАННЫМ менеджером базы агрегатов")
    void theBucketIsWrittenInANamedTransaction() {
        assertThat(transactionalMethods())
                .as("граница записи обязана называть менеджер своего владельца данных")
                .hasSize(1)
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class).transactionManager())
                        .isEqualTo(StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER));
    }

    @Test
    @DisplayName("Сборка порции своей транзакции не открывает: границу называет запись")
    void theCollectionOpensNoTransactionOfItsOwn() {
        assertThat(Arrays.stream(AggregateRecomputeService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList())
                .as("порция открывает ровно одну транзакцию, и открывает её запись")
                .isEmpty();
    }

    private List<Method> transactionalMethods() {
        return Arrays.stream(AggregateWriteService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList();
    }
}
