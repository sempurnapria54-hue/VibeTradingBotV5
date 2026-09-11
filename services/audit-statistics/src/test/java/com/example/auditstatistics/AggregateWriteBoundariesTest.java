package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.domain.service.AggregateRecomputeService;
import com.example.auditstatistics.domain.service.AggregateWriteService;
import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.IncidentGrainRow;
import com.example.auditstatistics.persistence.service.AggregateSourceDataService;
import com.example.auditstatistics.persistence.service.DealAggregateDataService;
import com.example.auditstatistics.persistence.service.IncidentAggregateDataService;
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
 * <p><b>Менеджер транзакций назван поимённо.</b> Подключений у процесса
 * три, и неквалифицированная граница взяла бы то из них, чьё объявление
 * обработано первым, — то есть, возможно, базу журнала, где у роли
 * агрегатов записи нет вовсе.
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
    @DisplayName("Порция пишет ОБА зерна одним ходом: они собраны из одного журнала за одни сутки")
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
        recomputeService.recomputeDay(BUCKET, ASSEMBLED);

        OffsetDateTime dayStart = BUCKET.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        verify(sourceDataService).collectDealGrain(dayStart, dayStart.plusDays(1));
        verify(sourceDataService).collectIncidentGrain(dayStart, dayStart.plusDays(1));
    }

    @Test
    @DisplayName("Запись порции идёт транзакцией с НАЗВАННЫМ менеджером базы агрегатов")
    void theBucketIsWrittenInANamedTransaction() {
        assertThat(transactionalMethods())
                .as("подключений три, и умолчания у выбора нет намеренно")
                .hasSize(1)
                .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class).transactionManager())
                        .isEqualTo(AggregatesPersistenceConfig.AGGREGATES_TRANSACTION_MANAGER));
    }

    @Test
    @DisplayName("Сборка порции своей транзакции не открывает: базы разные, общей у них не бывает")
    void theCollectionOpensNoTransactionOfItsOwn() {
        assertThat(Arrays.stream(AggregateRecomputeService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList())
                .as("кросс-базового запроса не бывает: чтение журнала и запись агрегатов идут порознь")
                .isEmpty();
    }

    private List<Method> transactionalMethods() {
        return Arrays.stream(AggregateWriteService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .toList();
    }
}
