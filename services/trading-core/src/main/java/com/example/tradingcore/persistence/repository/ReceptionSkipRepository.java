package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.ReceptionSkipEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по следу пропуска отравленных записей.
 *
 * <p><b>Наследует {@code Repository}, а не {@code JpaRepository}:</b> тропа у
 * следа одна — вставка, поглощающая конфликт по ключу координат;
 * унаследованный {@code save} завёл бы вторую, и повторная доставка той же
 * записи падала бы на ключе вместо того, чтобы оставить строку одну
 * (docs/rules/durable-consumer-reception.md §«След пропуска — таблица
 * `reception_skips`»).
 */
public interface ReceptionSkipRepository extends Repository<ReceptionSkipEntity, Long> {

    /**
     * Вставка строки следа по координатам записи; возвращает число
     * вставленных строк — ноль означает, что след этой записи уже лежит
     * (пропуск, чья фиксация смещения не состоялась, доставил её снова).
     *
     * <p><b>Мимо сущности, и довод в форме, а не в скорости:</b> поглощение
     * конфликта выражается только нативным {@code on conflict}. Аудиторских
     * колонок у строки нет, поэтому ставить запросу, кроме момента
     * пропуска, нечего.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into reception_skips (consumer_group, topic, record_partition, record_offset,
                                         event_id, event_type, tenant_id, cause, skipped_at)
            values (:consumerGroup, :topic, :recordPartition, :recordOffset,
                    cast(:eventId as varchar), cast(:eventType as varchar), cast(:tenantId as varchar),
                    :cause, :skippedAt)
            on conflict on constraint uk_reception_skip_record do nothing
            """)
    int insertIfAbsent(@Param("consumerGroup") String consumerGroup,
                       @Param("topic") String topic,
                       @Param("recordPartition") Integer recordPartition,
                       @Param("recordOffset") Long recordOffset,
                       @Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("tenantId") String tenantId,
                       @Param("cause") String cause,
                       @Param("skippedAt") OffsetDateTime skippedAt);
}
