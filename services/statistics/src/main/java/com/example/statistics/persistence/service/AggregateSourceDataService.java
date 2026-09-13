package com.example.statistics.persistence.service;

import com.example.statistics.persistence.repository.DealGrainRow;
import com.example.statistics.persistence.repository.FactAggregateSourceRepository;
import com.example.statistics.persistence.repository.IncidentGrainRow;
import com.example.statistics.util.Constants;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница чтения входов пересчёта: собственные факты сервиса
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»).
 *
 * <p><b>Чужого подключения у этой границы нет ни одного.</b> Прежде входы
 * читались из базы журнала кросс-подключением под ролью агрегатов; раздел
 * сервиса снял и подключение, и грант вместе с их предметом
 * (.claude/decisions/audit-statistics-split.md).
 *
 * <p><b>Перечень кодов ручной операции уезжает в запрос параметром</b>, а не
 * литералом текста: носитель у него один
 * ({@code Constants.ManualOperation}).
 *
 * <p><b>Собственной транзакции методы не открывают:</b> читающий запрос
 * идёт транзакцией менеджера, назначенного объявлением репозиториев.
 */
@Service
@RequiredArgsConstructor
public class AggregateSourceDataService {

    private final FactAggregateSourceRepository repository;

    /** Начало ряда сделочных фактов; пусто — ряд пуст. */
    public OffsetDateTime earliestDealFactMoment() {
        return repository.earliestDealFactMoment();
    }

    /** Начало ряда фактов происшествий; пусто — ряд пуст. */
    public OffsetDateTime earliestIncidentFactMoment() {
        return repository.earliestIncidentFactMoment();
    }

    /** Сделочное зерно за одни сутки окна. */
    public List<DealGrainRow> collectDealGrain(OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        return repository.collectDealGrain(dayStart, dayEnd);
    }

    /** Зерно происшествий за одни сутки окна. */
    public List<IncidentGrainRow> collectIncidentGrain(OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        return repository.collectIncidentGrain(dayStart, dayEnd, Constants.ManualOperation.CODES);
    }
}
