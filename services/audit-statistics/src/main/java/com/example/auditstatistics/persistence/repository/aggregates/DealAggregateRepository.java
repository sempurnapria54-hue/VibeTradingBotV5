package com.example.auditstatistics.persistence.repository.aggregates;

import com.example.auditstatistics.persistence.model.aggregates.DealAggregateEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Запись строк сделочного зерна агрегатов. */
public interface DealAggregateRepository extends Repository<DealAggregateEntity, Long> {

    /**
     * Записать строку суток по ключу зерна.
     *
     * <p><b>Форма — upsert по ИМЕНОВАННОМУ ключу зерна, а не по списку
     * колонок.</b> Ключ несёт клаузу {@code nulls not distinct}, без
     * которой строка с пустым определением стратегии или пустой валютой не
     * находилась бы вовсе и проекция росла бы по строке на каждый прогон
     * (docs/rules/idempotency-via-unique.md); имя ограничения выбирает
     * ровно тот индекс, а вывод по списку колонок оставлял бы выбор
     * планировщику.
     *
     * <p><b>Не «спросить и вставить»:</b> проверка «есть ли уже строка
     * зерна» перед записью — форма, которую дом идемпотентности
     * запрещает, и здесь она к тому же не нужна: пересчёт переписывает
     * строку целиком, потому что она проекция, а не накопитель
     * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
     * накопитель»).
     *
     * <p><b>Момент сборки едет ТОЙ ЖЕ записью, что и числа</b> — иначе
     * читатель получил бы числа без своей актуальности.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into deal_aggregates
                (tenant_id, exchange_account_internal_id, strategy_internal_id, bucket_date, result_currency,
                 closed_deals, risk_bearing_deals, winning_deals, losing_deals, neutral_deals,
                 result_unavailable_deals, currency_unresolved_deals, risk_unsized_deals,
                 liquidated_deals, forced_reduction_deals, outcome_undetermined_deals,
                 reconciliation_mismatched_deals, reconciliation_not_run_deals,
                 breakdown_incomplete_deals, breakdown_not_assessed_deals,
                 risk_benchmark_missing_deals, r_denominator_deals,
                 result_before_funding_sum, net_result_sum, fee_sum, funding_sum,
                 liquidation_penalty_sum, win_result_sum, loss_result_sum,
                 planned_risk_sum, planned_risk_excluded_sum, r_sum, assembled_at)
            values
                (:tenantId, :exchangeAccountInternalId, :strategyInternalId, :bucketDate, :resultCurrency,
                 :closedDeals, :riskBearingDeals, :winningDeals, :losingDeals, :neutralDeals,
                 :resultUnavailableDeals, :currencyUnresolvedDeals, :riskUnsizedDeals,
                 :liquidatedDeals, :forcedReductionDeals, :outcomeUndeterminedDeals,
                 :reconciliationMismatchedDeals, :reconciliationNotRunDeals,
                 :breakdownIncompleteDeals, :breakdownNotAssessedDeals,
                 :riskBenchmarkMissingDeals, :rDenominatorDeals,
                 :resultBeforeFundingSum, :netResultSum, :feeSum, :fundingSum,
                 :liquidationPenaltySum, :winResultSum, :lossResultSum,
                 :plannedRiskSum, :plannedRiskExcludedSum, :rSum, :assembledAt)
            on conflict on constraint uk_deal_aggregate_grain do update set
                closed_deals = excluded.closed_deals,
                risk_bearing_deals = excluded.risk_bearing_deals,
                winning_deals = excluded.winning_deals,
                losing_deals = excluded.losing_deals,
                neutral_deals = excluded.neutral_deals,
                result_unavailable_deals = excluded.result_unavailable_deals,
                currency_unresolved_deals = excluded.currency_unresolved_deals,
                risk_unsized_deals = excluded.risk_unsized_deals,
                liquidated_deals = excluded.liquidated_deals,
                forced_reduction_deals = excluded.forced_reduction_deals,
                outcome_undetermined_deals = excluded.outcome_undetermined_deals,
                reconciliation_mismatched_deals = excluded.reconciliation_mismatched_deals,
                reconciliation_not_run_deals = excluded.reconciliation_not_run_deals,
                breakdown_incomplete_deals = excluded.breakdown_incomplete_deals,
                breakdown_not_assessed_deals = excluded.breakdown_not_assessed_deals,
                risk_benchmark_missing_deals = excluded.risk_benchmark_missing_deals,
                r_denominator_deals = excluded.r_denominator_deals,
                result_before_funding_sum = excluded.result_before_funding_sum,
                net_result_sum = excluded.net_result_sum,
                fee_sum = excluded.fee_sum,
                funding_sum = excluded.funding_sum,
                liquidation_penalty_sum = excluded.liquidation_penalty_sum,
                win_result_sum = excluded.win_result_sum,
                loss_result_sum = excluded.loss_result_sum,
                planned_risk_sum = excluded.planned_risk_sum,
                planned_risk_excluded_sum = excluded.planned_risk_excluded_sum,
                r_sum = excluded.r_sum,
                assembled_at = excluded.assembled_at
            """)
    void upsert(@Param("tenantId") String tenantId,
                @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                @Param("strategyInternalId") String strategyInternalId,
                @Param("bucketDate") LocalDate bucketDate,
                @Param("resultCurrency") String resultCurrency,
                @Param("closedDeals") Integer closedDeals,
                @Param("riskBearingDeals") Integer riskBearingDeals,
                @Param("winningDeals") Integer winningDeals,
                @Param("losingDeals") Integer losingDeals,
                @Param("neutralDeals") Integer neutralDeals,
                @Param("resultUnavailableDeals") Integer resultUnavailableDeals,
                @Param("currencyUnresolvedDeals") Integer currencyUnresolvedDeals,
                @Param("riskUnsizedDeals") Integer riskUnsizedDeals,
                @Param("liquidatedDeals") Integer liquidatedDeals,
                @Param("forcedReductionDeals") Integer forcedReductionDeals,
                @Param("outcomeUndeterminedDeals") Integer outcomeUndeterminedDeals,
                @Param("reconciliationMismatchedDeals") Integer reconciliationMismatchedDeals,
                @Param("reconciliationNotRunDeals") Integer reconciliationNotRunDeals,
                @Param("breakdownIncompleteDeals") Integer breakdownIncompleteDeals,
                @Param("breakdownNotAssessedDeals") Integer breakdownNotAssessedDeals,
                @Param("riskBenchmarkMissingDeals") Integer riskBenchmarkMissingDeals,
                @Param("rDenominatorDeals") Integer rDenominatorDeals,
                @Param("resultBeforeFundingSum") BigDecimal resultBeforeFundingSum,
                @Param("netResultSum") BigDecimal netResultSum,
                @Param("feeSum") BigDecimal feeSum,
                @Param("fundingSum") BigDecimal fundingSum,
                @Param("liquidationPenaltySum") BigDecimal liquidationPenaltySum,
                @Param("winResultSum") BigDecimal winResultSum,
                @Param("lossResultSum") BigDecimal lossResultSum,
                @Param("plannedRiskSum") BigDecimal plannedRiskSum,
                @Param("plannedRiskExcludedSum") BigDecimal plannedRiskExcludedSum,
                @Param("rSum") BigDecimal rSum,
                @Param("assembledAt") OffsetDateTime assembledAt);

    /**
     * Страница сделочного зерна: окно тенанта по суткам зерна, от новых к
     * старым, с курсорной позицией по ключу зерна
     * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
     * читает»).
     *
     * <p><b>Страница берётся КУРСОРОМ, а не смещением.</b> Смещение на
     * таблице, куда каждые сутки добавляется строка, пропускает и дублирует
     * при вставке между запросами; ключ зерна уникален по построению
     * ({@code uk_deal_aggregate_grain}), поэтому второго операнда позиции не
     * заводится.
     *
     * <p><b>Сравнение позиции РАЗЛОЖЕНО, а не собрано в кортеж, и это
     * несущее отличие от журнальной выборки.</b> Две колонки ключа законно
     * пусты, а сравнение кортежей на пустоте даёт НЕИЗВЕСТНО — строка с
     * пустым определением стратегии выпадала бы из страницы молча. Разложив
     * его, пустота становится сравнимым состоянием: {@code is not null} у
     * колонки и {@code is null} у операнда отвечают на неё явно.
     *
     * <p><b>Пустота в порядке — НАИБОЛЬШЕЕ, и это ровно умолчание
     * Postgres:</b> {@code desc} означает {@code nulls first}, поэтому
     * строка с пустым ключом стои́т в выдаче раньше своих соседей по суткам
     * и счёту. Сравнение позиции написано под тот же порядок: «строка после
     * курсора» есть «строка строго меньше» в нём.
     *
     * <p><b>Порядок сортировки и порядок сравнения обязаны совпадать</b> —
     * разошедшись, они отдали бы читателю пустоту вместо хвоста окна.
     *
     * <p><b>Тождество пустот выражено {@code is not distinct from}:</b> на
     * равенстве строка с пустым определением никогда не совпала бы со своей
     * же позицией, и хвост её суток терялся бы.
     *
     * <p><b>Необязательные операнды приведены {@code cast}'ом:</b> пустое
     * значение уходит в базу параметром без собственного текста, и тип его
     * выводить неоткуда — приведение называет тип на месте, а не оставляет
     * его на догадку драйвера.
     *
     * <p><b>План запроса ограничен ОКНОМ:</b> окно обязательно и ограничено
     * сверху ({@code AggregateReadProperties}), а обслуживает отбор
     * {@code ix_deal_aggregate_tenant_bucket} — свой тенант в обратном
     * порядке суток.
     *
     * @param limit сколько строк прочитать. Вызывающий просит на одну больше
     *              страницы: лишняя строка отвечает на вопрос «окно дочитано
     *              или нет» и наружу не отдаётся
     */
    @Query(nativeQuery = true, value = """
            select aggregate.*
              from deal_aggregates aggregate
             where aggregate.tenant_id = :tenantId
               and aggregate.bucket_date >= :fromDate
               and aggregate.bucket_date <= :toDate
               and (cast(:cursorBucketDate as date) is null
                    or aggregate.bucket_date < cast(:cursorBucketDate as date)
                    or (aggregate.bucket_date = cast(:cursorBucketDate as date)
                        and aggregate.exchange_account_internal_id
                            < cast(:cursorExchangeAccountInternalId as varchar))
                    or (aggregate.bucket_date = cast(:cursorBucketDate as date)
                        and aggregate.exchange_account_internal_id
                            = cast(:cursorExchangeAccountInternalId as varchar)
                        and aggregate.strategy_internal_id is not null
                        and (cast(:cursorStrategyInternalId as varchar) is null
                             or aggregate.strategy_internal_id
                                < cast(:cursorStrategyInternalId as varchar)))
                    or (aggregate.bucket_date = cast(:cursorBucketDate as date)
                        and aggregate.exchange_account_internal_id
                            = cast(:cursorExchangeAccountInternalId as varchar)
                        and aggregate.strategy_internal_id
                            is not distinct from cast(:cursorStrategyInternalId as varchar)
                        and aggregate.result_currency is not null
                        and (cast(:cursorResultCurrency as varchar) is null
                             or aggregate.result_currency < cast(:cursorResultCurrency as varchar))))
             order by aggregate.bucket_date desc,
                      aggregate.exchange_account_internal_id desc,
                      aggregate.strategy_internal_id desc,
                      aggregate.result_currency desc
             limit :limit
            """)
    List<DealAggregateEntity> findPage(@Param("tenantId") String tenantId,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate,
                                      @Param("cursorBucketDate") LocalDate cursorBucketDate,
                                      @Param("cursorExchangeAccountInternalId")
                                      String cursorExchangeAccountInternalId,
                                      @Param("cursorStrategyInternalId") String cursorStrategyInternalId,
                                      @Param("cursorResultCurrency") String cursorResultCurrency,
                                      @Param("limit") Integer limit);
}
