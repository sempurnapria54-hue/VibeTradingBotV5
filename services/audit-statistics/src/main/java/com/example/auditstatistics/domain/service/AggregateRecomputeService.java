package com.example.auditstatistics.domain.service;

import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.IncidentGrainRow;
import com.example.auditstatistics.persistence.service.AggregateSourceDataService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Порция пересчёта: собрать зёрна одних суток из журнала и отдать их
 * писателю (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а
 * не накопитель»).
 *
 * <p><b>Сутки берутся по моменту ПРОИСШЕСТВИЯ</b> — в них лежит сделка, к
 * которой событие относится, — и меряются полуинтервалом
 * {@code [начало суток; начало следующих)}: включающая правая граница
 * задваивала бы полночь в двух соседних сутках.
 *
 * <p><b>Оба зерна собираются одним проходом суток и пишутся одной
 * транзакцией:</b> они складываются из одного журнала, а расходятся только
 * ключи строки.
 */
@Service
@RequiredArgsConstructor
public class AggregateRecomputeService {

    private final AggregateSourceDataService aggregateSourceDataService;
    private final AggregateWriteService aggregateWriteService;

    /**
     * Самый ранний момент приёма среди сохранившихся строк журнала —
     * операнд отбора суток (docs/spec/audit-journal.json,
     * {@code journalMinRecordedAt}); пусто означает пустой журнал.
     */
    public OffsetDateTime earliestJournalMoment() {
        return aggregateSourceDataService.earliestRecordedAt();
    }

    /** Пересчитать одни сутки окна: два запроса группировки и запись порции. */
    public void recomputeDay(LocalDate bucketDate, OffsetDateTime assembledAt) {
        OffsetDateTime dayStart = bucketDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        List<DealGrainRow> dealRows = aggregateSourceDataService.collectDealGrain(dayStart, dayEnd);
        List<IncidentGrainRow> incidentRows = aggregateSourceDataService.collectIncidentGrain(dayStart, dayEnd);
        aggregateWriteService.writeDay(bucketDate, dealRows, incidentRows, assembledAt);
    }
}
