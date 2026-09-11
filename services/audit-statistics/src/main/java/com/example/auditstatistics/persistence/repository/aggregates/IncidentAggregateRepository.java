package com.example.auditstatistics.persistence.repository.aggregates;

import com.example.auditstatistics.persistence.model.aggregates.IncidentAggregateEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Запись строк зерна происшествий. */
public interface IncidentAggregateRepository extends Repository<IncidentAggregateEntity, Long> {

    /**
     * Записать строку суток по ключу зерна.
     *
     * <p><b>Ключ здесь без клаузы {@code nulls not distinct}</b> — пустых
     * колонок в нём нет ни одной; довод и форма — те же, что у соседней
     * таблицы (docs/rules/statistics-aggregates.md §Персистентность).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into incident_aggregates
                (tenant_id, exchange_account_internal_id, bucket_date,
                 opened_deals, order_decisions, raised_holds, hard_raised_holds, manually_raised_holds,
                 anomaly_reports, critical_anomaly_reports, manual_operation_reports, assembled_at)
            values
                (:tenantId, :exchangeAccountInternalId, :bucketDate,
                 :openedDeals, :orderDecisions, :raisedHolds, :hardRaisedHolds, :manuallyRaisedHolds,
                 :anomalyReports, :criticalAnomalyReports, :manualOperationReports, :assembledAt)
            on conflict on constraint uk_incident_aggregate_grain do update set
                opened_deals = excluded.opened_deals,
                order_decisions = excluded.order_decisions,
                raised_holds = excluded.raised_holds,
                hard_raised_holds = excluded.hard_raised_holds,
                manually_raised_holds = excluded.manually_raised_holds,
                anomaly_reports = excluded.anomaly_reports,
                critical_anomaly_reports = excluded.critical_anomaly_reports,
                manual_operation_reports = excluded.manual_operation_reports,
                assembled_at = excluded.assembled_at
            """)
    void upsert(@Param("tenantId") String tenantId,
                @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                @Param("bucketDate") LocalDate bucketDate,
                @Param("openedDeals") Integer openedDeals,
                @Param("orderDecisions") Integer orderDecisions,
                @Param("raisedHolds") Integer raisedHolds,
                @Param("hardRaisedHolds") Integer hardRaisedHolds,
                @Param("manuallyRaisedHolds") Integer manuallyRaisedHolds,
                @Param("anomalyReports") Integer anomalyReports,
                @Param("criticalAnomalyReports") Integer criticalAnomalyReports,
                @Param("manualOperationReports") Integer manualOperationReports,
                @Param("assembledAt") OffsetDateTime assembledAt);

    /**
     * Страница зерна происшествий: окно тенанта по суткам зерна, от новых к
     * старым, с курсорной позицией по ключу зерна
     * (docs/rules/statistics-aggregates.md §«Что это за числа и кто их
     * читает»).
     *
     * <p><b>Сравнение позиции собрано в КОРТЕЖ, а не разложено, и разница с
     * соседним зерном не стилевая:</b> пустых колонок в этом ключе нет ни
     * одной, поэтому кортежное сравнение неизвестного не даёт. У сделочного
     * зерна их две, и там оно выпускало бы строки молча.
     *
     * <p><b>Порядок сортировки и порядок сравнения обязаны совпадать</b> —
     * читаем от новых к старым, значит следующая страница есть строки строго
     * <b>меньше</b> позиции.
     *
     * <p><b>Операнды позиции приведены {@code cast}'ом</b>: пустой курсор
     * означает первую страницу окна, и тип пустоты выводить неоткуда.
     *
     * <p><b>Отбор обслуживает {@code ix_incident_aggregate_tenant_bucket}</b>
     * — свой тенант в обратном порядке суток.
     *
     * @param limit сколько строк прочитать; вызывающий просит на одну больше
     *              страницы, чтобы узнать, дочитано ли окно
     */
    @Query(nativeQuery = true, value = """
            select aggregate.*
              from incident_aggregates aggregate
             where aggregate.tenant_id = :tenantId
               and aggregate.bucket_date >= :fromDate
               and aggregate.bucket_date <= :toDate
               and (cast(:cursorBucketDate as date) is null
                    or (aggregate.bucket_date, aggregate.exchange_account_internal_id)
                       < (cast(:cursorBucketDate as date),
                          cast(:cursorExchangeAccountInternalId as varchar)))
             order by aggregate.bucket_date desc, aggregate.exchange_account_internal_id desc
             limit :limit
            """)
    List<IncidentAggregateEntity> findPage(@Param("tenantId") String tenantId,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          @Param("cursorBucketDate") LocalDate cursorBucketDate,
                                          @Param("cursorExchangeAccountInternalId")
                                          String cursorExchangeAccountInternalId,
                                          @Param("limit") Integer limit);
}
