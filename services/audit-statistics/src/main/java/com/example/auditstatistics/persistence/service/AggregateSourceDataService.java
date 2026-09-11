package com.example.auditstatistics.persistence.service;

import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.IncidentGrainRow;
import com.example.auditstatistics.persistence.repository.journalread.JournalAggregateSourceRepository;
import com.example.auditstatistics.util.Constants;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Границы чтения журнала для пересчёта агрегатов — на кросс-подключении,
 * под ролью агрегатов.
 *
 * <p><b>Собственной транзакции методы не открывают.</b> Читающий запрос
 * идёт транзакцией своего менеджера, назначенного объявлением
 * репозиториев; общей транзакции с записью у него не бывает по построению
 * — базы разные, и кросс-базового запроса не существует
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Перечень кодов ручной операции подставляется ЗДЕСЬ, а не в тексте
 * запроса:</b> носитель перечня в коде сервиса один, и запрос берёт его
 * параметром.
 */
@Service
@RequiredArgsConstructor
public class AggregateSourceDataService {

    private final JournalAggregateSourceRepository repository;

    /**
     * Самый ранний момент приёма среди сохранившихся строк журнала; пусто
     * — журнал пуст.
     */
    public OffsetDateTime earliestRecordedAt() {
        return repository.earliestRecordedAt();
    }

    /** Строки сделочного зерна за сутки: полуинтервал по моменту происшествия. */
    public List<DealGrainRow> collectDealGrain(OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        return repository.collectDealGrain(dayStart, dayEnd);
    }

    /** Строки зерна происшествий за те же сутки. */
    public List<IncidentGrainRow> collectIncidentGrain(OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        return repository.collectIncidentGrain(dayStart, dayEnd, Constants.ManualOperation.CODES);
    }
}
