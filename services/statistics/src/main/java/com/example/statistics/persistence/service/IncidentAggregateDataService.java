package com.example.statistics.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.statistics.domain.model.AggregateQuery;
import com.example.statistics.domain.model.IncidentAggregate;
import com.example.statistics.mapping.AggregateMapper;
import com.example.statistics.persistence.repository.aggregates.IncidentAggregateRepository;
import com.example.statistics.persistence.repository.IncidentGrainRow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для строк зерна происшествий.
 *
 * <p>Границу транзакции называет вызывающий — та же, что у соседнего
 * зерна: порция пишет обе таблицы одним ходом.
 */
@Service
@RequiredArgsConstructor
public class IncidentAggregateDataService {

    private final IncidentAggregateRepository repository;
    private final AggregateMapper mapper;

    /** Записать строку суток по ключу зерна. */
    public void upsert(IncidentGrainRow row, LocalDate bucketDate, OffsetDateTime assembledAt) {
        repository.upsert(row.getTenantId(),
                row.getExchangeAccountInternalId(),
                bucketDate,
                row.getOpenedDeals(),
                row.getOrderDecisions(),
                row.getRaisedHolds(),
                row.getHardRaisedHolds(),
                row.getManuallyRaisedHolds(),
                row.getAnomalyReports(),
                row.getCriticalAnomalyReports(),
                row.getManualOperationReports(),
                assembledAt);
    }

    /**
     * Прочитать строки окна — от новых суток к старым, с курсорной
     * позицией по ключу зерна.
     *
     * <p>Компонентов позиции у этого зерна два: пустых колонок в его ключе
     * нет ни одной. Курсор, несущий компоненты сделочного зерна, сюда не
     * доезжает — вопрос с ним отвергается раньше
     * ({@code AggregateQuery#hasForeignGrainCursor}).
     *
     * @param limit сколько строк прочитать; вызывающий просит на одну
     *              больше страницы, чтобы узнать, дочитано ли окно
     */
    public List<IncidentAggregate> findPage(AggregateQuery query, Integer limit) {
        return repository.findPage(query.getTenantId(),
                        query.getFrom(),
                        query.getTo(),
                        query.getCursorBucketDate(),
                        query.getCursorExchangeAccountInternalId(),
                        limit).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
