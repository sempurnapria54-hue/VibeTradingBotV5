package com.example.auditstatistics.persistence.service;

import static java.util.stream.Collectors.toList;

import com.example.auditstatistics.domain.model.AggregateQuery;
import com.example.auditstatistics.domain.model.DealAggregate;
import com.example.auditstatistics.mapping.AggregateMapper;
import com.example.auditstatistics.persistence.repository.aggregates.DealAggregateRepository;
import com.example.auditstatistics.persistence.repository.journalread.DealGrainRow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Граница domain ↔ persistence для строк сделочного зерна.
 *
 * <p><b>Собственной транзакции метод не открывает:</b> обе таблицы порции
 * пишутся ОДНОЙ транзакцией её прохода, и границу называет вызывающий
 * ({@code AggregateWriteService}).
 *
 * <p><b>Сутки и момент сборки приезжают параметрами, а не из строки
 * выдачи:</b> сутки — операнд порции, один на всю выдачу, а момент сборки
 * — показание читателю, которое ставит проход, а не запрос.
 */
@Service
@RequiredArgsConstructor
public class DealAggregateDataService {

    private final DealAggregateRepository repository;
    private final AggregateMapper mapper;

    /** Записать строку суток по ключу зерна. */
    public void upsert(DealGrainRow row, LocalDate bucketDate, OffsetDateTime assembledAt) {
        repository.upsert(row.getTenantId(),
                row.getExchangeAccountInternalId(),
                row.getStrategyInternalId(),
                bucketDate,
                row.getResultCurrency(),
                row.getClosedDeals(),
                row.getRiskBearingDeals(),
                row.getWinningDeals(),
                row.getLosingDeals(),
                row.getNeutralDeals(),
                row.getResultUnavailableDeals(),
                row.getCurrencyUnresolvedDeals(),
                row.getRiskUnsizedDeals(),
                row.getLiquidatedDeals(),
                row.getForcedReductionDeals(),
                row.getOutcomeUndeterminedDeals(),
                row.getReconciliationMismatchedDeals(),
                row.getReconciliationNotRunDeals(),
                row.getBreakdownIncompleteDeals(),
                row.getBreakdownNotAssessedDeals(),
                row.getRiskBenchmarkMissingDeals(),
                row.getRDenominatorDeals(),
                row.getResultBeforeFundingSum(),
                row.getNetResultSum(),
                row.getFeeSum(),
                row.getFundingSum(),
                row.getLiquidationPenaltySum(),
                row.getWinResultSum(),
                row.getLossResultSum(),
                row.getPlannedRiskSum(),
                row.getPlannedRiskExcludedSum(),
                row.getRSum(),
                assembledAt);
    }

    /**
     * Прочитать строки окна — от новых суток к старым, с курсорной
     * позицией по ключу зерна.
     *
     * <p><b>Компоненты позиции уезжают в запрос по отдельности</b>, потому
     * что пустой курсор означает «первая страница окна», а пустота двух его
     * компонентов — строку с пустым ключом; различает эти состояния сам
     * запрос. Половина позиции сюда не доезжает — вопрос с ней отвергается
     * раньше ({@code AggregateQuery#hasPartialCursor}).
     *
     * @param limit сколько строк прочитать; вызывающий просит на одну
     *              больше страницы, чтобы узнать, дочитано ли окно
     */
    public List<DealAggregate> findPage(AggregateQuery query, Integer limit) {
        return repository.findPage(query.getTenantId(),
                        query.getFrom(),
                        query.getTo(),
                        query.getCursorBucketDate(),
                        query.getCursorExchangeAccountInternalId(),
                        query.getCursorStrategyInternalId(),
                        query.getCursorResultCurrency(),
                        limit).stream()
                .map(mapper::persistenceToDomain)
                .collect(toList());
    }
}
