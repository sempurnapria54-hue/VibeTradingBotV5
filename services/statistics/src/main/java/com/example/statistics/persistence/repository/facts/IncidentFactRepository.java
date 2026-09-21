package com.example.statistics.persistence.repository.facts;

import com.example.statistics.persistence.model.facts.IncidentFactEntity;
import com.example.statistics.persistence.model.facts.IncidentFactId;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Запись факта происшествия
 * (docs/models/domain/other/StatisticsFact.md §«Отметка обработанного»).
 *
 * <p>Форма и доводы те же, что у соседа по зерну
 * ({@link DealFactRepository}): наследуется {@code Repository}, вставка
 * нативная, дедуп — конфликтом по уникальному ограничению, названному
 * ИМЕНЕМ. Почему именем, а не перечнем колонок, — там же: обе таблицы
 * гипертаблицы, и колонка разбиения входит в ограничение по требованию
 * Timescale.
 *
 * <p><b>Уникальность объявлена в КАЖДОЙ из двух таблиц, а не одна на
 * обе:</b> инвариант «класс раскладывается ровно в одну таблицу» делает
 * пару таких ограничений равносильной одному общему, а общее потребовало
 * бы третьего носителя — таблицы ключей, то есть того самого inbox.
 */
public interface IncidentFactRepository extends Repository<IncidentFactEntity, IncidentFactId> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into incident_facts
                (event_id, tenant_id, exchange_account_internal_id, occurred_at,
                 event_type, hold_rung, anomaly_severity, operation_code)
            values
                (:eventId, :tenantId, :exchangeAccountInternalId, :occurredAt,
                 :eventType, :holdRung, :anomalySeverity, :operationCode)
            on conflict on constraint pk_incident_fact do nothing
            """)
    void insertAbsorbingDuplicate(@Param("eventId") String eventId,
                                  @Param("tenantId") String tenantId,
                                  @Param("exchangeAccountInternalId") String exchangeAccountInternalId,
                                  @Param("occurredAt") OffsetDateTime occurredAt,
                                  @Param("eventType") String eventType,
                                  @Param("holdRung") String holdRung,
                                  @Param("anomalySeverity") String anomalySeverity,
                                  @Param("operationCode") String operationCode);
}
